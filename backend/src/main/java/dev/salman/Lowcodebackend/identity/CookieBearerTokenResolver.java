package dev.salman.Lowcodebackend.identity;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;
import org.springframework.stereotype.Component;

/** Reads the access JWT from its HttpOnly cookie. */
@Component
public class CookieBearerTokenResolver implements BearerTokenResolver {

	/**
	 * A stale cookie (rotated secret, tampering) must not block signing in again, so the
	 * public sign-in endpoints never look at it.
	 */
	private static boolean isPublic(String path) {
		return path.equals("/api/auth/csrf") || path.equals(TokenService.REFRESH_PATH) || path.startsWith("/api/auth/otp/");
	}

	@Override
	public String resolve(HttpServletRequest request) {
		if (request.getCookies() == null || isPublic(request.getRequestURI())) {
			return null;
		}
		for (Cookie cookie : request.getCookies()) {
			if (TokenService.ACCESS_COOKIE.equals(cookie.getName())) {
				return cookie.getValue();
			}
		}
		return null;
	}

}
