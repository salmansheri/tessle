package dev.salman.Lowcodebackend.datasource;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

class DataSourceRecordTests {

	private static DataSourceRecord withState(String state) {
		return new DataSourceRecord(UUID.randomUUID(), UUID.randomUUID(), "sales", "Sales", "postgresql", "db", 5432, "d", "u",
				new byte[0], "k1", true, null, null, state, null, null, null, null, null, null, null, null);
	}

	@Test
	void aDisabledDataSourceRefusesWithAClearMessageAndAnEnabledOneDoesNot() {
		assertThatThrownBy(() -> withState("disabled").requireEnabled())
				.isInstanceOf(ResponseStatusException.class).hasMessageContaining("This data source is disabled");
		assertThatCode(() -> withState("enabled").requireEnabled()).doesNotThrowAnyException();
	}

}
