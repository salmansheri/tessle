package dev.salman.Lowcodebackend.tenantdb;

/** The four databases a Data source can point at. */
public enum Dialect {

	POSTGRESQL("postgresql.sql"), MYSQL("mysql.sql"), ORACLE("oracle.sql"), SQLSERVER("sqlserver.sql");

	private final String seedFile;

	Dialect(String seedFile) {
		this.seedFile = seedFile;
	}

	/** Seed script under {@code backend/tenant-dbs}, shared with the Compose profile. */
	String seedFile() {
		return seedFile;
	}

}
