-- Tenant isolation (ADR 0001).
--
-- The connection user owns the schema (a superuser in dev), and owners/superusers bypass row-level security.
-- So every application transaction runs `SET LOCAL ROLE tessle_app` (see TenantTransactionManager), a role
-- that is neither owner nor superuser and therefore always subject to the policies below. The caller's
-- identity arrives in per-transaction settings; when they are unset the helpers return NULL and every
-- policy denies (fails closed).

DO $$
BEGIN
    IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'tessle_app') THEN
        CREATE ROLE tessle_app NOLOGIN NOSUPERUSER NOBYPASSRLS;
    END IF;
END
$$;

-- Lets a non-superuser migration/connection user switch into the role.
DO $$
BEGIN
    EXECUTE format('GRANT tessle_app TO %I', current_user);
END
$$;

GRANT USAGE ON SCHEMA public TO tessle_app;
ALTER DEFAULT PRIVILEGES IN SCHEMA public GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO tessle_app;
GRANT SELECT, INSERT, UPDATE, DELETE ON users, otp_codes TO tessle_app;

CREATE FUNCTION app_user() RETURNS uuid LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('app.user_id', true), '')::uuid $$;

CREATE FUNCTION app_workspace() RETURNS uuid LANGUAGE sql STABLE
AS $$ SELECT nullif(current_setting('app.workspace_id', true), '')::uuid $$;

CREATE TABLE workspaces (
    id         UUID PRIMARY KEY,
    name       TEXT NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 80),
    created_by UUID NOT NULL REFERENCES users (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE memberships (
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    user_id      UUID NOT NULL REFERENCES users (id) ON DELETE CASCADE,
    role         TEXT NOT NULL CHECK (role IN ('owner', 'admin', 'builder', 'editor', 'viewer')),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    PRIMARY KEY (workspace_id, user_id)
);
CREATE INDEX memberships_user_idx ON memberships (user_id);

-- Policy helpers read `memberships` as the table owner (SECURITY DEFINER, which bypasses RLS). Querying
-- the table from inside its own policies would recurse. The owner is deliberately not FORCEd.
-- They answer only about the caller (app_user()), never about an arbitrary user, so they cannot be used
-- as a membership oracle, and only tessle_app may execute them.
CREATE FUNCTION is_member(ws uuid) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$ SELECT EXISTS (SELECT 1 FROM memberships WHERE workspace_id = ws AND user_id = app_user()) $$;

CREATE FUNCTION is_admin(ws uuid) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$ SELECT EXISTS (SELECT 1 FROM memberships
                     WHERE workspace_id = ws AND user_id = app_user() AND role IN ('owner', 'admin')) $$;

CREATE FUNCTION is_owner(ws uuid) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$ SELECT EXISTS (SELECT 1 FROM memberships
                     WHERE workspace_id = ws AND user_id = app_user() AND role = 'owner') $$;

-- The creator of a Workspace that has no members yet may add themselves as its owner.
CREATE FUNCTION can_bootstrap_owner(ws uuid) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$
    SELECT EXISTS (SELECT 1 FROM workspaces WHERE id = ws AND created_by = app_user())
       AND NOT EXISTS (SELECT 1 FROM memberships WHERE workspace_id = ws)
$$;

REVOKE ALL ON FUNCTION app_user(), app_workspace(), is_member(uuid), is_admin(uuid), is_owner(uuid),
    can_bootstrap_owner(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION app_user(), app_workspace(), is_member(uuid), is_admin(uuid), is_owner(uuid),
    can_bootstrap_owner(uuid) TO tessle_app;

ALTER TABLE workspaces ENABLE ROW LEVEL SECURITY;

CREATE POLICY workspaces_select ON workspaces FOR SELECT TO tessle_app
    USING (is_member(id));
CREATE POLICY workspaces_insert ON workspaces FOR INSERT TO tessle_app
    WITH CHECK (created_by = app_user());
CREATE POLICY workspaces_update ON workspaces FOR UPDATE TO tessle_app
    USING (is_admin(id)) WITH CHECK (is_admin(id));
-- No DELETE policy: deleting a Workspace is denied until it is designed.

ALTER TABLE memberships ENABLE ROW LEVEL SECURITY;

-- Your own memberships anywhere, plus every member of the Workspace you are acting in
-- (the active-Workspace setting alone proves nothing, so membership is re-checked).
CREATE POLICY memberships_select ON memberships FOR SELECT TO tessle_app
    USING (user_id = app_user() OR (workspace_id = app_workspace() AND is_member(workspace_id)));

-- Admins manage the Workspace's members, but only owners may touch owners: an admin cannot create an
-- owner, promote themselves, or remove or demote one. (The last owner is protected in the application.)
CREATE POLICY memberships_insert ON memberships FOR INSERT TO tessle_app
    WITH CHECK ((workspace_id = app_workspace() AND is_admin(workspace_id)
                 AND (role <> 'owner' OR is_owner(workspace_id)))
                OR (user_id = app_user() AND role = 'owner' AND can_bootstrap_owner(workspace_id)));
CREATE POLICY memberships_update ON memberships FOR UPDATE TO tessle_app
    USING (workspace_id = app_workspace() AND is_admin(workspace_id) AND (role <> 'owner' OR is_owner(workspace_id)))
    WITH CHECK (workspace_id = app_workspace() AND is_admin(workspace_id) AND (role <> 'owner' OR is_owner(workspace_id)));
CREATE POLICY memberships_delete ON memberships FOR DELETE TO tessle_app
    USING (workspace_id = app_workspace() AND is_admin(workspace_id) AND (role <> 'owner' OR is_owner(workspace_id)));
