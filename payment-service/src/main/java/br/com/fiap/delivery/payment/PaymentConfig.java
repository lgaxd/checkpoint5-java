package br.com.fiap.delivery.payment;

import java.util.Random;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class PaymentConfig {

    @Bean
    public Random random() {
        return new Random();
    }
}
