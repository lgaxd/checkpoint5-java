package br.com.fiap.delivery.order.error;

public class OutOfStockException extends RuntimeException {

    public OutOfStockException() {
        super("Dish out of stock");
    }
}
