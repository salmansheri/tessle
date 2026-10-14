package dev.salman.Lowcodebackend.identity;

import java.time.Duration;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/auth", produces = "application/json")
class AuthController {

	private static final Duration WINDOW = Duration.ofMinutes(15);

	record CsrfResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String token) {
	}

	record OtpRequest(@NotBlank @Email String email) {
	}

	record OtpVerification(@NotBlank @Email String email, @NotBlank @Pattern(regexp = "\\d{6}") String code) {
	}

	record UserResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email) {
	}

	private final OtpService otp;
	private final TokenService tokens;
	private final UserRepository users;
	private final RateLimiter limiter;

	AuthController(OtpService otp, TokenService tokens, UserRepository users, RateLimiter limiter) {
		this.otp = otp;
		this.tokens = tokens;
		this.users = users;
		this.limiter = limiter;
	}

	/** Hands the SPA a CSRF token; the matching cookie is set by the same call (double-submit). */
	@Operation(operationId = "getCsrf", summary = "Start a CSRF double-submit exchange")
	@GetMapping("/csrf")
	CsrfResponse csrf(@Parameter(hidden = true) CsrfToken token) {
		return new CsrfResponse(token.getToken());
	}

	@Operation(operationId = "requestOtp", summary = "Email a sign-in code (202 whether or not the email has an account)")
	@ApiResponse(responseCode = "202", description = "Code emailed (or silently ignored for unknown addresses)")
	@ApiResponse(responseCode = "400", description = "Malformed email")
	@ApiResponse(responseCode = "429", description = "Rate limited")
	@PostMapping("/otp/request")
	ResponseEntity<Void> requestOtp(@Valid @RequestBody OtpRequest body, HttpServletRequest request) {
		String email = normalize(body.email());
		if (!limiter.allow("otp-req-email", email, 5, WINDOW) || !limiter.allow("otp-req-ip", request.getRemoteAddr(), 30, WINDOW)) {
			return tooMany();
		}
		otp.request(email);
		return ResponseEntity.status(HttpStatus.ACCEPTED).build();
	}

	@Operation(operationId = "verifyOtp", summary = "Exchange an emailed code for a session")
	@ApiResponse(responseCode = "200", description = "Signed in; session cookies set",
			content = @Content(schema = @Schema(implementation = UserResponse.class)))
	@ApiResponse(responseCode = "400", description = "Malformed email or code")
	@ApiResponse(responseCode = "401", description = "Invalid, expired or used code")
	@ApiResponse(responseCode = "429", description = "Rate limited")
	@PostMapping("/otp/verify")
	ResponseEntity<UserResponse> verifyOtp(@Valid @RequestBody OtpVerification body, HttpServletRequest request) {
		String email = normalize(body.email());
		if (!limiter.allow("otp-verify-email", email, 20, WINDOW) || !limiter.allow("otp-verify-ip", request.getRemoteAddr(), 60, WINDOW)) {
			return tooMany();
		}
		return otp.verify(email, body.code()).map(user -> {
			ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
			for (ResponseCookie cookie : tokens.issue(user)) {
				ok.header(HttpHeaders.SET_COOKIE, cookie.toString());
			}
			return ok.body(new UserResponse(user.getId(), user.getEmail()));
		}).orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

	@Operation(operationId = "getMe", summary = "The signed-in user")
	@ApiResponse(responseCode = "200", description = "The signed-in user",
			content = @Content(schema = @Schema(implementation = UserResponse.class)))
	@ApiResponse(responseCode = "401", description = "Not signed in")
	@GetMapping("/me")
	ResponseEntity<UserResponse> me(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return users.findById(UUID.fromString(jwt.getSubject()))
				.map(user -> ResponseEntity.ok(new UserResponse(user.getId(), user.getEmail())))
				.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

	private static String normalize(String email) {
		return email.trim().toLowerCase(java.util.Locale.ROOT);
	}

	private static <T> ResponseEntity<T> tooMany() {
		return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS).header(HttpHeaders.RETRY_AFTER, String.valueOf(WINDOW.toSeconds())).build();
	}

}
