-- Run after migrations 001-004 once Neon Auth and the Data API are configured.
-- Rule owners can manage their own rules; admins can read all rules in the household.

BEGIN;

CREATE TABLE IF NOT EXISTS public.recurring_rules (
    id uuid PRIMARY KEY,
    household_id uuid NOT NULL REFERENCES public.households(id) ON DELETE CASCADE,
    created_by text NOT NULL,
    category_id uuid NOT NULL,
    kind text NOT NULL CHECK (kind IN ('income', 'expense')),
    amount numeric(18, 2) NOT NULL CHECK (amount > 0),
    currency char(3) NOT NULL DEFAULT 'BOB' CHECK (currency = 'BOB'),
    description text,
    frequency text NOT NULL CHECK (frequency IN ('daily', 'weekly', 'monthly')),
    interval_count integer NOT NULL DEFAULT 1 CHECK (interval_count BETWEEN 1 AND 3650),
    start_on date NOT NULL,
    next_due_on date NOT NULL,
    is_active boolean NOT NULL DEFAULT true,
    created_at timestamptz NOT NULL DEFAULT now(),
    updated_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT recurring_rules_household_id_id_key UNIQUE (household_id, id),
    CONSTRAINT recurring_rules_category_household_fk
        FOREIGN KEY (household_id, category_id)
        REFERENCES public.categories (household_id, id)
        ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS recurring_rules_household_due_active_idx
    ON public.recurring_rules (household_id, next_due_on, is_active);
CREATE INDEX IF NOT EXISTS recurring_rules_household_owner_due_idx
    ON public.recurring_rules (household_id, created_by, next_due_on);

ALTER TABLE public.transactions
    ADD COLUMN IF NOT EXISTS source_recurring_rule_id uuid,
    ADD COLUMN IF NOT EXISTS scheduled_for date;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'transactions_recurring_source_pair_check'
    ) THEN
        ALTER TABLE public.transactions
            ADD CONSTRAINT transactions_recurring_source_pair_check
            CHECK (
                (source_recurring_rule_id IS NULL AND scheduled_for IS NULL)
                OR (source_recurring_rule_id IS NOT NULL AND scheduled_for IS NOT NULL)
            );
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_constraint WHERE conname = 'transactions_recurring_rule_household_fk'
    ) THEN
        ALTER TABLE public.transactions
            ADD CONSTRAINT transactions_recurring_rule_household_fk
            FOREIGN KEY (household_id, source_recurring_rule_id)
            REFERENCES public.recurring_rules (household_id, id)
            ON DELETE RESTRICT;
    END IF;
END;
$$;

CREATE UNIQUE INDEX IF NOT EXISTS transactions_one_recurring_occurrence_idx
    ON public.transactions (source_recurring_rule_id, scheduled_for)
    WHERE source_recurring_rule_id IS NOT NULL;

DROP TRIGGER IF EXISTS recurring_rules_set_updated_at ON public.recurring_rules;
CREATE TRIGGER recurring_rules_set_updated_at
    BEFORE UPDATE ON public.recurring_rules
    FOR EACH ROW EXECUTE FUNCTION public.set_finance_updated_at();

CREATE OR REPLACE FUNCTION public.enforce_transaction_recurring_rule_owner()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    rule_owner text;
BEGIN
    IF NEW.source_recurring_rule_id IS NULL THEN
        RETURN NEW;
    END IF;
    IF NEW.occurred_on <> NEW.scheduled_for THEN
        RAISE EXCEPTION 'The transaction date must match its scheduled occurrence.' USING ERRCODE = '23514';
    END IF;

    SELECT created_by INTO rule_owner
    FROM public.recurring_rules
    WHERE id = NEW.source_recurring_rule_id
      AND household_id = NEW.household_id;

    IF rule_owner IS NULL OR rule_owner <> NEW.created_by THEN
        RAISE EXCEPTION 'A recurring occurrence must be registered by its rule owner.' USING ERRCODE = '23514';
    END IF;
    RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS transactions_validate_recurring_rule_owner ON public.transactions;
CREATE TRIGGER transactions_validate_recurring_rule_owner
    BEFORE INSERT OR UPDATE OF household_id, created_by, source_recurring_rule_id, scheduled_for, occurred_on
    ON public.transactions
    FOR EACH ROW EXECUTE FUNCTION public.enforce_transaction_recurring_rule_owner();

ALTER TABLE public.recurring_rules ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS recurring_rules_authorized_read ON public.recurring_rules;
CREATE POLICY recurring_rules_authorized_read ON public.recurring_rules
    FOR SELECT TO authenticated
    USING (
        public.is_household_member(household_id)
        AND (created_by = auth.user_id() OR public.is_household_admin(household_id))
    );

DROP POLICY IF EXISTS recurring_rules_own_insert ON public.recurring_rules;
CREATE POLICY recurring_rules_own_insert ON public.recurring_rules
    FOR INSERT TO authenticated
    WITH CHECK (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
    );

DROP POLICY IF EXISTS recurring_rules_own_update ON public.recurring_rules;
CREATE POLICY recurring_rules_own_update ON public.recurring_rules
    FOR UPDATE TO authenticated
    USING (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
    )
    WITH CHECK (
        created_by = auth.user_id()
        AND public.is_household_member(household_id)
    );

REVOKE ALL ON TABLE public.recurring_rules FROM PUBLIC, anon, authenticated;
GRANT SELECT, INSERT, UPDATE ON TABLE public.recurring_rules TO authenticated;
REVOKE ALL ON FUNCTION public.enforce_transaction_recurring_rule_owner() FROM PUBLIC;

COMMIT;
