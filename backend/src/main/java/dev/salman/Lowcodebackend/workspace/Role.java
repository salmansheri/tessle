package dev.salman.Lowcodebackend.workspace;

import java.util.Optional;

/** Membership roles, weakest first. */
enum Role {

	viewer, editor, builder, admin, owner;

	boolean atLeast(Role other) {
		return ordinal() >= other.ordinal();
	}

	static Optional<Role> parse(String value) {
		for (Role role : values()) {
			if (role.name().equals(value)) {
				return Optional.of(role);
			}
		}
		return Optional.empty();
	}

	static final String PATTERN = "owner|admin|builder|editor|viewer";

}
