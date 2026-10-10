package br.com.fiap.delivery.order.config;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import br.com.fiap.delivery.order.payment.PaymentClient;
import br.com.fiap.delivery.order.payment.PaymentResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:cors-${random.uuid};DB_CLOSE_DELAY=-1",
        "delivery.rate-limit.orders-per-second=1"
})
@AutoConfigureMockMvc
class CorsTests {

    private static final String ORIGIN = "http://localhost:8081";

    @Autowired
    MockMvc mockMvc;

    @MockitoBean
    PaymentClient paymentClient;

    @Test
    void simpleRequestsGetAllowOriginHeader() throws Exception {
        mockMvc.perform(get("/dishes").header("Origin", ORIGIN))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }

    @Test
    void preflightForPostWithJsonIsAllowed() throws Exception {
        mockMvc.perform(options("/orders")
                        .header("Origin", ORIGIN)
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "content-type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string("Access-Control-Allow-Methods",
                        org.hamcrest.Matchers.containsString("POST")));
    }

    @Test
    void rateLimited429ResponsesAlsoCarryCorsHeaders() throws Exception {
        when(paymentClient.charge(any())).thenReturn(new PaymentResponse("APPROVED", 8081));
        int limited = 0;
        for (int i = 0; i < 5; i++) {
            var result = mockMvc.perform(post("/orders").header("Origin", ORIGIN)
                    .contentType(MediaType.APPLICATION_JSON).content("{\"dishId\": 2, \"quantity\": 1}")).andReturn();
            if (result.getResponse().getStatus() == 429) {
                limited++;
                org.assertj.core.api.Assertions.assertThat(result.getResponse().getHeader("Access-Control-Allow-Origin"))
                        .isEqualTo(ORIGIN);
            }
        }
        org.assertj.core.api.Assertions.assertThat(limited).isGreaterThan(0);
    }
}
