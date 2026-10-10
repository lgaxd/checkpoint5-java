package br.com.fiap.delivery.order.error;

/** A dependency (payment, message broker, AI model) failed: mapped to HTTP 502. */
public class ExternalServiceException extends RuntimeException {

    public ExternalServiceException(String message, Throwable cause) {
        super(message, cause);
    }
}
