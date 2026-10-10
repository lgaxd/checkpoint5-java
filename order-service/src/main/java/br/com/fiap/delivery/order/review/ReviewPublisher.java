package br.com.fiap.delivery.order.review;

import br.com.fiap.delivery.order.config.RabbitConfig;
import br.com.fiap.delivery.order.dish.Dish;
import br.com.fiap.delivery.order.dish.DishRepository;
import br.com.fiap.delivery.order.error.DishNotFoundException;
import br.com.fiap.delivery.order.error.ExternalServiceException;

import org.springframework.amqp.AmqpException;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Service;

/** Producer side of the review flow: reviews are only published, never written to the database here. */
@Service
public class ReviewPublisher {

    private final DishRepository dishRepository;
    private final RabbitTemplate rabbitTemplate;

    public ReviewPublisher(DishRepository dishRepository, RabbitTemplate rabbitTemplate) {
        this.dishRepository = dishRepository;
        this.rabbitTemplate = rabbitTemplate;
    }

    public void publish(ReviewRequest request) {
        Dish dish = dishRepository.findById(request.dishId()).orElseThrow(DishNotFoundException::new);
        ReviewMessage message = new ReviewMessage(dish.getId(), dish.getName(), request.rating(),
                request.comment());
        try {
            rabbitTemplate.convertAndSend(RabbitConfig.EXCHANGE, RabbitConfig.ROUTING_KEY, message);
        } catch (AmqpException e) {
            throw new ExternalServiceException("Review queue unavailable", e);
        }
    }
}
