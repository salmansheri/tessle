package dev.salman.Lowcodebackend.workspace;

import java.util.List;
import java.util.UUID;

import dev.salman.Lowcodebackend.identity.TokenService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping(value = "/api/workspaces", produces = "application/json")
class WorkspaceController {

	record CreateWorkspace(@NotBlank @Size(max = 80) @Pattern(regexp = Names.PATTERN, message = Names.MESSAGE) String name) {
	}

	record WorkspaceResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, description = "The caller's role in this Workspace") String role) {
	}

	private final WorkspaceService workspaces;
	private final TokenService tokens;

	WorkspaceController(WorkspaceService workspaces, TokenService tokens) {
		this.workspaces = workspaces;
		this.tokens = tokens;
	}

	@Operation(operationId = "createWorkspace", summary = "Create a Workspace; the caller becomes its owner and it becomes active")
	@ApiResponse(responseCode = "201", description = "Created; the access cookie is renewed with the new active Workspace")
	@ApiResponse(responseCode = "400", description = "Invalid name")
	@PostMapping
	ResponseEntity<WorkspaceResponse> create(@Valid @RequestBody CreateWorkspace body,
			@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		WorkspaceService.WorkspaceView created = workspaces.create(UUID.fromString(jwt.getSubject()), body.name());
		ResponseEntity.BodyBuilder response = ResponseEntity.status(HttpStatus.CREATED);
		tokens.activateWorkspace(jwt, created.id()).ifPresent(cookie -> response.header(HttpHeaders.SET_COOKIE, cookie.toString()));
		return response.body(new WorkspaceResponse(created.id(), created.name(), created.role()));
	}

	@Operation(operationId = "listWorkspaces", summary = "The Workspaces the caller belongs to")
	@ApiResponse(responseCode = "200", description = "The caller's Workspaces",
			content = @io.swagger.v3.oas.annotations.media.Content(
					array = @io.swagger.v3.oas.annotations.media.ArraySchema(schema = @Schema(implementation = WorkspaceResponse.class))))
	@GetMapping
	List<WorkspaceResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return workspaces.listFor(UUID.fromString(jwt.getSubject())).stream()
				.map(w -> new WorkspaceResponse(w.id(), w.name(), w.role())).toList();
	}

	@Operation(operationId = "activateWorkspace", summary = "Switch the session's active Workspace")
	@ApiResponse(responseCode = "204", description = "Switched; the access cookie is renewed")
	@ApiResponse(responseCode = "404", description = "Not a Workspace the caller belongs to")
	@ApiResponse(responseCode = "401", description = "The session has ended")
	@PostMapping("/{id}/activate")
	ResponseEntity<Void> activate(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		if (!workspaces.isMember(UUID.fromString(jwt.getSubject()), id)) {
			return ResponseEntity.notFound().build();
		}
		return tokens.activateWorkspace(jwt, id)
				.map(cookie -> ResponseEntity.noContent().header(HttpHeaders.SET_COOKIE, cookie.toString()).<Void>build())
				.orElseGet(() -> ResponseEntity.status(HttpStatus.UNAUTHORIZED).build());
	}

}
