package dev.salman.Lowcodebackend.system;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
class HealthController {

	record HealthResponse(@Schema(requiredMode = Schema.RequiredMode.REQUIRED) String status) {
	}

	@Operation(operationId = "getHealth", summary = "Liveness check")
	@GetMapping(value = "/api/health", produces = "application/json")
	HealthResponse health() {
		return new HealthResponse("UP");
	}

}
