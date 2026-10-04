-- Run after migrations 001-003 once Neon Auth and the Data API are configured.
-- Budget owners manage their own limits; admins can only read household budgets.

BEGIN;

CREATE TABLE IF NOT EXISTS public.budgets (
    id uuid PRIMARY KEY,
    household_id uuid NOT NULL REFERENCES public.households(id) ON DELETE CASCADE,
    user_id text NOT NULL,
    category_id uuid,
    month_start date NOT NULL CHECK (extract(day FROM month_start) = 1),
    amount_limit numeric(18, 2) NOT NULL CHECK (amount_limit > 0),
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT budgets_category_household_fk
        FOREIGN KEY (household_id, category_id)
        REFERENCES public.categories (household_id, id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS budgets_household_month_user_idx
    ON public.budgets (household_id, month_start, user_id);
CREATE INDEX IF NOT EXISTS budgets_household_category_idx
    ON public.budgets (household_id, category_id);

CREATE UNIQUE INDEX IF NOT EXISTS budgets_unique_general_per_user_month
    ON public.budgets (household_id, user_id, month_start)
    WHERE category_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS budgets_unique_category_per_user_month
    ON public.budgets (household_id, user_id, month_start, category_id)
    WHERE category_id IS NOT NULL;

DROP TRIGGER IF EXISTS budgets_set_updated_at ON public.budgets;
CREATE TRIGGER budgets_set_updated_at
    BEFORE UPDATE ON public.budgets
    FOR EACH ROW EXECUTE FUNCTION public.set_finance_updated_at();

ALTER TABLE public.budgets ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS budgets_authorized_read ON public.budgets;
CREATE POLICY budgets_authorized_read ON public.budgets
    FOR SELECT TO authenticated
    USING (
        public.is_household_member(household_id)
        AND (user_id = auth.user_id() OR public.is_household_admin(household_id))
    );

DROP POLICY IF EXISTS budgets_own_insert ON public.budgets;
CREATE POLICY budgets_own_insert ON public.budgets
    FOR INSERT TO authenticated
    WITH CHECK (
        user_id = auth.user_id()
        AND public.is_household_member(household_id)
    );

DROP POLICY IF EXISTS budgets_own_update ON public.budgets;
CREATE POLICY budgets_own_update ON public.budgets
    FOR UPDATE TO authenticated
    USING (
        user_id = auth.user_id()
        AND public.is_household_member(household_id)
    )
    WITH CHECK (
        user_id = auth.user_id()
        AND public.is_household_member(household_id)
    );

DROP POLICY IF EXISTS budgets_own_delete ON public.budgets;
CREATE POLICY budgets_own_delete ON public.budgets
    FOR DELETE TO authenticated
    USING (
        user_id = auth.user_id()
        AND public.is_household_member(household_id)
    );

REVOKE ALL ON TABLE public.budgets FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.budgets TO authenticated;

COMMIT;
