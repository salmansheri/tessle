package dev.salman.Lowcodebackend;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.file.Files;
import java.nio.file.Path;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.SerializationFeature;

/**
 * Guards the frontend/backend contract. The committed spec is the input to the generated
 * client; if the API changes, run {@code ./mvnw test -Dopenapi.update=true} and commit the result.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class OpenApiContractTests {

	static final Path SPEC = Path.of("..", "frontend", "openapi.json");

	@Autowired
	MockMvc mvc;

	@Test
	void committedSpecMatchesRunningApi() throws Exception {
		String live = mvc.perform(get("/v3/api-docs")).andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString();
		ObjectMapper mapper = new ObjectMapper().rebuild().enable(SerializationFeature.INDENT_OUTPUT).build();
		JsonNode liveTree = mapper.readTree(live);
		assertThat(liveTree.at("/paths").has("/api/health")).isTrue();

		if (Boolean.getBoolean("openapi.update")) {
			Files.writeString(SPEC, mapper.writeValueAsString(liveTree) + "\n");
		}
		assertThat(mapper.readTree(Files.readString(SPEC)))
				.as("frontend/openapi.json is stale; run ./mvnw test -Dopenapi.update=true")
				.isEqualTo(liveTree);
	}

}
