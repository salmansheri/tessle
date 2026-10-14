package dev.salman.Lowcodebackend.workspace;

import java.time.Instant;
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
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Apps of the session's active Workspace. */
@RestController
@RequestMapping(value = "/api/apps", produces = "application/json")
class AppsController {

	record CreateApp(@NotBlank @Size(max = 80) @Pattern(regexp = Names.PATTERN, message = Names.MESSAGE) String name) {
	}

	record AppResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String name,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt) {
	}

	private final AppService apps;

	AppsController(AppService apps) {
		this.apps = apps;
	}

	@Operation(operationId = "listApps", summary = "Apps in the active Workspace")
	@ApiResponse(responseCode = "200", description = "The Apps",
			content = @Content(array = @ArraySchema(schema = @Schema(implementation = AppResponse.class))))
	@ApiResponse(responseCode = "400", description = "No active workspace")
	@ApiResponse(responseCode = "403", description = "Not a member of the active workspace")
	@GetMapping
	List<AppResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return apps.list(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt)).stream()
				.map(a -> new AppResponse(a.id(), a.name(), a.createdAt())).toList();
	}

	@Operation(operationId = "createApp", summary = "Create an App (builders, admins and owners)")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiResponse(responseCode = "400", description = "No active workspace, or an invalid name")
	@ApiResponse(responseCode = "403", description = "The caller's role cannot create Apps")
	@PostMapping
	ResponseEntity<AppResponse> create(@Valid @RequestBody CreateApp body, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		AppService.AppView created = apps.create(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt), body.name());
		return ResponseEntity.status(HttpStatus.CREATED).body(new AppResponse(created.id(), created.name(), created.createdAt()));
	}

}
