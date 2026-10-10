package br.com.fiap.delivery.order.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.concurrent.atomic.AtomicLong;

import org.junit.jupiter.api.Test;

class TokenBucketTests {

    private static final long SECOND = 1_000_000_000L;

    @Test
    void allowsTwentyRequestsInABurstAndRejectsTheTwentyFirst() {
        AtomicLong clock = new AtomicLong();
        TokenBucket bucket = new TokenBucket(20, clock::get);

        for (int i = 0; i < 20; i++) {
            assertThat(bucket.tryAcquire()).as("request %d", i + 1).isTrue();
        }
        assertThat(bucket.tryAcquire()).isFalse();
    }

    @Test
    void refillsContinuouslyOverTime() {
        AtomicLong clock = new AtomicLong();
        TokenBucket bucket = new TokenBucket(20, clock::get);
        for (int i = 0; i < 20; i++) {
            bucket.tryAcquire();
        }

        clock.addAndGet(SECOND / 20); // 50ms => exactly one token
        assertThat(bucket.tryAcquire()).isTrue();
        assertThat(bucket.tryAcquire()).isFalse();

        clock.addAndGet(SECOND); // a full second => full bucket, never above capacity
        int accepted = 0;
        for (int i = 0; i < 30; i++) {
            if (bucket.tryAcquire()) {
                accepted++;
            }
        }
        assertThat(accepted).isEqualTo(20);
    }
}
