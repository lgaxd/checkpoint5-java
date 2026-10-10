package br.com.fiap.delivery.payment;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Random;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "server.port=8082",
        "payment.failure-rate=0.5"
})
@AutoConfigureMockMvc
class PaymentControllerTests {

    /** Deterministic random: returns the value configured in the holder. */
    static class FixedRandom extends Random {
        static volatile double next = 0.9;

        @Override
        public double nextDouble() {
            return next;
        }
    }

    @TestConfiguration
    static class Config {
        @Bean
        @Primary
        Random fixedRandom() {
            return new FixedRandom();
        }
    }

    @Autowired
    MockMvc mockMvc;

    @Test
    void approvesPaymentAndReportsInstancePort() throws Exception {
        FixedRandom.next = 0.9;

        mockMvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 79.80}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.instance").value(8082));
    }

    @Test
    void failsWithHttp500WhenSimulatedFailureOccurs() throws Exception {
        FixedRandom.next = 0.1;

        mockMvc.perform(post("/payments").contentType(MediaType.APPLICATION_JSON).content("{\"amount\": 79.80}"))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.error").exists());
    }
}
