package br.com.fiap.delivery.order.ratelimit;

import java.util.function.LongSupplier;

/** Thread-safe token bucket: {@code permitsPerSecond} tokens refilled continuously, burst up to the same value. */
public class TokenBucket {

    private final double capacity;
    private final double tokensPerNano;
    private final LongSupplier nanoClock;

    private double tokens;
    private long lastRefill;

    public TokenBucket(int permitsPerSecond) {
        this(permitsPerSecond, System::nanoTime);
    }

    public TokenBucket(int permitsPerSecond, LongSupplier nanoClock) {
        this.capacity = permitsPerSecond;
        this.tokensPerNano = permitsPerSecond / 1_000_000_000.0;
        this.nanoClock = nanoClock;
        this.tokens = permitsPerSecond;
        this.lastRefill = nanoClock.getAsLong();
    }

    public synchronized boolean tryAcquire() {
        long now = nanoClock.getAsLong();
        tokens = Math.min(capacity, tokens + (now - lastRefill) * tokensPerNano);
        lastRefill = now;
        if (tokens >= 1.0) {
            tokens -= 1.0;
            return true;
        }
        return false;
    }
}
