-- Workspace names end up in email subjects and headers; refuse control characters (CR, LF, ...).
ALTER TABLE workspaces ADD CONSTRAINT workspace_name_no_control CHECK (name !~ '[[:cntrl:]]');
