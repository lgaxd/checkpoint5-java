package br.com.fiap.delivery.order.assistant;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.env.Environment;

/** The Gemini settings come from GEMINI_* entries, which can live in a .env file imported as properties. */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:dotenv-${random.uuid};DB_CLOSE_DELAY=-1",
        "spring.config.import=optional:file:src/test/resources/test.env[.properties]"
})
class DotEnvConfigTests {

    @Autowired
    Environment environment;

    @Test
    void geminiKeyAndModelAreReadFromTheDotEnvFile() {
        assertThat(environment.getProperty("spring.ai.google.genai.api-key")).isEqualTo("key-from-dotenv");
        assertThat(environment.getProperty("spring.ai.google.genai.chat.model")).isEqualTo("gemini-test-model");
    }
}
