package br.com.fiap.delivery.order.error;

public class DishNotFoundException extends RuntimeException {

    public DishNotFoundException() {
        super("Dish not found");
    }
}
