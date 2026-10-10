package br.com.fiap.delivery.payment;

import java.math.BigDecimal;

public record PaymentRequest(BigDecimal amount) {
}
