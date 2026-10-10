package br.com.fiap.delivery.review;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

@SpringBootTest(properties = {
        "eureka.client.enabled=false",
        "spring.rabbitmq.listener.simple.auto-startup=false",
        "review.flush-interval-ms=3600000"
})
class ReviewServiceTests {

    @Autowired
    ReviewBuffer buffer;

    @Autowired
    ReviewService service;

    @Autowired
    ReviewSummaryRepository repository;

    @BeforeEach
    void clean() {
        buffer.drain();
        repository.deleteAll();
    }

    @Test
    void flushPersistsBufferAndClearsIt() {
        buffer.add(1L, "House Burger", 5);
        buffer.add(1L, "House Burger", 4);

        service.flush();

        assertThat(buffer.size()).isZero();
        ReviewSummary summary = repository.findById(1L).orElseThrow();
        assertThat(summary.getRatingSum()).isEqualTo(9);
        assertThat(summary.getReviewCount()).isEqualTo(2);
    }

    @Test
    void successiveFlushesAreAccumulatedInTheDatabase() {
        buffer.add(1L, "House Burger", 5);
        service.flush();
        buffer.add(1L, "House Burger", 3);
        service.flush();

        ReviewSummary summary = repository.findById(1L).orElseThrow();
        assertThat(summary.getRatingSum()).isEqualTo(8);
        assertThat(summary.getReviewCount()).isEqualTo(2);
    }

    @Test
    void rankingIsSortedByAverageAndReadFromTheDatabase() {
        buffer.add(1L, "Pizza", 3);
        buffer.add(2L, "Burger", 5);
        buffer.add(2L, "Burger", 4);
        service.flush();
        // Still only in the buffer: must not appear in the ranking.
        buffer.add(3L, "Salad", 5);

        List<RankingItem> ranking = service.ranking();

        assertThat(ranking).extracting(RankingItem::dishName).containsExactly("Burger", "Pizza");
        assertThat(ranking.get(0).average()).isEqualTo(4.5);
        assertThat(ranking.get(0).count()).isEqualTo(2);
    }
}
