package br.com.fiap.delivery.order.order;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import br.com.fiap.delivery.order.dish.Dish;
import br.com.fiap.delivery.order.dish.DishRepository;
import br.com.fiap.delivery.order.error.DishNotFoundException;
import br.com.fiap.delivery.order.error.ExternalServiceException;
import br.com.fiap.delivery.order.error.OrderNotFoundException;
import br.com.fiap.delivery.order.error.OutOfStockException;
import br.com.fiap.delivery.order.payment.PaymentClient;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final DishRepository dishRepository;
    private final OrderRepository orderRepository;
    private final PaymentClient paymentClient;

    public OrderService(DishRepository dishRepository, OrderRepository orderRepository,
                        PaymentClient paymentClient) {
        this.dishRepository = dishRepository;
        this.orderRepository = orderRepository;
        this.paymentClient = paymentClient;
    }

    /**
     * The dish row stays locked (PESSIMISTIC_WRITE) for the whole transaction, so concurrent orders for
     * the same dish are serialized and the stock can never go below zero. Any failure (out of stock or
     * payment) rolls the transaction back, leaving the stock intact.
     */
    @Transactional
    public CustomerOrder create(Long dishId, int quantity) {
        Dish dish = dishRepository.findByIdForUpdate(dishId).orElseThrow(DishNotFoundException::new);

        if (!dish.hasStockFor(quantity)) {
            throw new OutOfStockException();
        }

        BigDecimal totalPrice = dish.getPrice().multiply(BigDecimal.valueOf(quantity));
        try {
            paymentClient.charge(totalPrice);
        } catch (RuntimeException e) {
            log.warn("Payment for dish {} (quantity {}) failed after all attempts", dishId, quantity);
            throw new ExternalServiceException("Payment service unavailable", e);
        }

        dish.decreaseStock(quantity);
        dishRepository.save(dish);
        return orderRepository.save(new CustomerOrder(dishId, quantity, totalPrice, OrderStatus.CONFIRMED,
                LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS)));
    }

    @Transactional(readOnly = true)
    public CustomerOrder get(Long id) {
        return orderRepository.findById(id).orElseThrow(OrderNotFoundException::new);
    }
}
