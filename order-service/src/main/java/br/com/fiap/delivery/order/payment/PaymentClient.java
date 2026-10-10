package br.com.fiap.delivery.order.payment;

import java.math.BigDecimal;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.resilience.annotation.Retryable;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

/**
 * Calls the payment service by its Eureka name (load balanced, no host or port hard-coded).
 * Kept in its own bean so the {@link Retryable} proxy is applied to calls coming from other beans.
 */
@Component
public class PaymentClient {

    private static final Logger log = LoggerFactory.getLogger(PaymentClient.class);

    static final String PAYMENT_URL = "http://PAYMENT-SERVICE/payments";

    private final RestTemplate restTemplate;

    public PaymentClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @Retryable(
            maxRetriesString = "${payment.retry.max-retries}",
            delayString = "${payment.retry.delay}",
            multiplierString = "${payment.retry.multiplier}",
            jitterString = "${payment.retry.jitter}",
            maxDelayString = "${payment.retry.max-delay}")
    public PaymentResponse charge(BigDecimal amount) {
        log.info("Requesting payment of {}", amount);
        PaymentResponse response = restTemplate.postForObject(PAYMENT_URL, new PaymentRequest(amount),
                PaymentResponse.class);
        if (response == null || !"APPROVED".equals(response.status())) {
            throw new IllegalStateException("Payment was not approved");
        }
        log.info("Payment approved by instance {}", response.instance());
        return response;
    }
}
