package br.com.fiap.delivery.order.review;

import jakarta.validation.Valid;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class ReviewController {

    private final ReviewPublisher reviewPublisher;

    public ReviewController(ReviewPublisher reviewPublisher) {
        this.reviewPublisher = reviewPublisher;
    }

    @PostMapping("/reviews")
    @ResponseStatus(HttpStatus.ACCEPTED)
    public void create(@Valid @RequestBody ReviewRequest request) {
        reviewPublisher.publish(request);
    }
}
