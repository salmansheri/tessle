package dev.salman.Lowcodebackend;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.context.annotation.Import;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
@Import(TestcontainersConfiguration.class)
class HealthApiTests {

	@Autowired
	MockMvc mvc;

	@Test
	void healthIsPublicAndReportsUp() throws Exception {
		mvc.perform(get("/api/health"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("UP"));
	}

	@Test
	void everyResponseCarriesATraceIdAndEchoesAWellFormedIncomingOne() throws Exception {
		mvc.perform(get("/api/health"))
				.andExpect(header().exists("X-Trace-Id"));
		mvc.perform(get("/api/health").header("X-Trace-Id", "abc-123"))
				.andExpect(header().string("X-Trace-Id", "abc-123"));
		mvc.perform(get("/api/health").header("X-Trace-Id", "bad id\twith spaces"))
				.andExpect(header().string("X-Trace-Id", org.hamcrest.Matchers.not("bad id\twith spaces")));
	}

}
