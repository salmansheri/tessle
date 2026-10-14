package dev.salman.Lowcodebackend.workspace;

import java.util.List;
import java.util.UUID;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Members of the session's active Workspace. */
@RestController
@RequestMapping(value = "/api/members", produces = "application/json")
class MembersController {

	record MemberResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID userId,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String email,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = { "owner", "admin", "builder", "editor", "viewer" }) String role) {
	}

	record ChangeRole(@NotBlank @Pattern(regexp = Role.PATTERN) String role) {
	}

	private final MemberService members;
	private final MembershipRoles roles;

	MembersController(MemberService members, MembershipRoles roles) {
		this.members = members;
		this.roles = roles;
	}

	@Operation(operationId = "listMembers", summary = "Members of the active Workspace")
	@ApiResponse(responseCode = "200", description = "The members",
			content = @Content(array = @ArraySchema(schema = @Schema(implementation = MemberResponse.class))))
	@ApiResponse(responseCode = "400", description = "No active workspace")
	@ApiResponse(responseCode = "403", description = "Not a member of the active workspace")
	@GetMapping
	List<MemberResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		UUID workspace = ActiveWorkspace.of(jwt);
		roles.require(ActiveWorkspace.user(jwt), workspace, Role.viewer);
		return members.list(workspace).stream().map(m -> new MemberResponse(m.userId(), m.email(), m.role())).toList();
	}

	@Operation(operationId = "changeMemberRole", summary = "Change a member's role (owners and admins; only owners touch owners)")
	@ApiResponse(responseCode = "204", description = "Changed")
	@ApiResponse(responseCode = "403", description = "Not allowed")
	@ApiResponse(responseCode = "404", description = "Not a member")
	@ApiResponse(responseCode = "409", description = "The workspace would be left without an owner")
	@PatchMapping("/{userId}")
	ResponseEntity<Void> changeRole(@PathVariable UUID userId, @Valid @RequestBody ChangeRole body,
			@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		members.changeRole(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt), userId, Role.parse(body.role()).orElseThrow());
		return ResponseEntity.noContent().build();
	}

	@Operation(operationId = "removeMember", summary = "Remove a member (owners and admins; only owners remove owners)")
	@ApiResponse(responseCode = "204", description = "Removed")
	@ApiResponse(responseCode = "403", description = "Not allowed")
	@ApiResponse(responseCode = "404", description = "Not a member")
	@ApiResponse(responseCode = "409", description = "The workspace would be left without an owner")
	@DeleteMapping("/{userId}")
	ResponseEntity<Void> remove(@PathVariable UUID userId, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		members.remove(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt), userId);
		return ResponseEntity.noContent().build();
	}

}
