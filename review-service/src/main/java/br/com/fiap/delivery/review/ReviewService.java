package br.com.fiap.delivery.review;

import java.util.Comparator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ReviewService {

    private static final Logger log = LoggerFactory.getLogger(ReviewService.class);

    private final ReviewBuffer buffer;
    private final ReviewSummaryRepository repository;

    public ReviewService(ReviewBuffer buffer, ReviewSummaryRepository repository) {
        this.buffer = buffer;
        this.repository = repository;
    }

    /** Persists the accumulated data and clears the buffer. Runs every five seconds by default. */
    @Scheduled(fixedRateString = "${review.flush-interval-ms:5000}")
    public void flush() {
        Map<Long, ReviewBuffer.Entry> snapshot = buffer.drain();
        if (snapshot.isEmpty()) {
            return;
        }
        try {
            persist(snapshot);
            log.info("Flushed reviews of {} dish(es) to the database", snapshot.size());
        } catch (RuntimeException e) {
            log.error("Failed to persist reviews, keeping them in the buffer", e);
            buffer.addAll(snapshot);
        }
    }

    @Transactional
    public void persist(Map<Long, ReviewBuffer.Entry> snapshot) {
        snapshot.forEach((dishId, entry) -> {
            ReviewSummary summary = repository.findById(dishId)
                    .orElseGet(() -> new ReviewSummary(dishId, entry.dishName()));
            summary.add(entry.dishName(), entry.ratingSum(), entry.reviewCount());
            repository.save(summary);
        });
    }

    @Transactional(readOnly = true)
    public List<RankingItem> ranking() {
        return repository.findAll().stream()
                .sorted(Comparator.comparingDouble(ReviewSummary::average).reversed()
                        .thenComparing(Comparator.comparingLong(ReviewSummary::getReviewCount).reversed()))
                .map(s -> new RankingItem(s.getDishId(), s.getDishName(),
                        Math.round(s.average() * 100.0) / 100.0, s.getReviewCount()))
                .toList();
    }
}
