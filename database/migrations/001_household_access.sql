-- Run once on the Neon branch after Neon Auth and Neon Data API are enabled.
-- Exposes only app-level household membership. No password or Neon management key belongs here.

BEGIN;

CREATE TABLE IF NOT EXISTS public.households (
    id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
    name text NOT NULL,
    created_by text NOT NULL,
    created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS public.household_members (
    household_id uuid NOT NULL REFERENCES public.households(id) ON DELETE CASCADE,
    user_id text NOT NULL,
    email text NOT NULL,
    role text NOT NULL CHECK (role IN ('admin', 'member')),
    joined_at timestamptz NOT NULL DEFAULT now(),
    CONSTRAINT household_members_pkey PRIMARY KEY (household_id, user_id),
    CONSTRAINT household_members_user_id_key UNIQUE (user_id)
);

CREATE OR REPLACE FUNCTION public.is_household_member(p_household_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.household_members AS member
        WHERE member.household_id = p_household_id
          AND member.user_id = auth.user_id()
    );
$$;

CREATE OR REPLACE FUNCTION public.is_household_admin(p_household_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
    SELECT EXISTS (
        SELECT 1
        FROM public.household_members AS member
        WHERE member.household_id = p_household_id
          AND member.user_id = auth.user_id()
          AND member.role = 'admin'
    );
$$;

CREATE OR REPLACE FUNCTION public.create_household(p_name text, p_email text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    current_user_id text := auth.user_id();
    new_household_id uuid;
BEGIN
    IF current_user_id IS NULL THEN
        RAISE EXCEPTION 'An authenticated user is required.' USING ERRCODE = '42501';
    END IF;

    IF EXISTS (SELECT 1 FROM public.household_members WHERE user_id = current_user_id) THEN
        RAISE EXCEPTION 'This user already belongs to a household.' USING ERRCODE = '23505';
    END IF;

    INSERT INTO public.households (name, created_by)
    VALUES (COALESCE(NULLIF(btrim(p_name), ''), 'Finanzas en pareja'), current_user_id)
    RETURNING id INTO new_household_id;

    INSERT INTO public.household_members (household_id, user_id, email, role)
    VALUES (new_household_id, current_user_id, lower(btrim(p_email)), 'admin');

    RETURN new_household_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.add_household_member(
    p_user_id text,
    p_email text,
    p_role text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    caller_household_id uuid;
BEGIN
    SELECT member.household_id INTO caller_household_id
    FROM public.household_members AS member
    WHERE member.user_id = auth.user_id()
      AND member.role = 'admin';

    IF caller_household_id IS NULL THEN
        RAISE EXCEPTION 'Only an administrator can add household members.' USING ERRCODE = '42501';
    END IF;
    PERFORM 1 FROM public.households WHERE id = caller_household_id FOR UPDATE;
    IF NOT EXISTS (
        SELECT 1 FROM public.household_members
        WHERE household_id = caller_household_id
          AND user_id = auth.user_id()
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only an administrator can add household members.' USING ERRCODE = '42501';
    END IF;
    IF p_user_id IS NULL OR btrim(p_user_id) = '' OR p_email IS NULL OR btrim(p_email) = '' THEN
        RAISE EXCEPTION 'A user ID and email are required.' USING ERRCODE = '22023';
    END IF;
    IF p_role NOT IN ('admin', 'member') THEN
        RAISE EXCEPTION 'The household role is invalid.' USING ERRCODE = '22023';
    END IF;

    INSERT INTO public.household_members (household_id, user_id, email, role)
    VALUES (caller_household_id, p_user_id, lower(btrim(p_email)), p_role);
END;
$$;

CREATE OR REPLACE FUNCTION public.set_household_member_role(
    p_target_user_id text,
    p_role text
)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    caller_household_id uuid;
    target_role text;
BEGIN
    SELECT member.household_id INTO caller_household_id
    FROM public.household_members AS member
    WHERE member.user_id = auth.user_id()
      AND member.role = 'admin';

    IF caller_household_id IS NULL THEN
        RAISE EXCEPTION 'Only an administrator can change roles.' USING ERRCODE = '42501';
    END IF;
    PERFORM 1 FROM public.households WHERE id = caller_household_id FOR UPDATE;
    IF NOT EXISTS (
        SELECT 1 FROM public.household_members
        WHERE household_id = caller_household_id
          AND user_id = auth.user_id()
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only an administrator can change roles.' USING ERRCODE = '42501';
    END IF;
    IF p_role NOT IN ('admin', 'member') THEN
        RAISE EXCEPTION 'The household role is invalid.' USING ERRCODE = '22023';
    END IF;

    SELECT member.role INTO target_role
    FROM public.household_members AS member
    WHERE member.household_id = caller_household_id
      AND member.user_id = p_target_user_id
    FOR UPDATE;

    IF target_role IS NULL THEN
        RAISE EXCEPTION 'The household member does not exist.' USING ERRCODE = 'P0002';
    END IF;
    IF target_role = 'admin' AND p_role <> 'admin'
       AND (SELECT count(*) FROM public.household_members AS member
            WHERE member.household_id = caller_household_id AND member.role = 'admin') <= 1 THEN
        RAISE EXCEPTION 'A household must keep at least one administrator.' USING ERRCODE = '23514';
    END IF;

    UPDATE public.household_members
    SET role = p_role
    WHERE household_id = caller_household_id
      AND user_id = p_target_user_id;
END;
$$;

CREATE OR REPLACE FUNCTION public.remove_household_member(p_target_user_id text)
RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = pg_catalog, public
AS $$
DECLARE
    caller_household_id uuid;
    target_role text;
BEGIN
    SELECT member.household_id INTO caller_household_id
    FROM public.household_members AS member
    WHERE member.user_id = auth.user_id()
      AND member.role = 'admin';

    IF caller_household_id IS NULL THEN
        RAISE EXCEPTION 'Only an administrator can remove household members.' USING ERRCODE = '42501';
    END IF;
    PERFORM 1 FROM public.households WHERE id = caller_household_id FOR UPDATE;
    IF NOT EXISTS (
        SELECT 1 FROM public.household_members
        WHERE household_id = caller_household_id
          AND user_id = auth.user_id()
          AND role = 'admin'
    ) THEN
        RAISE EXCEPTION 'Only an administrator can remove household members.' USING ERRCODE = '42501';
    END IF;

    SELECT member.role INTO target_role
    FROM public.household_members AS member
    WHERE member.household_id = caller_household_id
      AND member.user_id = p_target_user_id
    FOR UPDATE;

    IF target_role IS NULL THEN
        RAISE EXCEPTION 'The household member does not exist.' USING ERRCODE = 'P0002';
    END IF;
    IF target_role = 'admin'
       AND (SELECT count(*) FROM public.household_members AS member
            WHERE member.household_id = caller_household_id AND member.role = 'admin') <= 1 THEN
        RAISE EXCEPTION 'The only administrator cannot be removed.' USING ERRCODE = '23514';
    END IF;

    DELETE FROM public.household_members
    WHERE household_id = caller_household_id
      AND user_id = p_target_user_id;
END;
$$;

ALTER TABLE public.households ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.household_members ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS households_member_read ON public.households;
CREATE POLICY households_member_read ON public.households
    FOR SELECT TO authenticated
    USING (public.is_household_member(id));

DROP POLICY IF EXISTS household_members_self_or_admin_read ON public.household_members;
CREATE POLICY household_members_self_or_admin_read ON public.household_members
    FOR SELECT TO authenticated
    USING (user_id = auth.user_id() OR public.is_household_admin(household_id));

REVOKE ALL ON TABLE public.households FROM PUBLIC, anon, authenticated;
REVOKE ALL ON TABLE public.household_members FROM PUBLIC, anon, authenticated;
GRANT SELECT ON TABLE public.households, public.household_members TO authenticated;

REVOKE ALL ON FUNCTION public.is_household_member(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.is_household_admin(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.create_household(text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.add_household_member(text, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.set_household_member_role(text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION public.remove_household_member(text) FROM PUBLIC;

GRANT EXECUTE ON FUNCTION public.is_household_member(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.is_household_admin(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION public.create_household(text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.add_household_member(text, text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.set_household_member_role(text, text) TO authenticated;
GRANT EXECUTE ON FUNCTION public.remove_household_member(text) TO authenticated;

COMMIT;
