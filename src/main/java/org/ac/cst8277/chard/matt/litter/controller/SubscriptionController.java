package org.ac.cst8277.chard.matt.litter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.model.Subscription;
import org.ac.cst8277.chard.matt.litter.security.LogSanitizer;
import org.ac.cst8277.chard.matt.litter.service.SubscriptionService;
import org.springframework.beans.factory.annotation.Autowired;
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
@RequestMapping("/subscriptions")
@Tag(name = "Subscription API", description = "Endpoints for managing subscriptions")
public class SubscriptionController {
    private final SubscriptionService subscriptionService;

    /**
     * Constructor for SubscriptionController class.
     *
     * @param subscriptionService Service for handling subscription-related operations
     */
    @Autowired
    public SubscriptionController(SubscriptionService subscriptionService) {
        this.subscriptionService = subscriptionService;
    }

    /**
     * Creates a new subscription to a producer.
     *
     * @param jwt              Jwt used by the authenticated user, resolved by Spring Security
     * @param producerUsername username of the producer to subscribe to
     * @return Mono of the created subscription
     */
    @Operation(summary = "Create Subscription", description = "Subscribes the authenticated user to the specified producer")
    @ApiResponse(responseCode = "201", description = "Subscription created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Subscription.class)))
    @ApiResponse(responseCode = "400", description = "Invalid producer username", content = @Content)
    @ApiResponse(responseCode = "409", description = "Already subscribed to this producer", content = @Content)
    @PutMapping("{producerUsername}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Subscription> createSubscription(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Username of the producer to subscribe to", required = true)
            @PathVariable String producerUsername) {
        return subscriptionService.subscribe(jwt, producerUsername)
                .doFirst(() -> log.info("User {} attempting to subscribe to producer {}",
                        LogSanitizer.sanitize(jwt.getSubject()),
                        LogSanitizer.sanitize(producerUsername)))
                .doOnSuccess(subscription -> log.info("User {} subscribed to producer {}",
                        LogSanitizer.sanitize(jwt.getSubject()),
                        LogSanitizer.sanitize(producerUsername)))
                .doOnError(e -> log.error("Failed to create subscription for user {} to producer {}: {}",
                        LogSanitizer.sanitize(jwt.getSubject()),
                        LogSanitizer.sanitize(producerUsername),
                        LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Deletes a subscription to a producer.
     *
     * @param jwt              Jwt used by the authenticated user, resolved by Spring Security
     * @param producerUsername username of the producer to unsubscribe from
     * @return Mono of completion
     */
    @Operation(summary = "Delete Subscription", description = "Deletes the subscription for the authenticated user from the specified producer")
    @ApiResponse(responseCode = "204", description = "Subscription deleted successfully", content = @Content)
    @ApiResponse(responseCode = "400", description = "Invalid producer username", content = @Content)
    @ApiResponse(responseCode = "404", description = "Subscription not found", content = @Content)
    @DeleteMapping("{producerUsername}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteSubscription(
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Username of the producer to unsubscribe from", required = true)
            @PathVariable String producerUsername) {
        return subscriptionService.unsubscribe(jwt, producerUsername)
                .doFirst(() -> log.info("User {} attempting to unsubscribe from producer {}",
                        LogSanitizer.sanitize(jwt.getSubject()),
                        LogSanitizer.sanitize(producerUsername)))
                .doOnSuccess(v -> log.info("User {} unsubscribed from producer {}",
                        LogSanitizer.sanitize(jwt.getSubject()),
                        LogSanitizer.sanitize(producerUsername)))
                .doOnError(e -> log.error("Failed to delete subscription for user {} from producer {}: {}",
                        LogSanitizer.sanitize(jwt.getSubject()),
                        LogSanitizer.sanitize(producerUsername),
                        LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Retrieves all subscriptions for the authenticated user.
     *
     * @param jwt Jwt used by the authenticated user, resolved by Spring Security
     * @return Flux of subscriptions for the user
     */
    @Operation(summary = "Get Subscriptions", description = "Retrieves all subscriptions for the authenticated subscriber")
    @ApiResponse(responseCode = "200", description = "Subscriptions retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Subscription.class)))
    @ApiResponse(responseCode = "404", description = "No subscriptions found or user not a subscriber", content = @Content)
    @GetMapping("")
    public Flux<Subscription> getSubscriptions(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.getSubscriptionsForUser(jwt)
                .doFirst(() -> log.info("Retrieving subscriptions for user {}", LogSanitizer.sanitize(jwt.getSubject())))
                .doOnComplete(() -> log.info("Retrieved subscriptions for user {}", LogSanitizer.sanitize(jwt.getSubject())))
                .doOnError(e -> log.error("Failed to retrieve subscriptions for user {}: {}",
                        LogSanitizer.sanitize(jwt.getSubject()),
                        LogSanitizer.sanitize(e.getMessage())));
    }
}
