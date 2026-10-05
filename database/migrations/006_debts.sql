-- Deudas y abonos independientes de ingresos/gastos; los saldos se calculan en la APK.
-- Cada propietario gestiona sus deudas; los administradores pueden consultarlas.

BEGIN;

CREATE TABLE IF NOT EXISTS public.debts (
    id uuid PRIMARY KEY,
    household_id uuid NOT NULL REFERENCES public.households(id) ON DELETE CASCADE,
    created_by text NOT NULL,
    direction text NOT NULL CHECK (direction IN ('owed_by_me', 'owed_to_me')),
    counterparty text NOT NULL CHECK (length(btrim(counterparty)) BETWEEN 1 AND 120),
    description text,
    principal_amount numeric(18, 2) NOT NULL CHECK (principal_amount > 0),
    currency char(3) NOT NULL DEFAULT 'BOB' CHECK (currency = 'BOB'),
    opened_on date NOT NULL,
    due_on date,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT debts_due_after_open_check CHECK (due_on IS NULL OR due_on >= opened_on),
    CONSTRAINT debts_household_id_id_key UNIQUE (household_id, id)
);

CREATE INDEX IF NOT EXISTS debts_household_owner_direction_idx
    ON public.debts (household_id, created_by, direction);
CREATE INDEX IF NOT EXISTS debts_household_due_idx
    ON public.debts (household_id, due_on);

CREATE TABLE IF NOT EXISTS public.debt_payments (
    id uuid PRIMARY KEY,
    household_id uuid NOT NULL,
    debt_id uuid NOT NULL,
    created_by text NOT NULL,
    amount numeric(18, 2) NOT NULL CHECK (amount > 0),
    paid_on date NOT NULL,
    note text,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT debt_payments_debt_household_fk
        FOREIGN KEY (household_id, debt_id)
        REFERENCES public.debts (household_id, id)
        ON DELETE CASCADE
);

CREATE INDEX IF NOT EXISTS debt_payments_household_debt_paid_idx
    ON public.debt_payments (household_id, debt_id, paid_on);
CREATE INDEX IF NOT EXISTS debt_payments_household_owner_idx
    ON public.debt_payments (household_id, created_by);

CREATE OR REPLACE FUNCTION public.enforce_debt_payment_balance()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    debt_owner text;
    principal numeric(18, 2);
    other_payments numeric(18, 2);
BEGIN
    SELECT created_by, principal_amount
      INTO debt_owner, principal
      FROM public.debts
     WHERE household_id = NEW.household_id
       AND id = NEW.debt_id
     FOR UPDATE;

    IF debt_owner IS NULL THEN
        RAISE EXCEPTION 'The debt does not exist.' USING ERRCODE = '23503';
    END IF;
    IF NEW.created_by <> debt_owner OR debt_owner <> auth.user_id() THEN
        RAISE EXCEPTION 'Only the debt owner can register its payments.' USING ERRCODE = '42501';
    END IF;

    SELECT COALESCE(SUM(amount), 0)
      INTO other_payments
      FROM public.debt_payments
     WHERE household_id = NEW.household_id
       AND debt_id = NEW.debt_id
       AND id <> NEW.id;

    IF other_payments + NEW.amount > principal THEN
        RAISE EXCEPTION 'Debt payments cannot exceed the original balance.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

CREATE OR REPLACE FUNCTION public.enforce_debt_principal_not_below_paid()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    paid_amount numeric(18, 2);
BEGIN
    SELECT COALESCE(SUM(amount), 0)
      INTO paid_amount
      FROM public.debt_payments
     WHERE household_id = NEW.household_id
       AND debt_id = NEW.id;
    IF paid_amount > NEW.principal_amount THEN
        RAISE EXCEPTION 'The original balance cannot be lower than registered payments.'
            USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS debts_set_updated_at ON public.debts;
CREATE TRIGGER debts_set_updated_at
    BEFORE UPDATE ON public.debts
    FOR EACH ROW EXECUTE FUNCTION public.set_finance_updated_at();

DROP TRIGGER IF EXISTS debt_payments_set_updated_at ON public.debt_payments;
CREATE TRIGGER debt_payments_set_updated_at
    BEFORE UPDATE ON public.debt_payments
    FOR EACH ROW EXECUTE FUNCTION public.set_finance_updated_at();

