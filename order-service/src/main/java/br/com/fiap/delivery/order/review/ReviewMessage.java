package br.com.fiap.delivery.order.review;

/** Payload published to RabbitMQ as JSON. */
public record ReviewMessage(Long dishId, String dishName, int rating, String comment) {
}
