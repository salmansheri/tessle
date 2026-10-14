package dev.salman.Lowcodebackend.workspace;

import java.util.UUID;

import dev.salman.Lowcodebackend.identity.TokenService;
import org.springframework.http.HttpStatus;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.server.ResponseStatusException;

/** The signed-in user and the Workspace their session is acting in. */
final class ActiveWorkspace {

	private ActiveWorkspace() {
	}

	static UUID user(Jwt jwt) {
		return UUID.fromString(jwt.getSubject());
	}

	/** 400 when the session has not chosen a Workspace yet. */
	static UUID of(Jwt jwt) {
		String claim = jwt.getClaimAsString(TokenService.WORKSPACE_CLAIM);
		if (claim == null) {
			throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "No active workspace");
		}
		return UUID.fromString(claim);
	}

}
