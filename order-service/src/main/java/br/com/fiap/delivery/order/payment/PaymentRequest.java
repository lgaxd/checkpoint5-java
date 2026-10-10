package br.com.fiap.delivery.order.payment;

import java.math.BigDecimal;

public record PaymentRequest(BigDecimal amount) {
}
