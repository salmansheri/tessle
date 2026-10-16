-- Data sources (Milestone 2): a Workspace's connections to external databases, and the audit trail of
-- administrative actions on them. Passwords are stored only as AES-256-GCM ciphertext (see SecretBox).
CREATE TABLE data_sources (
    id                  UUID PRIMARY KEY,
    workspace_id        UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    -- Immutable, unique per Workspace; what exports and Definitions will refer to.
    logical_name        TEXT NOT NULL CHECK (logical_name ~ '^[a-z][a-z0-9_]{1,39}$'),
    label               TEXT NOT NULL CHECK (char_length(btrim(label)) BETWEEN 1 AND 80 AND label !~ '[[:cntrl:]]'),
    type                TEXT NOT NULL CHECK (type IN ('postgresql', 'mysql', 'oracle', 'sqlserver')),
    host                TEXT NOT NULL CHECK (host ~ '^[A-Za-z0-9._-]{1,253}$'),
    port                INTEGER NOT NULL CHECK (port BETWEEN 1 AND 65535),
    database_name       TEXT NOT NULL CHECK (char_length(database_name) BETWEEN 1 AND 128),
    username            TEXT NOT NULL CHECK (char_length(username) BETWEEN 1 AND 128),
    password_ciphertext BYTEA NOT NULL,
    secret_key_id       TEXT NOT NULL,
    state               TEXT NOT NULL DEFAULT 'disabled' CHECK (state IN ('disabled', 'enabled')),
    -- Result of the latest connection test; cleared whenever a connection field changes.
    last_test_ok        BOOLEAN,
    last_test_at        TIMESTAMPTZ,
    last_test_error     TEXT,
    created_by          UUID NOT NULL REFERENCES users (id),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX data_sources_workspace_idx ON data_sources (workspace_id);
CREATE UNIQUE INDEX data_sources_logical_name_key ON data_sources (workspace_id, logical_name);

ALTER TABLE data_sources ENABLE ROW LEVEL SECURITY;

-- Builders and above can list (the application hides connection details from builders); only admins write.
CREATE POLICY data_sources_select ON data_sources FOR SELECT TO tessle_app
    USING (workspace_id = app_workspace() AND is_builder(workspace_id));
CREATE POLICY data_sources_insert ON data_sources FOR INSERT TO tessle_app
    WITH CHECK (workspace_id = app_workspace() AND is_admin(workspace_id) AND created_by = app_user());
CREATE POLICY data_sources_update ON data_sources FOR UPDATE TO tessle_app
    USING (workspace_id = app_workspace() AND is_admin(workspace_id))
    WITH CHECK (workspace_id = app_workspace() AND is_admin(workspace_id));
CREATE POLICY data_sources_delete ON data_sources FOR DELETE TO tessle_app
    USING (workspace_id = app_workspace() AND is_admin(workspace_id));

-- Structured audit events: who did what to which object, never a secret or a row value.
CREATE TABLE audit_events (
    id           UUID PRIMARY KEY,
    workspace_id UUID NOT NULL REFERENCES workspaces (id) ON DELETE CASCADE,
    actor_id     UUID NOT NULL REFERENCES users (id),
    action       TEXT NOT NULL,
    target_type  TEXT NOT NULL,
    target_id    UUID,
    detail       JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE INDEX audit_events_workspace_idx ON audit_events (workspace_id, created_at DESC);

ALTER TABLE audit_events ENABLE ROW LEVEL SECURITY;

-- Append-only for the application: anyone acting may record their own action; only admins read; no edits.
CREATE POLICY audit_events_insert ON audit_events FOR INSERT TO tessle_app
    WITH CHECK (workspace_id = app_workspace() AND actor_id = app_user() AND is_member(workspace_id));
CREATE POLICY audit_events_select ON audit_events FOR SELECT TO tessle_app
    USING (workspace_id = app_workspace() AND is_admin(workspace_id));
