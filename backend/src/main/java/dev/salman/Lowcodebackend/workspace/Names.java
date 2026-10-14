package dev.salman.Lowcodebackend.workspace;

/** Display names (Workspaces, Apps) end up in lists, emails and exports. */
final class Names {

	private Names() {
	}

	/**
	 * At least one visible character, and no control characters (ASCII or C1). Stricter than the database
	 * CHECK constraints on purpose, so bad input is a 400 here instead of a constraint failure there.
	 */
	static final String PATTERN = "^(?=.*[^\\p{Z}\\s\\p{C}])[^\\p{Cntrl}\\u0080-\\u009f]*$";

	static final String MESSAGE = "must contain a visible character and no control characters";

}
