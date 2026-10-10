package br.com.fiap.delivery.review;

import jakarta.persistence.Entity;
import jakarta.persistence.Id;

@Entity
public class ReviewSummary {

    /** The dish id is the primary key: there is exactly one summary per dish. */
    @Id
    private Long dishId;

    private String dishName;

    private long ratingSum;

    private long reviewCount;

    protected ReviewSummary() {
    }

    public ReviewSummary(Long dishId, String dishName) {
        this.dishId = dishId;
        this.dishName = dishName;
    }

    public void add(String dishName, long ratingSum, long reviewCount) {
        if (dishName != null) {
            this.dishName = dishName;
        }
        this.ratingSum += ratingSum;
        this.reviewCount += reviewCount;
    }

    public double average() {
        return reviewCount == 0 ? 0.0 : (double) ratingSum / reviewCount;
    }

    public Long getDishId() {
        return dishId;
    }

    public String getDishName() {
        return dishName;
    }

    public long getRatingSum() {
        return ratingSum;
    }

    public long getReviewCount() {
        return reviewCount;
    }
}
