package br.com.fiap.delivery.review;

public record ReviewMessage(Long dishId, String dishName, int rating, String comment) {
}
