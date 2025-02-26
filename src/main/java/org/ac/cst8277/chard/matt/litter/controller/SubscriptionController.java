package org.ac.cst8277.chard.matt.litter.controller;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.model.Subscription;
import org.ac.cst8277.chard.matt.litter.security.LogSanitizer;
import org.ac.cst8277.chard.matt.litter.service.SubscriptionService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controller for handling subscription-related requests.
 */
@Slf4j
@RestController
@Tag(name = "Subscription API")
@RequestMapping("/subscriptions")
@SecurityScheme(type = SecuritySchemeType.HTTP, in = SecuritySchemeIn.HEADER, scheme = "bearer", bearerFormat = "JWT")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    /**
     * Constructor for SubscriptionController class.
     *
     * @param subscriptionService Service for handling subscription-related operations
     */
    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Creates a new subscription to a producer.
     *
     * <p>This endpoint returns a reactive Mono that emits the created subscription and responds with a 201 Created HTTP status.
     * The authenticated user subscribes to the specified producer. If the subscription already exists or
     * if the producer doesn't exist, appropriate error responses will be returned.
     *
     * @param jwt              JWT representing the authenticated user
     * @param producerUsername Username of the producer to subscribe to
     * @return A Mono emitting the created subscription with HTTP 201 Created status.
     */
    @PutMapping("{producerUsername}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Subscription> createSubscription(@AuthenticationPrincipal Jwt jwt, @PathVariable String producerUsername) {
        return subscriptionService.subscribe(jwt, producerUsername)
                .doFirst(() -> log.info("User {} attempting to subscribe to producer {}",
                        LogSanitizer.sanitize(jwt.getSubject()), LogSanitizer.sanitize(producerUsername)))
                .doOnSuccess(subscription -> log.info("User {} subscribed to producer {}",
                        LogSanitizer.sanitize(jwt.getSubject()), LogSanitizer.sanitize(producerUsername)))
                .doOnError(e -> log.error("Failed to create subscription for user {} to producer {}. Error: {}",
                        LogSanitizer.sanitize(jwt.getSubject()), LogSanitizer.sanitize(producerUsername), e.getMessage(), e));
    }

    /**
     * Deletes a subscription to a producer.
     *
     * <p>This endpoint returns a reactive Mono that signals completion
     * and responds with a 204 No Content HTTP status upon successful deletion.
     * The authenticated user unsubscribes from the specified producer. If the subscription doesn't exist
     * or if the producer doesn't exist, appropriate error responses will be returned.
     *
     * @param jwt              JWT representing the authenticated user
     * @param producerUsername Username of the producer to unsubscribe from
     * @return A Mono signaling completion with HTTP 204 No Content status.
     */
    @DeleteMapping("{producerUsername}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteSubscription(@AuthenticationPrincipal Jwt jwt, @PathVariable String producerUsername) {
        return subscriptionService.unsubscribe(jwt, producerUsername)
                .doFirst(() -> log.info("User {} attempting to unsubscribe from producer {}",
                        LogSanitizer.sanitize(jwt.getSubject()), LogSanitizer.sanitize(producerUsername)))
                .doOnSuccess(v -> log.info("User {} unsubscribed from producer {}",
                        LogSanitizer.sanitize(jwt.getSubject()), LogSanitizer.sanitize(producerUsername)))
                .doOnError(e -> log.error("Failed to delete subscription for user {} from producer {}. Error: {}",
                        LogSanitizer.sanitize(jwt.getSubject()), LogSanitizer.sanitize(producerUsername), e.getMessage(), e));
    }

    /**
     * Retrieves all subscriptions for the authenticated subscriber.
     *
     * <p>This endpoint returns a reactive Flux that emits subscriptions for the authenticated user
     * and responds with a 200 OK HTTP status. If no subscriptions are found, an empty Flux is returned
     * which will result in an empty array in the response.
     *
     * @param jwt JWT representing the authenticated user
     * @return A Flux emitting the subscriptions for the user with HTTP 200 OK status.
     */
    @GetMapping("")
    public Flux<Subscription> getSubscriptions(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.getSubscriptionsForUser(jwt)
                .doFirst(() -> log.info("Retrieving subscriptions for user {}", LogSanitizer.sanitize(jwt.getSubject())))
                .doOnComplete(() -> log.info("Retrieved subscriptions for user {}", LogSanitizer.sanitize(jwt.getSubject())))
                .doOnError(e -> log.error("Failed to retrieve subscriptions for user {}. Error: {}",
                        LogSanitizer.sanitize(jwt.getSubject()), e.getMessage(), e));
    }
}
