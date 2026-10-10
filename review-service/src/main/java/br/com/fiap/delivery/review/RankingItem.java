package br.com.fiap.delivery.review;

public record RankingItem(Long dishId, String dishName, double average, long count) {
}
