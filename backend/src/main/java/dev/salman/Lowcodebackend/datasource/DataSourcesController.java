package dev.salman.Lowcodebackend.datasource;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import dev.salman.Lowcodebackend.workspace.ActiveWorkspace;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.ArraySchema;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Data sources of the session's active Workspace. Connection details are structured fields only (ADR 0006); the
 * password is write-only and appears in no response.
 */
@RestController
@RequestMapping(value = "/api/data-sources", produces = "application/json")
class DataSourcesController {

	private static final String VISIBLE = "(?s).*\\S.*";
	private static final String PLAIN_TEXT = "^[^\\p{Cntrl}\\u0080-\\u009f]*$";

	record CreateDataSource(
			@NotBlank @Pattern(regexp = "^[a-z][a-z0-9_]{1,39}$", message = "2 to 40 characters: lowercase letters, digits and underscores, starting with a letter") String logicalName,
			@NotBlank @Size(max = 80) @Pattern(regexp = PLAIN_TEXT, message = "must not contain control characters") String label,
			@NotBlank @Pattern(regexp = "postgresql", message = "only postgresql is supported so far") String type,
			@NotBlank @Pattern(regexp = "^[A-Za-z0-9._-]{1,253}$", message = "a host name or IPv4 address, without a scheme, port or path") String host,
			@Min(1) @Max(65535) int port,
			@NotBlank @Size(max = 128) @Pattern(regexp = PLAIN_TEXT, message = "must not contain control characters") String database,
			@NotBlank @Size(max = 128) @Pattern(regexp = PLAIN_TEXT, message = "must not contain control characters") String username,
			@NotBlank @Size(max = 256) String password) {
	}

	/** Every field optional; absent means unchanged. The logical name and type cannot be edited. */
	record UpdateDataSource(
			@Size(min = 1, max = 80) @Pattern(regexp = PLAIN_TEXT, message = "must not contain control characters") @Pattern(regexp = VISIBLE, message = "must contain a visible character") String label,
			@Pattern(regexp = "^[A-Za-z0-9._-]{1,253}$", message = "a host name or IPv4 address, without a scheme, port or path") String host,
			@Min(1) @Max(65535) Integer port,
			@Size(min = 1, max = 128) @Pattern(regexp = PLAIN_TEXT, message = "must not contain control characters") @Pattern(regexp = VISIBLE, message = "must contain a visible character") String database,
			@Size(min = 1, max = 128) @Pattern(regexp = PLAIN_TEXT, message = "must not contain control characters") @Pattern(regexp = VISIBLE, message = "must contain a visible character") String username,
			@Size(min = 1, max = 256) String password) {
	}

	record Health(Boolean lastTestOk, Instant lastTestAt, String lastTestError) {
	}

