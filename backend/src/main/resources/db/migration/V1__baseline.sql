-- Baseline. Tenant-owned tables and row-level security arrive with the Workspace ticket.
CREATE TABLE platform_info (
    id         SMALLINT PRIMARY KEY CHECK (id = 1),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
INSERT INTO platform_info (id) VALUES (1);