DROP TRIGGER IF EXISTS debt_payments_enforce_balance ON public.debt_payments;
CREATE TRIGGER debt_payments_enforce_balance
    BEFORE INSERT OR UPDATE OF household_id, debt_id, created_by, amount
    ON public.debt_payments
    FOR EACH ROW EXECUTE FUNCTION public.enforce_debt_payment_balance();

DROP TRIGGER IF EXISTS debts_keep_principal_above_paid ON public.debts;
CREATE TRIGGER debts_keep_principal_above_paid
    BEFORE UPDATE OF principal_amount ON public.debts
    FOR EACH ROW EXECUTE FUNCTION public.enforce_debt_principal_not_below_paid();

ALTER TABLE public.debts ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.debt_payments ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS debts_authorized_read ON public.debts;
CREATE POLICY debts_authorized_read ON public.debts
    FOR SELECT TO authenticated
    USING (
        public.is_household_member(household_id)
        AND (created_by = auth.user_id() OR public.is_household_admin(household_id))
    );

DROP POLICY IF EXISTS debts_own_insert ON public.debts;
CREATE POLICY debts_own_insert ON public.debts
    FOR INSERT TO authenticated
    WITH CHECK (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
    );

DROP POLICY IF EXISTS debts_own_update ON public.debts;
CREATE POLICY debts_own_update ON public.debts
    FOR UPDATE TO authenticated
    USING (created_by = auth.user_id() AND public.is_household_member(household_id))
    WITH CHECK (created_by = auth.user_id() AND public.is_household_member(household_id));

DROP POLICY IF EXISTS debts_own_delete ON public.debts;
CREATE POLICY debts_own_delete ON public.debts
    FOR DELETE TO authenticated
    USING (created_by = auth.user_id() AND public.is_household_member(household_id));

DROP POLICY IF EXISTS debt_payments_authorized_read ON public.debt_payments;
CREATE POLICY debt_payments_authorized_read ON public.debt_payments
    FOR SELECT TO authenticated
    USING (
        public.is_household_member(household_id)
        AND EXISTS (
            SELECT 1 FROM public.debts AS parent
             WHERE parent.household_id = debt_payments.household_id
               AND parent.id = debt_payments.debt_id
               AND (parent.created_by = auth.user_id() OR public.is_household_admin(parent.household_id))
        )
    );

DROP POLICY IF EXISTS debt_payments_own_insert ON public.debt_payments;
CREATE POLICY debt_payments_own_insert ON public.debt_payments
    FOR INSERT TO authenticated
    WITH CHECK (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
        AND EXISTS (
            SELECT 1 FROM public.debts AS parent
             WHERE parent.household_id = debt_payments.household_id
               AND parent.id = debt_payments.debt_id
               AND parent.created_by = auth.user_id()
        )
    );

DROP POLICY IF EXISTS debt_payments_own_update ON public.debt_payments;
CREATE POLICY debt_payments_own_update ON public.debt_payments
    FOR UPDATE TO authenticated
    USING (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
        AND EXISTS (
            SELECT 1 FROM public.debts AS parent
             WHERE parent.household_id = debt_payments.household_id
               AND parent.id = debt_payments.debt_id
               AND parent.created_by = auth.user_id()
        )
    )
    WITH CHECK (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
        AND EXISTS (
            SELECT 1 FROM public.debts AS parent
             WHERE parent.household_id = debt_payments.household_id
               AND parent.id = debt_payments.debt_id
               AND parent.created_by = auth.user_id()
        )
    );

DROP POLICY IF EXISTS debt_payments_own_delete ON public.debt_payments;
CREATE POLICY debt_payments_own_delete ON public.debt_payments
    FOR DELETE TO authenticated
    USING (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
        AND EXISTS (
            SELECT 1 FROM public.debts AS parent
             WHERE parent.household_id = debt_payments.household_id
               AND parent.id = debt_payments.debt_id
               AND parent.created_by = auth.user_id()
        )
    );

REVOKE ALL ON TABLE public.debts, public.debt_payments FROM PUBLIC, anonymous, authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE public.debts, public.debt_payments TO authenticated;
REVOKE ALL ON FUNCTION public.enforce_debt_payment_balance() FROM PUBLIC;
REVOKE ALL ON FUNCTION public.enforce_debt_principal_not_below_paid() FROM PUBLIC;

COMMIT;