	/** Connection details are null for builders, who may list Data sources but not see how they are reached. */
	record DataSourceResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) UUID id,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String logicalName,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String label,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = { "postgresql", "mysql", "oracle", "sqlserver" }) String type,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED, allowableValues = { "disabled", "enabled" }) String state,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Health health,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) Instant createdAt,
			@Schema(nullable = true) String host, @Schema(nullable = true) Integer port,
			@Schema(nullable = true) String database, @Schema(nullable = true) String username) {

		static DataSourceResponse of(DataSourceService.View v) {
			DataSourceRecord r = v.record();
			return new DataSourceResponse(r.id(), r.logicalName(), r.label(), r.type(), r.state(),
					new Health(r.lastTestOk(), r.lastTestAt(), r.lastTestError()), r.createdAt(),
					v.details() ? r.host() : null, v.details() ? r.port() : null, v.details() ? r.database() : null,
					v.details() ? r.username() : null);
		}

	}

	record TestResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) boolean ok,
			@Schema(nullable = true, allowableValues = { "unreachable", "bad_credentials", "unknown_database", "failed" }) String error,
			@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String message) {
	}

	private final DataSourceService sources;

	DataSourcesController(DataSourceService sources) {
		this.sources = sources;
	}

	@Operation(operationId = "listDataSources", summary = "Data sources in the active Workspace (builders and above)")
	@ApiResponse(responseCode = "200", description = "The Data sources; connection details only for admins and owners",
			content = @Content(array = @ArraySchema(schema = @Schema(implementation = DataSourceResponse.class))))
	@ApiResponse(responseCode = "400", description = "No active workspace")
	@ApiResponse(responseCode = "403", description = "The caller's role cannot see Data sources")
	@GetMapping
	List<DataSourceResponse> list(@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return sources.list(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt)).stream().map(DataSourceResponse::of).toList();
	}

	@Operation(operationId = "createDataSource", summary = "Add a Data source, disabled until a connection test passes (admins and owners)")
	@ApiResponse(responseCode = "201", description = "Created")
	@ApiResponse(responseCode = "400", description = "No active workspace, or invalid fields")
	@ApiResponse(responseCode = "403", description = "The caller's role cannot manage Data sources")
	@ApiResponse(responseCode = "409", description = "That logical name is already used in this workspace")
	@PostMapping
	ResponseEntity<DataSourceResponse> create(@Valid @RequestBody CreateDataSource body,
			@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		DataSourceService.View created = sources.create(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt),
				new DataSourceService.NewDataSource(body.logicalName(), body.label(), body.type(), body.host(), body.port(),
						body.database(), body.username(), body.password()));
		return ResponseEntity.status(HttpStatus.CREATED).body(DataSourceResponse.of(created));
	}

	@Operation(operationId = "testDataSource", summary = "Try the connection and record the result (admins and owners)")
	@ApiResponse(responseCode = "200", description = "The outcome; a failed connection is a normal answer, not an error")
	@ApiResponse(responseCode = "403", description = "The caller's role cannot manage Data sources")
	@ApiResponse(responseCode = "404", description = "No such Data source in this workspace")
	@ApiResponse(responseCode = "409", description = "The Data source was edited while the test ran, so the result was not recorded")
	@PostMapping("/{id}/test")
	TestResponse test(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		ConnectionTester.Result r = sources.test(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt), id);
		return new TestResponse(r.ok(), r.error(), r.message());
	}

	@Operation(operationId = "enableDataSource", summary = "Enable a Data source whose latest connection test passed (admins and owners)")
	@ApiResponse(responseCode = "200", description = "Enabled")
	@ApiResponse(responseCode = "403", description = "The caller's role cannot manage Data sources")
	@ApiResponse(responseCode = "404", description = "No such Data source in this workspace")
	@ApiResponse(responseCode = "409", description = "The latest connection test has not passed")
	@PostMapping("/{id}/enable")
	DataSourceResponse enable(@PathVariable UUID id, @Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return DataSourceResponse.of(sources.enable(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt), id));
	}

	@Operation(operationId = "updateDataSource", summary = "Edit a Data source; omitted fields stay as they are (admins and owners)")
	@ApiResponse(responseCode = "200", description = "Updated. Changing how it connects clears its test result and disables it")
	@ApiResponse(responseCode = "400", description = "Invalid fields, or an attempt to change the logical name")
	@ApiResponse(responseCode = "403", description = "The caller's role cannot manage Data sources")
	@ApiResponse(responseCode = "404", description = "No such Data source in this workspace")
	@ApiResponse(responseCode = "409", description = "Someone else changed the Data source since it was read")
	@PatchMapping("/{id}")
	DataSourceResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateDataSource body,
			@Parameter(hidden = true) @AuthenticationPrincipal Jwt jwt) {
		return DataSourceResponse.of(sources.update(ActiveWorkspace.user(jwt), ActiveWorkspace.of(jwt), id,
				new DataSourceService.Edit(body.label(), body.host(), body.port(), body.database(), body.username(), body.password())));
	}

}
