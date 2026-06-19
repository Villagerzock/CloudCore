package net.villagerzock.corehandshake;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Import;

import static org.mockito.Mockito.mock;

@SpringBootTest(classes = CoreHandshakeInitializer.BootstrapConfiguration.class)
@Import(CoreHandshakeApplicationTests.ProviderConfiguration.class)
class CoreHandshakeApplicationTests {

	@Test
	void contextLoads() {
	}

	@TestConfiguration
	static class ProviderConfiguration {
		@Bean
		CoreHandshakeProvider coreHandshakeProvider() {
			return mock(CoreHandshakeProvider.class);
		}
	}

}
