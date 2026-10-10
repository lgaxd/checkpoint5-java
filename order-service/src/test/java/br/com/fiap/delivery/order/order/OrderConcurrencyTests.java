package br.com.fiap.delivery.order.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import br.com.fiap.delivery.order.dish.DataLoader;
import br.com.fiap.delivery.order.dish.Dish;
import br.com.fiap.delivery.order.dish.DishRepository;
import br.com.fiap.delivery.order.payment.PaymentClient;
import br.com.fiap.delivery.order.payment.PaymentResponse;
import br.com.fiap.delivery.order.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class OrderConcurrencyTests {

    private static final int REQUESTS = 50;

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DishRepository dishRepository;

    @Autowired
    OrderRepository orderRepository;

    @MockitoBean
    PaymentClient paymentClient;

    @Test
    void fiftySimultaneousRequestsConfirmExactlyTenOrdersAndStockEndsAtZero() throws Exception {
        when(paymentClient.charge(any())).thenReturn(new PaymentResponse("APPROVED", 8081));
        orderRepository.deleteAll();
        Dish promotion = dishRepository.findAll().stream()
                .filter(d -> d.getName().equals(DataLoader.PROMOTION_DISH)).findFirst().orElseThrow();
        promotion.setStock(DataLoader.PROMOTION_STOCK);
        dishRepository.save(promotion);

        ExecutorService executor = Executors.newFixedThreadPool(REQUESTS);
        CountDownLatch ready = new CountDownLatch(REQUESTS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<Integer>> results = new ArrayList<>();
        try {
            for (int i = 0; i < REQUESTS; i++) {
                results.add(executor.submit(() -> {
                    ready.countDown();
                    start.await();
                    return mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                                    .content("{\"dishId\": " + promotion.getId() + ", \"quantity\": 1}"))
                            .andReturn().getResponse().getStatus();
                }));
            }
            ready.await();
            start.countDown();

            int created = 0;
            int conflicts = 0;
            for (Future<Integer> result : results) {
                int status = result.get();
                if (status == 201) {
                    created++;
                } else if (status == 409) {
                    conflicts++;
                }
            }

            assertThat(created).isEqualTo(10);
            assertThat(conflicts).isEqualTo(40);
        } finally {
            executor.shutdownNow();
        }

        assertThat(orderRepository.countByStatus(OrderStatus.CONFIRMED)).isEqualTo(10);
        assertThat(dishRepository.findById(promotion.getId()).orElseThrow().getStock()).isZero();
    }
}
