package br.com.fiap.delivery.order.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;

import br.com.fiap.delivery.order.dish.DishRepository;
import br.com.fiap.delivery.order.support.IntegrationTest;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.amqp.AmqpConnectException;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@IntegrationTest
class ReviewApiTests {

    @Autowired
    MockMvc mockMvc;

    @Autowired
    DishRepository dishRepository;

    @Autowired
    TopicExchange deliveryExchange;

    @Autowired
    Queue reviewsQueue;

    @Autowired
    Binding reviewsBinding;

    @Autowired
    MessageConverter messageConverter;

    @MockitoBean
    RabbitTemplate rabbitTemplate;

    private long firstDishId() {
        return dishRepository.findAll().getFirst().getId();
    }

    private String body(Object dishId, Object rating) {
        return "{\"dishId\": " + dishId + ", \"rating\": " + rating + ", \"comment\": \"Great\"}";
    }

    @Test
    void topologyIsExplicit() {
        assertThat(deliveryExchange.getName()).isEqualTo("delivery.exchange");
        assertThat(deliveryExchange.isDurable()).isTrue();
        assertThat(reviewsQueue.getName()).isEqualTo("reviews.queue");
        assertThat(reviewsQueue.isDurable()).isTrue();
        assertThat(reviewsBinding.getExchange()).isEqualTo("delivery.exchange");
        assertThat(reviewsBinding.getDestination()).isEqualTo("reviews.queue");
        assertThat(reviewsBinding.getRoutingKey()).isEqualTo("reviews.new");
    }

    @Test
    void validReviewIsPublishedAndReturns202WithoutBody() throws Exception {
        long dishId = firstDishId();

        mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON).content(body(dishId, 5)))
                .andExpect(status().isAccepted())
                .andExpect(content().string(""));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(rabbitTemplate).convertAndSend(eq("delivery.exchange"), eq("reviews.new"), captor.capture());
        ReviewMessage message = (ReviewMessage) captor.getValue();
        assertThat(message.dishId()).isEqualTo(dishId);
        assertThat(message.dishName()).isEqualTo(dishRepository.findById(dishId).orElseThrow().getName());
        assertThat(message.rating()).isEqualTo(5);
        assertThat(message.comment()).isEqualTo("Great");
    }

    @Test
    void publishedMessageIsJsonWithTheContractFields() {
        Message amqpMessage = messageConverter.toMessage(new ReviewMessage(1L, "House Burger", 4, "Nice"),
                new org.springframework.amqp.core.MessageProperties());

        String json = new String(amqpMessage.getBody(), StandardCharsets.UTF_8);
        assertThat(amqpMessage.getMessageProperties().getContentType()).isEqualTo("application/json");
        assertThat(json).contains("\"dishId\":1", "\"dishName\":\"House Burger\"", "\"rating\":4",
                "\"comment\":\"Nice\"");
    }

    @Test
    void ratingOutsideOneToFiveReturns400AndPublishesNothing() throws Exception {
        long dishId = firstDishId();
        for (int rating : new int[]{0, 6, -1}) {
            mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON).content(body(dishId, rating)))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.error").isString());
        }
        verify(rabbitTemplate, never()).convertAndSend(any(String.class), any(String.class), any(Object.class));
    }

    @Test
    void everyRatingFromOneToFiveIsAccepted() throws Exception {
        long dishId = firstDishId();
        for (int rating = 1; rating <= 5; rating++) {
            mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON).content(body(dishId, rating)))
                    .andExpect(status().isAccepted());
        }
    }

    @Test
    void unknownDishReturns404() throws Exception {
        mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON).content(body(99999, 5)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("Dish not found"));
    }

    @Test
    void missingRatingReturns400() throws Exception {
        mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dishId\": " + firstDishId() + "}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void brokerOutageReturns502() throws Exception {
        doThrow(new AmqpConnectException(new RuntimeException("down")))
                .when(rabbitTemplate).convertAndSend(any(String.class), any(String.class), any(Object.class));

        mockMvc.perform(post("/reviews").contentType(MediaType.APPLICATION_JSON).content(body(firstDishId(), 5)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error").value("Review queue unavailable"));
    }
}
