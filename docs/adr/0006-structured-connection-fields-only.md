# Data source connections use structured fields, never a raw JDBC URL

A Data source is described by host, port, database or service name, credentials and a short allowlist of options, and the platform builds the JDBC URL itself. We rejected accepting a pasted URL because JDBC drivers accept options that read local files or load arbitrary classes (for example MySQL local-infile and PostgreSQL socket-factory settings), and because the network policy needs to inspect the exact host the driver will dial.
