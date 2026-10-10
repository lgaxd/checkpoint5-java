package br.com.fiap.delivery.review;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * In-memory accumulator of reviews grouped by dish. Only the sum and the count of ratings are kept,
 * so memory usage is bounded by the number of dishes regardless of how many reviews arrive.
 */
@Component
public class ReviewBuffer {

    public record Entry(String dishName, long ratingSum, long reviewCount) {

        Entry merge(Entry other) {
            return new Entry(other.dishName != null ? other.dishName : dishName,
                    ratingSum + other.ratingSum, reviewCount + other.reviewCount);
        }
    }

    private final ConcurrentHashMap<Long, Entry> buffer = new ConcurrentHashMap<>();

    public void add(Long dishId, String dishName, int rating) {
        buffer.merge(dishId, new Entry(dishName, rating, 1), Entry::merge);
    }

    /** Puts previously drained entries back (used when persistence fails). */
    public void addAll(Map<Long, Entry> entries) {
        entries.forEach((dishId, entry) -> buffer.merge(dishId, entry, Entry::merge));
    }

    /**
     * Removes every entry from the buffer. Each key is removed with a single atomic operation, so a
     * review that arrives concurrently is either part of this snapshot or of the next one.
     */
    public Map<Long, Entry> drain() {
        Map<Long, Entry> snapshot = new HashMap<>();
        for (Long dishId : buffer.keySet()) {
            Entry entry = buffer.remove(dishId);
            if (entry != null) {
                snapshot.put(dishId, entry);
            }
        }
        return snapshot;
    }

    public int size() {
        return buffer.size();
    }
}
