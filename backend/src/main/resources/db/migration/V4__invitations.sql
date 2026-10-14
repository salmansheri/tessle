-- Invitations: an owner/admin invites an email address; the invitee signs in and accepts with a token.
-- Only a hash of the token is stored.
CREATE TABLE invitations (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    email        TEXT NOT NULL,
    role         TEXT NOT NULL CHECK (role IN ('owner', 'admin', 'builder', 'editor', 'viewer')),
    token_hash   TEXT NOT NULL UNIQUE,
    invited_by   UUID NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now(),
    expires_at   TIMESTAMPTZ NOT NULL,
    accepted_at  TIMESTAMPTZ
);
-- One pending invitation per address and Workspace; re-inviting replaces it.
CREATE UNIQUE INDEX invitations_pending_key ON invitations (workspace_id, lower(email)) WHERE accepted_at IS NULL;

ALTER TABLE invitations ENABLE ROW LEVEL SECURITY;

-- Admins of the Workspace they are acting in manage its invitations; only owners may invite owners.
CREATE POLICY invitations_select ON invitations FOR SELECT TO tessle_app
    USING (workspace_id = app_workspace() AND is_admin(workspace_id));
CREATE POLICY invitations_insert ON invitations FOR INSERT TO tessle_app
    WITH CHECK (workspace_id = app_workspace() AND is_admin(workspace_id)
                AND invited_by = app_user() AND (role <> 'owner' OR is_owner(workspace_id)));
-- Withdrawing (or replacing) a pending owner invitation is an owner-only act, like touching an owner.
CREATE POLICY invitations_delete ON invitations FOR DELETE TO tessle_app
    USING (workspace_id = app_workspace() AND is_admin(workspace_id)
           AND (role <> 'owner' OR is_owner(workspace_id)));

-- The invitee has no rights in the Workspace yet, so accepting cannot go through the policies. This
-- definer function is the only way in: the token must match, be pending and unexpired, and the signed-in
-- user's email must equal the invited address. An existing membership is never changed.
CREATE FUNCTION accept_invitation(p_token_hash text)
RETURNS TABLE (ws_id uuid, member_role text) LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    inv invitations%ROWTYPE;
    my_email text;
BEGIN
    SELECT email INTO my_email FROM users WHERE id = app_user();
    IF my_email IS NULL THEN
        RETURN;
    END IF;
    SELECT * INTO inv FROM invitations
     WHERE token_hash = p_token_hash AND accepted_at IS NULL AND expires_at > now()
       AND lower(email) = lower(my_email)
     FOR UPDATE;
    IF NOT FOUND THEN
        RETURN;
    END IF;
    INSERT INTO memberships (workspace_id, user_id, role)
    VALUES (inv.workspace_id, app_user(), inv.role)
    ON CONFLICT (workspace_id, user_id) DO NOTHING;
    UPDATE invitations SET accepted_at = now() WHERE id = inv.id;
    RETURN QUERY SELECT m.workspace_id, m.role FROM memberships m
                  WHERE m.workspace_id = inv.workspace_id AND m.user_id = app_user();
END
$$;
REVOKE ALL ON FUNCTION accept_invitation(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION accept_invitation(text) TO tessle_app;

-- Safety net for the "keep one owner" rule the application also enforces. Deferred, so a transaction may
-- swap owners as long as one remains at commit. A Workspace that no longer exists needs no owner.
CREATE FUNCTION ensure_workspace_keeps_owner() RETURNS trigger LANGUAGE plpgsql SECURITY DEFINER
SET search_path = public
AS $$
DECLARE
    ws uuid := COALESCE(OLD.workspace_id, NEW.workspace_id);
BEGIN
    IF EXISTS (SELECT 1 FROM workspaces WHERE id = ws)
       AND NOT EXISTS (SELECT 1 FROM memberships WHERE workspace_id = ws AND role = 'owner') THEN
        RAISE EXCEPTION 'workspace % must keep at least one owner', ws USING ERRCODE = 'check_violation';
    END IF;
    RETURN NULL;
END
$$;

CREATE CONSTRAINT TRIGGER memberships_keep_owner
    AFTER UPDATE OR DELETE ON memberships
    DEFERRABLE INITIALLY DEFERRED
    FOR EACH ROW EXECUTE FUNCTION ensure_workspace_keeps_owner();
