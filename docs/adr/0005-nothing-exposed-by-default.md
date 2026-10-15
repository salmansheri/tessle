# Data sources expose nothing until an admin exposes tables

Connecting a Data source makes no table visible to builders. An admin picks the Exposed tables (each read-only or read-write) and may hide columns, and everything else does not exist as far as builders are concerned. We rejected "everything visible minus a deny list" because one forgotten exclusion would leak payroll or personal data, and the people connecting a database are not always the people who know what is sensitive in it.
