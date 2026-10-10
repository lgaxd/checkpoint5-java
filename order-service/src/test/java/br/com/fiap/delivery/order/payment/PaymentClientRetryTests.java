package br.com.fiap.delivery.order.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import br.com.fiap.delivery.order.dish.DataLoader;
import br.com.fiap.delivery.order.dish.Dish;
import br.com.fiap.delivery.order.dish.DishRepository;
import br.com.fiap.delivery.order.order.OrderRepository;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.MediaType;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Exercises the real {@link PaymentClient} (retry proxy + load balanced RestTemplate) against a stub
 * payment server. The stub is registered under the Eureka name PAYMENT-SERVICE in the discovery client,
 * so the URL used by the application never contains a host or port.
 */
@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.datasource.url=jdbc:h2:mem:retry-${random.uuid};DB_CLOSE_DELAY=-1;LOCK_TIMEOUT=60000",
        "delivery.rate-limit.orders-per-second=10000",
        "payment.retry.max-retries=3",
        "payment.retry.delay=10",
        "payment.retry.multiplier=2.0",
        "payment.retry.jitter=5",
        "payment.retry.max-delay=50"
})
@AutoConfigureMockMvc
class PaymentClientRetryTests {

    private static final HttpServer SERVER = startStub();
    private static final AtomicInteger CALLS = new AtomicInteger();
    private static volatile int failuresBeforeSuccess;

    private static HttpServer startStub() {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
            server.createContext("/payments", exchange -> {
                exchange.getRequestBody().readAllBytes();
                int call = CALLS.incrementAndGet();
                boolean fail = call <= failuresBeforeSuccess;
                byte[] body = (fail ? "{\"error\":\"Payment processing failed\"}"
                        : "{\"status\":\"APPROVED\",\"instance\":9999}").getBytes(StandardCharsets.UTF_8);
                exchange.getResponseHeaders().add("Content-Type", "application/json");
                exchange.sendResponseHeaders(fail ? 500 : 200, body.length);
                exchange.getResponseBody().write(body);
                exchange.close();
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    @DynamicPropertySource
    static void discovery(DynamicPropertyRegistry registry) {
        String uri = "http://127.0.0.1:" + SERVER.getAddress().getPort();
        registry.add("spring.cloud.discovery.client.simple.instances.PAYMENT-SERVICE[0].uri", () -> uri);
    }

    @AfterAll
    static void stopStub() {
        SERVER.stop(0);
    }

    @Autowired
    PaymentClient paymentClient;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DishRepository dishRepository;

    @Autowired
    OrderRepository orderRepository;

    @BeforeEach
    void reset() {
        CALLS.set(0);
        failuresBeforeSuccess = 0;
    }

    @Test
    void callsPaymentServiceByNameAndReturnsApproval() {
        PaymentResponse response = paymentClient.charge(new BigDecimal("79.80"));

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(response.instance()).isEqualTo(9999);
        assertThat(CALLS.get()).isEqualTo(1);
    }

    @Test
    void retriesTransientFailuresUntilApproved() {
        failuresBeforeSuccess = 3;

        PaymentResponse response = paymentClient.charge(new BigDecimal("10.00"));

        assertThat(response.status()).isEqualTo("APPROVED");
        assertThat(CALLS.get()).isEqualTo(4); // 1 call + 3 retries
    }

    @Test
    void givesUpAfterMaxRetries() {
        failuresBeforeSuccess = Integer.MAX_VALUE;

        assertThatThrownBy(() -> paymentClient.charge(new BigDecimal("10.00"))).isNotNull();

        assertThat(CALLS.get()).isEqualTo(4); // 1 call + 3 retries
    }

    @Test
    void orderEndsWith502AndStockIntactWhenAllPaymentAttemptsFail() throws Exception {
        failuresBeforeSuccess = Integer.MAX_VALUE;
        orderRepository.deleteAll();
        Dish promotion = dishRepository.findAll().stream()
                .filter(d -> d.getName().equals(DataLoader.PROMOTION_DISH)).findFirst().orElseThrow();
        promotion.setStock(DataLoader.PROMOTION_STOCK);
        dishRepository.save(promotion);

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishId\": " + promotion.getId() + ", \"quantity\": 2}"))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Payment service unavailable"));

        assertThat(CALLS.get()).isEqualTo(4);
        assertThat(dishRepository.findById(promotion.getId()).orElseThrow().getStock()).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void orderIsConfirmedWhenPaymentRecoversWithinTheRetries() throws Exception {
        failuresBeforeSuccess = 2;
        orderRepository.deleteAll();
        Dish promotion = dishRepository.findAll().stream()
                .filter(d -> d.getName().equals(DataLoader.PROMOTION_DISH)).findFirst().orElseThrow();
        promotion.setStock(DataLoader.PROMOTION_STOCK);
        dishRepository.save(promotion);

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishId\": " + promotion.getId() + ", \"quantity\": 1}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"));

        assertThat(CALLS.get()).isEqualTo(3);
        assertThat(dishRepository.findById(promotion.getId()).orElseThrow().getStock()).isEqualTo(9);
    }
}
