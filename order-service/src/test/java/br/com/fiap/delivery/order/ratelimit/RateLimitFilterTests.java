package br.com.fiap.delivery.order.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;

import br.com.fiap.delivery.order.dish.DishRepository;
import br.com.fiap.delivery.order.payment.PaymentClient;
import br.com.fiap.delivery.order.payment.PaymentResponse;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:ratelimit-${random.uuid};DB_CLOSE_DELAY=-1",
        "delivery.rate-limit.orders-per-second=3"
})
@AutoConfigureMockMvc
class RateLimitFilterTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DishRepository dishRepository;

    @MockitoBean
    PaymentClient paymentClient;

    @Test
    void requestsAboveTheLimitReceive429WithJsonErrorBody() throws Exception {
        when(paymentClient.charge(any())).thenReturn(new PaymentResponse("APPROVED", 8081));
        // The dish with the largest stock, so that stock never interferes with the result.
        long dishId = dishRepository.findAll().stream()
                .max(java.util.Comparator.comparingInt(d -> d.getStock())).orElseThrow().getId();

        List<MockHttpServletResponse> responses = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            responses.add(mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                    .content("{\"dishId\": " + dishId + ", \"quantity\": 1}")).andReturn().getResponse());
        }

        assertThat(responses.subList(0, 3)).allSatisfy(r -> assertThat(r.getStatus()).isEqualTo(201));
        List<MockHttpServletResponse> limited = responses.stream().filter(r -> r.getStatus() == 429).toList();
        assertThat(limited).isNotEmpty();
        assertThat(limited.getFirst().getContentType()).startsWith("application/json");
        assertThat(limited.getFirst().getContentAsString()).contains("\"error\"");
    }

    @Test
    void otherEndpointsAreNotRateLimited() throws Exception {
        for (int i = 0; i < 10; i++) {
            assertThat(mockMvc.perform(get("/dishes")).andReturn().getResponse().getStatus()).isEqualTo(200);
        }
    }
}
