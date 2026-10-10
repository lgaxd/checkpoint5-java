package br.com.fiap.delivery.payment;

import java.util.Map;
import java.util.Random;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class PaymentController {

    private static final Logger log = LoggerFactory.getLogger(PaymentController.class);

    private final Random random;
    private final Environment environment;
    private final double failureRate;

    public PaymentController(Random random, Environment environment,
                             @Value("${payment.failure-rate:0.5}") double failureRate) {
        this.random = random;
        this.environment = environment;
        this.failureRate = failureRate;
    }

    @PostMapping("/payments")
    public ResponseEntity<?> pay(@RequestBody PaymentRequest request) {
        int port = currentPort();

        if (random.nextDouble() < failureRate) {
            log.warn("[instance {}] Payment of {} FAILED (simulated failure)", port, request.amount());
            return ResponseEntity.status(500).body(Map.of("error", "Payment processing failed", "instance", port));
        }

        log.info("[instance {}] Payment of {} APPROVED", port, request.amount());
        return ResponseEntity.ok(new PaymentResponse("APPROVED", port));
    }

    /** Port of the instance that actually served the request (works with --server.port overrides and random ports). */
    private int currentPort() {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "0"));
        return Integer.parseInt(port);
    }
}
