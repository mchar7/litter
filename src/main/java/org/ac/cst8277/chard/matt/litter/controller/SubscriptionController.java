package org.ac.cst8277.chard.matt.litter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
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
     * @param jwt              JWT representing the authenticated user (provided in the Authorization header)
     * @param producerUsername Username of the producer to subscribe to
     * @return Mono of the created subscription
     */
    @Operation(
            summary = "Create Subscription",
            description = "Subscribes the authenticated user to the specified producer. " +
                    "The path parameter must be a valid producer username. " +
                    "Requires a valid JWT Bearer token.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Subscription created successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Subscription.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid producer username"),
                    @ApiResponse(responseCode = "409", description = "Already subscribed to this producer")
            })
    @SecurityRequirement(name = "bearerAuth")
    @PutMapping("{producerUsername}")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Subscription> createSubscription(
            @Parameter(description = "JWT token representing the authenticated user")
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Username of the producer to subscribe to", required = true, example = "producer1")
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
     * @param jwt              JWT representing the authenticated user (provided in the Authorization header)
     * @param producerUsername Username of the producer to unsubscribe from
     * @return Mono signaling completion
     */
    @Operation(
            summary = "Delete Subscription",
            description = "Deletes the subscription for the authenticated user from the specified producer. " +
                    "Requires a valid JWT Bearer token.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Subscription deleted successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid producer username"),
                    @ApiResponse(responseCode = "404", description = "Subscription not found")
            })
    @SecurityRequirement(name = "bearerAuth")
    @DeleteMapping("{producerUsername}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteSubscription(
            @Parameter(description = "JWT token representing the authenticated user")
            @AuthenticationPrincipal Jwt jwt,
            @Parameter(description = "Username of the producer to unsubscribe from", required = true, example = "producer1")
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
     * Retrieves all subscriptions for the authenticated subscriber.
     *
     * @param jwt JWT representing the authenticated user (provided in the Authorization header)
     * @return Flux of subscriptions for the user
     */
    @Operation(
            summary = "Get Subscriptions",
            description = "Retrieves all subscriptions for the authenticated subscriber. Requires a valid JWT Bearer token.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Subscriptions retrieved successfully",
                            content = @Content(mediaType = "application/json",
                                    schema = @Schema(type = "array", implementation = Subscription.class))),
                    @ApiResponse(responseCode = "404", description = "No subscriptions found or user not a subscriber")})
    @SecurityRequirement(name = "bearerAuth")
    @GetMapping("")
    public Flux<Subscription> getSubscriptions(
            @Parameter(description = "JWT token representing the authenticated user")
            @AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.getSubscriptionsForUser(jwt)
                .doFirst(() -> log.info("Retrieving subscriptions for user {}", LogSanitizer.sanitize(jwt.getSubject())))
                .doOnComplete(() -> log.info("Retrieved subscriptions for user {}", LogSanitizer.sanitize(jwt.getSubject())))
                .doOnError(e -> log.error("Failed to retrieve subscriptions for user {}: {}",
                        LogSanitizer.sanitize(jwt.getSubject()),
                        LogSanitizer.sanitize(e.getMessage())));
    }
}
