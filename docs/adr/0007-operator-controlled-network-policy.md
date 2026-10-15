# The platform operator, not the Workspace, decides which networks a Data source may reach

Loopback, link-local and cloud-metadata addresses are always refused. Private ranges are reachable only if the operator lists them in configuration. The hostname is resolved once and the connection goes to that exact address, so a DNS answer cannot change between check and connect. We rejected per-Workspace allowlists for now because a Workspace owner is a customer, and a customer should not be able to point the platform at its own internal network; a per-Workspace layer can be added on top later.
