package br.com.fiap.delivery.order.dish;

import java.util.List;

import br.com.fiap.delivery.order.error.DishNotFoundException;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class DishController {

    private final DishRepository dishRepository;

    public DishController(DishRepository dishRepository) {
        this.dishRepository = dishRepository;
    }

    @GetMapping("/dishes")
    public List<Dish> list() {
        return dishRepository.findAll();
    }

    @GetMapping("/dishes/{id}")
    public Dish get(@PathVariable Long id) {
        return dishRepository.findById(id).orElseThrow(DishNotFoundException::new);
    }
}
