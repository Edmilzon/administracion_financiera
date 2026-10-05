-- Run after 001_household_access.sql and 002_financial_records.sql.
-- Markers carry only the transaction ID, household scope, author and deletion time.
-- They let active devices learn about physical deletes without retaining finance data.

BEGIN;

CREATE TABLE IF NOT EXISTS public.transaction_deletion_markers (
    transaction_id uuid PRIMARY KEY,
    household_id uuid NOT NULL REFERENCES public.households(id) ON DELETE CASCADE,
    created_by text NOT NULL,
    deleted_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS transaction_deletion_markers_household_deleted_idx
    ON public.transaction_deletion_markers (household_id, deleted_at);

ALTER TABLE public.transaction_deletion_markers ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS transaction_deletion_markers_household_read
    ON public.transaction_deletion_markers;
CREATE POLICY transaction_deletion_markers_household_read
    ON public.transaction_deletion_markers
    FOR SELECT TO authenticated
    USING (
        public.is_household_member(household_id)
        AND (public.is_household_admin(household_id) OR created_by = auth.user_id())
    );

DROP POLICY IF EXISTS transaction_deletion_markers_own_insert
    ON public.transaction_deletion_markers;
CREATE POLICY transaction_deletion_markers_own_insert
    ON public.transaction_deletion_markers
    FOR INSERT TO authenticated
    WITH CHECK (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
    );

DROP POLICY IF EXISTS transaction_deletion_markers_own_update
    ON public.transaction_deletion_markers;
CREATE POLICY transaction_deletion_markers_own_update
    ON public.transaction_deletion_markers
    FOR UPDATE TO authenticated
    USING (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
    )
    WITH CHECK (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
    );

CREATE OR REPLACE FUNCTION public.prune_expired_transaction_deletion_markers(
    p_household_id uuid
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
BEGIN
    IF NOT public.is_household_member(p_household_id) THEN
        RAISE EXCEPTION 'Household membership is required.' USING ERRCODE = '42501';
    END IF;

    DELETE FROM public.transaction_deletion_markers
    WHERE household_id = p_household_id
      AND deleted_at < now() - interval '30 days';
END;
$$;

REVOKE ALL ON TABLE public.transaction_deletion_markers FROM PUBLIC, anonymous, authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.transaction_deletion_markers TO authenticated;
REVOKE ALL ON FUNCTION public.prune_expired_transaction_deletion_markers(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION public.prune_expired_transaction_deletion_markers(uuid) TO authenticated;

COMMIT;
