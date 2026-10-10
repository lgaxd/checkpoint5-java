package br.com.fiap.delivery.order.order;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;

import br.com.fiap.delivery.order.dish.DataLoader;
import br.com.fiap.delivery.order.dish.Dish;
import br.com.fiap.delivery.order.dish.DishRepository;
import br.com.fiap.delivery.order.payment.PaymentClient;
import br.com.fiap.delivery.order.payment.PaymentResponse;
import br.com.fiap.delivery.order.support.IntegrationTest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class OrderApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DishRepository dishRepository;

    @Autowired
    OrderRepository orderRepository;

    @MockitoBean
    PaymentClient paymentClient;

    Dish promotion;

    @BeforeEach
    void setUp() {
        orderRepository.deleteAll();
        promotion = dishRepository.findAll().stream()
                .filter(d -> d.getName().equals(DataLoader.PROMOTION_DISH)).findFirst().orElseThrow();
        promotion.setStock(DataLoader.PROMOTION_STOCK);
        dishRepository.save(promotion);
        when(paymentClient.charge(any())).thenReturn(new PaymentResponse("APPROVED", 8081));
    }

    private int stock() {
        return dishRepository.findById(promotion.getId()).orElseThrow().getStock();
    }

    private String body(long dishId, Object quantity) {
        return "{\"dishId\": " + dishId + ", \"quantity\": " + quantity + "}";
    }

    @Test
    void dataLoaderCreatesAtLeastFiveDishesAndPromotionStartsWithTenUnits() throws Exception {
        assertThat(dishRepository.count()).isGreaterThanOrEqualTo(5);
        mockMvc.perform(get("/dishes"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(dishRepository.count()));
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void getDishReturnsContractFields() throws Exception {
        mockMvc.perform(get("/dishes/" + promotion.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(promotion.getId()))
                .andExpect(jsonPath("$.name").value("House Burger"))
                .andExpect(jsonPath("$.description").value("Brioche bun"))
                .andExpect(jsonPath("$.price").value(39.90))
                .andExpect(jsonPath("$.stock").value(10));
    }

    @Test
    void getUnknownDishReturns404() throws Exception {
        mockMvc.perform(get("/dishes/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Dish not found"));
    }

    @Test
    void createOrderReturns201AndDecreasesStock() throws Exception {
        String response = mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(promotion.getId(), 2)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNumber())
                .andExpect(jsonPath("$.dishId").value(promotion.getId()))
                .andExpect(jsonPath("$.quantity").value(2))
                .andExpect(jsonPath("$.totalPrice").value(79.80))
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.createdAt").value(org.hamcrest.Matchers.matchesPattern(
                        "\\d{4}-\\d{2}-\\d{2}T\\d{2}:\\d{2}:\\d{2}(\\.\\d+)?")))
                .andReturn().getResponse().getContentAsString();

        assertThat(stock()).isEqualTo(8);
        verify(paymentClient).charge(new BigDecimal("79.80"));

        long id = Long.parseLong(response.replaceAll(".*\"id\":(\\d+).*", "$1"));
        mockMvc.perform(get("/orders/" + id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.quantity").value(2));
    }

    @Test
    void getUnknownOrderReturns404() throws Exception {
        mockMvc.perform(get("/orders/99999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Order not found"));
    }

    @Test
    void unknownDishReturns404() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content(body(99999, 1)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Dish not found"));
    }

    @Test
    void quantityLowerThanOneReturns400() throws Exception {
        for (Object quantity : new Object[]{0, -3}) {
            mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                            .content(body(promotion.getId(), quantity)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").isString());
        }
        assertThat(stock()).isEqualTo(10);
    }

    @Test
    void missingFieldsAndMalformedBodyReturn400() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON).content("not json"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").isString());
    }

    @Test
    void insufficientStockReturns409WithoutChargingPayment() throws Exception {
        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(promotion.getId(), 11)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("Dish out of stock"));

        verify(paymentClient, never()).charge(any());
        assertThat(stock()).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }

    @Test
    void paymentFailureReturns502AndKeepsStockIntact() throws Exception {
        when(paymentClient.charge(any())).thenThrow(new IllegalStateException("payment down"));

        mockMvc.perform(post("/orders").contentType(MediaType.APPLICATION_JSON)
                        .content(body(promotion.getId(), 3)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Payment service unavailable"));

        assertThat(stock()).isEqualTo(10);
        assertThat(orderRepository.count()).isZero();
    }
}
