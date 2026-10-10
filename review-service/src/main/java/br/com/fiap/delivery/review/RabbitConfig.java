package br.com.fiap.delivery.review;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.support.converter.JacksonJsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Same explicit topology declared by the producer (order-service); declarations are idempotent,
 * so the consumer can start before or after the producer.
 */
@Configuration
public class RabbitConfig {

    public static final String EXCHANGE = "delivery.exchange";
    public static final String QUEUE = "reviews.queue";
    public static final String ROUTING_KEY = "reviews.new";

    @Bean
    public TopicExchange deliveryExchange() {
        return new TopicExchange(EXCHANGE, true, false);
    }

    @Bean
    public Queue reviewsQueue() {
        return QueueBuilder.durable(QUEUE).build();
    }

    @Bean
    public Binding reviewsBinding(Queue reviewsQueue, TopicExchange deliveryExchange) {
        return BindingBuilder.bind(reviewsQueue).to(deliveryExchange).with(ROUTING_KEY);
    }

    @Bean
    public MessageConverter jsonMessageConverter() {
        return new JacksonJsonMessageConverter();
    }
}
