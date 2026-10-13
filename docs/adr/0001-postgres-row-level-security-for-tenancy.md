# Shared schema with Postgres row-level security for Workspace isolation

All platform metadata lives in one Postgres schema with `workspace_id` on every tenant-owned row, protected by row-level security. The app sets the active Workspace per transaction and RLS fails closed when it is unset. We rejected schema-per-Workspace because Flyway migrations and connection handling get painful at many small Workspaces, and rejected application-only filtering because one missed `WHERE` would leak data.
