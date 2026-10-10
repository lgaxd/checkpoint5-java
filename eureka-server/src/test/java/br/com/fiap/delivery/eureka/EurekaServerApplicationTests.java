package br.com.fiap.delivery.eureka;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClient;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class EurekaServerApplicationTests {

    @LocalServerPort
    int port;

    @Autowired
    org.springframework.core.env.Environment environment;

    @Test
    void applicationNameIsConfigured() {
        assertThat(environment.getProperty("spring.application.name")).isEqualTo("eureka-server");
    }

    @Test
    void registryEndpointIsAvailable() {
        ResponseEntity<String> response = RestClient.create("http://127.0.0.1:" + port)
                .get().uri("/eureka/apps").retrieve().toEntity(String.class);
        assertThat(response.getStatusCode().is2xxSuccessful()).isTrue();
    }
}
