#!/bin/bash
# Compose-only: the image runs init scripts in the container root, so load the shared seed as the tenant user in the PDB.
sqlplus -s -L tenant/tenant@//localhost/FREEPDB1 <<'SQL'
WHENEVER SQLERROR EXIT FAILURE
@/seed/oracle.sql
COMMIT;
EXIT
SQL
