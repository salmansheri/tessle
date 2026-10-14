-- Apps: the container for a Workspace's Definitions (forms, reports, pages) and the unit of export/promotion.
CREATE TABLE apps (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    name         TEXT NOT NULL CHECK (char_length(btrim(name)) BETWEEN 1 AND 80 AND name !~ '[[:cntrl:]]'),
    created_by   UUID NOT NULL REFERENCES users (id),
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX apps_workspace_idx ON apps (workspace_id);
-- One App per name within a Workspace (ignoring case and padding), so names can identify Apps in lists and exports.
CREATE UNIQUE INDEX apps_name_key ON apps (workspace_id, lower(btrim(name)));

-- Builders and above write; same caller-only definer pattern as the other role helpers.
CREATE FUNCTION is_builder(ws uuid) RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER
SET search_path = public
AS $$ SELECT EXISTS (SELECT 1 FROM memberships
                     WHERE workspace_id = ws AND user_id = app_user() AND role IN ('owner', 'admin', 'builder')) $$;
REVOKE ALL ON FUNCTION is_builder(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION is_builder(uuid) TO tessle_app;

ALTER TABLE apps ENABLE ROW LEVEL SECURITY;

-- Every member of the Workspace they are acting in can see its Apps; nobody sees another Workspace's.
CREATE POLICY apps_select ON apps FOR SELECT TO tessle_app
    USING (workspace_id = app_workspace() AND is_member(workspace_id));
CREATE POLICY apps_insert ON apps FOR INSERT TO tessle_app
    WITH CHECK (workspace_id = app_workspace() AND is_builder(workspace_id) AND created_by = app_user());
CREATE POLICY apps_update ON apps FOR UPDATE TO tessle_app
    USING (workspace_id = app_workspace() AND is_builder(workspace_id))
    WITH CHECK (workspace_id = app_workspace() AND is_builder(workspace_id));
CREATE POLICY apps_delete ON apps FOR DELETE TO tessle_app
    USING (workspace_id = app_workspace() AND is_builder(workspace_id));
