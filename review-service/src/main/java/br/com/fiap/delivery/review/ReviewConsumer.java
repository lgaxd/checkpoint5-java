package br.com.fiap.delivery.review;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Component
public class ReviewConsumer {

    private static final Logger log = LoggerFactory.getLogger(ReviewConsumer.class);

    private final ReviewBuffer buffer;

    public ReviewConsumer(ReviewBuffer buffer) {
        this.buffer = buffer;
    }

    @RabbitListener(queues = RabbitConfig.QUEUE)
    public void receive(ReviewMessage message) {
        log.debug("Review received for dish {} (rating {})", message.dishId(), message.rating());
        buffer.add(message.dishId(), message.dishName(), message.rating());
    }
}
