package br.com.fiap.delivery.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Map;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

class ReviewBufferTests {

    @Test
    void accumulatesSumAndCountPerDish() {
        ReviewBuffer buffer = new ReviewBuffer();
        buffer.add(1L, "Burger", 5);
        buffer.add(1L, "Burger", 4);
        buffer.add(2L, "Pizza", 3);

        Map<Long, ReviewBuffer.Entry> snapshot = buffer.drain();

        assertThat(snapshot).hasSize(2);
        assertThat(snapshot.get(1L)).isEqualTo(new ReviewBuffer.Entry("Burger", 9, 2));
        assertThat(snapshot.get(2L)).isEqualTo(new ReviewBuffer.Entry("Pizza", 3, 1));
    }

    @Test
    void drainClearsTheBuffer() {
        ReviewBuffer buffer = new ReviewBuffer();
        buffer.add(1L, "Burger", 5);

        buffer.drain();

        assertThat(buffer.size()).isZero();
        assertThat(buffer.drain()).isEmpty();
    }

    @Test
    void doesNotLoseReviewsAddedConcurrentlyWithDrain() throws Exception {
        ReviewBuffer buffer = new ReviewBuffer();
        int total = 20_000;
        ExecutorService executor = Executors.newFixedThreadPool(8);
        long drained = 0;
        try {
            IntStream.range(0, total).forEach(i -> executor.submit(() -> buffer.add(1L, "Burger", 5)));
            executor.shutdown();
            while (!executor.awaitTermination(1, TimeUnit.MILLISECONDS)) {
                drained += count(buffer.drain());
            }
            drained += count(buffer.drain());
        } finally {
            executor.shutdownNow();
        }
        assertThat(drained).isEqualTo(total);
    }

    private long count(Map<Long, ReviewBuffer.Entry> snapshot) {
        return snapshot.values().stream().mapToLong(ReviewBuffer.Entry::reviewCount).sum();
    }
}
