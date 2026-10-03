-- Run after 001_household_access.sql once Neon Auth and the Data API are configured.
-- Financial queries require a valid Auth JWT and household membership.

BEGIN;

CREATE TABLE IF NOT EXISTS public.categories (
    id uuid PRIMARY KEY,
    household_id uuid NOT NULL REFERENCES public.households(id) ON DELETE CASCADE,
    created_by text,
    name text NOT NULL CHECK (length(btrim(name)) BETWEEN 1 AND 40),
    kind text NOT NULL CHECK (kind IN ('income', 'expense')),
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT categories_household_id_id_key UNIQUE (household_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS categories_household_kind_name_key
    ON public.categories (household_id, kind, lower(btrim(name)));

CREATE INDEX IF NOT EXISTS categories_household_kind_active_idx
    ON public.categories (household_id, kind, is_active);

CREATE TABLE IF NOT EXISTS public.transactions (
    id uuid PRIMARY KEY,
    household_id uuid NOT NULL REFERENCES public.households(id) ON DELETE CASCADE,
    created_by text NOT NULL,
    category_id uuid NOT NULL,
    source_recurring_rule_id uuid,
    scheduled_for date,
    kind text NOT NULL CHECK (kind IN ('income', 'expense')),
    amount numeric(18, 2) NOT NULL CHECK (amount > 0),
    currency char(3) NOT NULL DEFAULT 'BOB' CHECK (currency = 'BOB'),
    occurred_on date NOT NULL,
    description text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT transactions_category_household_fk
        FOREIGN KEY (household_id, category_id)
        REFERENCES public.categories (household_id, id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS transactions_household_date_idx
    ON public.transactions (household_id, occurred_on DESC);
CREATE INDEX IF NOT EXISTS transactions_household_author_date_idx
    ON public.transactions (household_id, created_by, occurred_on DESC);
CREATE INDEX IF NOT EXISTS transactions_household_category_idx
    ON public.transactions (household_id, category_id);

CREATE OR REPLACE FUNCTION public.finance_default_category_id(
    p_household_id uuid,
    p_kind text,
    p_slug text
)
RETURNS uuid
LANGUAGE sql
IMMUTABLE
SET search_path = pg_catalog
AS $$
    WITH digest AS (
        SELECT md5(p_household_id::text || ':category:' || p_kind || ':' || p_slug) AS value
    )
    SELECT (
        substr(value, 1, 8) || '-' ||
        substr(value, 9, 4) || '-3' ||
        substr(value, 14, 3) || '-' ||
        CASE substr(value, 17, 1)
            WHEN '0' THEN '8' WHEN '1' THEN '9' WHEN '2' THEN 'a' WHEN '3' THEN 'b'
            WHEN '4' THEN '8' WHEN '5' THEN '9' WHEN '6' THEN 'a' WHEN '7' THEN 'b'
            WHEN '8' THEN '8' WHEN '9' THEN '9' WHEN 'a' THEN 'a' WHEN 'b' THEN 'b'
            WHEN 'c' THEN '8' WHEN 'd' THEN '9' WHEN 'e' THEN 'a' ELSE 'b'
        END ||
        substr(value, 18, 3) || '-' || substr(value, 21, 12)
    )::uuid
    FROM digest;
$$;

CREATE OR REPLACE FUNCTION public.seed_household_finance_categories(p_household_id uuid)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
BEGIN
    INSERT INTO public.categories (id, household_id, name, kind, created_by)
    VALUES
        (public.finance_default_category_id(p_household_id, 'expense', 'comida'), p_household_id, 'Comida', 'expense', NULL),
        (public.finance_default_category_id(p_household_id, 'expense', 'pasaje'), p_household_id, 'Pasaje', 'expense', NULL),
        (public.finance_default_category_id(p_household_id, 'expense', 'varios'), p_household_id, 'Varios', 'expense', NULL),
        (public.finance_default_category_id(p_household_id, 'income', 'salario'), p_household_id, 'Salario', 'income', NULL),
        (public.finance_default_category_id(p_household_id, 'income', 'trabajos-extra'), p_household_id, 'Trabajos extra', 'income', NULL)
    ON CONFLICT (id) DO NOTHING;
END;
$$;

CREATE OR REPLACE FUNCTION public.seed_finance_categories_after_household_insert()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
BEGIN
    PERFORM public.seed_household_finance_categories(NEW.id);
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS households_seed_finance_categories ON public.households;
CREATE TRIGGER households_seed_finance_categories
    AFTER INSERT ON public.households
    FOR EACH ROW EXECUTE FUNCTION public.seed_finance_categories_after_household_insert();

DO $$
DECLARE household_record record;
BEGIN
    FOR household_record IN SELECT id FROM public.households LOOP
        PERFORM public.seed_household_finance_categories(household_record.id);
    END LOOP;
END;
$$;

CREATE OR REPLACE FUNCTION public.set_finance_updated_at()
RETURNS trigger
LANGUAGE plpgsql
SET search_path = pg_catalog
AS $$
BEGIN
    NEW.updated_at = now();
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.enforce_transaction_category()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    category_kind text;
    category_is_active boolean;
BEGIN
    SELECT category.kind, category.is_active
    INTO category_kind, category_is_active
    FROM public.categories AS category
    WHERE category.id = NEW.category_id
      AND category.household_id = NEW.household_id;

    IF category_kind IS NULL OR category_kind <> NEW.kind OR NOT category_is_active THEN
        RAISE EXCEPTION 'The transaction category is unavailable for this type.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS transactions_validate_category ON public.transactions;
CREATE TRIGGER transactions_validate_category
    BEFORE INSERT OR UPDATE OF household_id, category_id, kind ON public.transactions
    FOR EACH ROW EXECUTE FUNCTION public.enforce_transaction_category();

DROP TRIGGER IF EXISTS categories_set_updated_at ON public.categories;
CREATE TRIGGER categories_set_updated_at
    BEFORE UPDATE ON public.categories
    FOR EACH ROW EXECUTE FUNCTION public.set_finance_updated_at();

DROP TRIGGER IF EXISTS transactions_set_updated_at ON public.transactions;
CREATE TRIGGER transactions_set_updated_at
    BEFORE UPDATE ON public.transactions
    FOR EACH ROW EXECUTE FUNCTION public.set_finance_updated_at();

ALTER TABLE public.categories ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.transactions ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS categories_household_read ON public.categories;
CREATE POLICY categories_household_read ON public.categories
    FOR SELECT TO authenticated
    USING (public.is_household_member(household_id));

DROP POLICY IF EXISTS categories_admin_insert ON public.categories;
CREATE POLICY categories_admin_insert ON public.categories
    FOR INSERT TO authenticated
    WITH CHECK (
        created_by = auth.user_id()
        AND public.is_household_admin(household_id)
    );

DROP POLICY IF EXISTS categories_admin_update ON public.categories;
CREATE POLICY categories_admin_update ON public.categories
    FOR UPDATE TO authenticated
    USING (public.is_household_admin(household_id))
    WITH CHECK (public.is_household_admin(household_id));

DROP POLICY IF EXISTS transactions_authorized_read ON public.transactions;
CREATE POLICY transactions_authorized_read ON public.transactions
    FOR SELECT TO authenticated
    USING (
        public.is_household_member(household_id)
        AND (public.is_household_admin(household_id) OR created_by = auth.user_id())
    );

DROP POLICY IF EXISTS transactions_own_insert ON public.transactions;
CREATE POLICY transactions_own_insert ON public.transactions
    FOR INSERT TO authenticated
    WITH CHECK (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
    );

DROP POLICY IF EXISTS transactions_own_update ON public.transactions;
CREATE POLICY transactions_own_update ON public.transactions
    FOR UPDATE TO authenticated
    USING (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
    )
    WITH CHECK (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
    );

DROP POLICY IF EXISTS transactions_own_delete ON public.transactions;
CREATE POLICY transactions_own_delete ON public.transactions
    FOR DELETE TO authenticated
    USING (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
    );

REVOKE ALL ON TABLE public.categories FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.transactions FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.categories TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.transactions TO authenticated;

REVOKE ALL ON FUNCTION public.finance_default_category_id(uuid, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.seed_household_finance_categories(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.seed_finance_categories_after_household_insert() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.set_finance_updated_at() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.enforce_transaction_category() FROM PUBLIC;

COMMIT;
