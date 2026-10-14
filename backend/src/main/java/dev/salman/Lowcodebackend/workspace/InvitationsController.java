package dev.salman.Lowcodebackend.workspace;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.salman.Lowcodebackend.identity.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/invitations", produces = "application/json")
class InvitationsController {

	record InviteRequest(@NotBlank @Email String email, @NotBlank @Pattern(regexp = Role.PATTERN) String role) {
	}

	record AcceptRequest(@NotBlank @Size(max = 200) String token) {
	}

	record InvitationResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String role,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant expiresAt) {
	}

	record AcceptedResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID workspaceId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String role) {
	}

	private final InvitationService invitations;
	private final TokenService tokens;

	InvitationsController(InvitationService invitations, TokenService tokens) {
		this.invitations = invitations;
		this.tokens = tokens;
	}

	@Operation(operationId = "inviteMember", summary = "Email an invitation to join the active Workspace")
	@ApiResponse(responseCode = "201", description = "Invitation sent")
	@ApiResponse(responseCode = "403", description = "Not allowed")
	@ApiResponse(responseCode = "409", description = "Already a member, or a pending owner invitation exists")
	@ApiResponse(responseCode = "429", description = "Too many invitations sent recently")
	@PostMapping
	ResponseEntity<InvitationResponse> invite(@Valid @RequestBody InviteRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		InvitationService.InvitationView view = invitations.invite(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt),
				body.email(), Role.parse(body.role()).orElseThrow());
		return ResponseEntity.status(HttpStatus.CREATED)
				.body(new InvitationResponse(view.id(), view.email(), view.role(), view.expiresAt()));
	}

	@Operation(operationId = "listInvitations", summary = "Pending invitations of the active Workspace")
	@ApiResponse(responseCode = "200", description = "Pending invitations",
			content = @Content(array = @ArraySchema(schema = @Schema(implementation = InvitationResponse.class))))
	@GetMapping
	List<InvitationResponse> pending(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return invitations.pending(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt)).stream()
				.map(v -> new InvitationResponse(v.id(), v.email(), v.role(), v.expiresAt())).toList();
	}

	@Operation(operationId = "revokeInvitation", summary = "Withdraw a pending invitation")
	@ApiResponse(responseCode = "204", description = "Withdrawn")
	@ApiResponse(responseCode = "403", description = "Only owners can withdraw an owner invitation")
	@ApiResponse(responseCode = "404", description = "No such pending invitation")
	@DeleteMapping("/{id}")
	ResponseEntity<Void> revoke(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		invitations.revoke(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt), id);
		return ResponseEntity.noContent().build();
	}

	@Operation(operationId = "acceptInvitation", summary = "Accept an invitation; the signed-in email must match the invited one")
	@ApiResponse(responseCode = "200", description = "Joined; the joined Workspace becomes active",
			content = @Content(schema = @Schema(implementation = AcceptedResponse.class)))
	@ApiResponse(responseCode = "404", description = "Unknown, used, expired, or meant for someone else")
	@PostMapping("/accept")
	ResponseEntity<AcceptedResponse> accept(@Valid @RequestBody AcceptRequest body,
			@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return invitations.accept(ActiveWorkspace.user(jwt), body.token()).map(accepted -> {
			ResponseEntity.BodyBuilder ok = ResponseEntity.ok();
			tokens.activateWorkspace(jwt, accepted.workspaceId())
					.ifPresent(cookie -> ok.header(HttpHeaders.SET_COOKIE, cookie.toString()));
			return ok.body(new AcceptedResponse(accepted.workspaceId(), accepted.role()));
		}).orElseGet(() -> ResponseEntity.notFound().build());
	}

}
