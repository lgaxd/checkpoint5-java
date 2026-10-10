package br.com.fiap.delivery.order.dish;

import java.math.BigDecimal;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

@Component
public class DataLoader implements ApplicationRunner {

    /** Name of the promotional dish, which starts with exactly 10 units in stock. */
    public static final String PROMOTION_DISH = "House Burger";
    public static final int PROMOTION_STOCK = 10;

    private static final Logger log = LoggerFactory.getLogger(DataLoader.class);

    private final DishRepository dishRepository;

    public DataLoader(DishRepository dishRepository) {
        this.dishRepository = dishRepository;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (dishRepository.count() > 0) {
            return;
        }
        dishRepository.saveAll(List.of(
                new Dish(PROMOTION_DISH, "Brioche bun", new BigDecimal("39.90"), PROMOTION_STOCK),
                new Dish("Margherita Pizza", "Tomato sauce, mozzarella and fresh basil. Vegetarian",
                        new BigDecimal("54.90"), 50),
                new Dish("Vegan Bowl", "Quinoa, grilled vegetables, chickpeas and tahini. Vegan",
                        new BigDecimal("34.50"), 40),
                new Dish("Caesar Salad", "Romaine lettuce, parmesan, croutons and chicken",
                        new BigDecimal("29.90"), 30),
                new Dish("Chocolate Brownie", "Warm brownie with vanilla ice cream. Vegetarian",
                        new BigDecimal("19.90"), 25),
                new Dish("Fries Basket", "Crispy potato fries with house seasoning. Vegetarian",
                        new BigDecimal("22.00"), 60)));
        log.info("Loaded {} dishes", dishRepository.count());
    }
}
