package org.ac.cst8277.chard.matt.litter.controller;

import org.ac.cst8277.chard.matt.litter.model.Subscription;
import org.ac.cst8277.chard.matt.litter.service.SubscriptionService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controller for handling subscription-related requests.
 */
@RestController
@RequestMapping("/subscriptions")
public class SubscriptionController {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionController.class);
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
     * Endpoint for creating a new subscription.
     *
     * @param jwt              Jwt used by the authenticated user, resolved by Spring Security
     * @param producerUsername username of the producer to subscribe to
     * @return a Mono of ResponseEntity containing the created Subscription or an error response
     */
    @PutMapping({"{producerUsername}", "/{producerUsername}/"})
    public Mono<ResponseEntity<Subscription>> createSubscription(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String producerUsername) {
        if (null == producerUsername || producerUsername.isEmpty()) {
            logger.warn("Attempt to create subscription with invalid producer username");
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return subscriptionService.subscribe(jwt, producerUsername)
                .map(subscription -> {
                    logger.info("Subscription created: user {} subscribed to {}", jwt.getSubject(), producerUsername);
                    return ResponseEntity.ok(subscription);
                })
                .onErrorResume(e -> {
                    logger.error("Error creating subscription: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * Endpoint for deleting a subscription.
     *
     * @param jwt              Jwt used by the authenticated user, resolved by Spring Security
     * @param producerUsername username of the producer to unsubscribe from
     * @return HTTP response entity
     */
    @DeleteMapping({"{producerUsername}", "{producerUsername}/"})
    public Mono<ResponseEntity<Void>> deleteSubscription(
            @AuthenticationPrincipal Jwt jwt,
            @PathVariable String producerUsername
    ) {
        if (null == producerUsername || producerUsername.isEmpty()) {
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return subscriptionService.unsubscribe(jwt, producerUsername)
                .then(Mono.defer(() -> {
                    // log the success
                    logger.info(
                            "Subscription deleted: user {} unsubscribed from {}",
                            jwt.getSubject(),
                            producerUsername
                    );
                    // force <Void> here, returning 204 No Content
                    return Mono.just(ResponseEntity.noContent().<Void>build());
                }))
                .onErrorResume(e -> {
                    logger.error("Error deleting subscription: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * Endpoint for getting all subscriptions for the authenticated user.
     *
     * @param jwt Jwt used by the authenticated user, resolved by Spring Security
     * @return Mono of ResponseEntity containing a Flux of subscriptions for the user
     */
    @GetMapping({"", "/"})
    public Mono<ResponseEntity<Flux<Subscription>>> getSubscriptions(@AuthenticationPrincipal Jwt jwt) {
        return subscriptionService.getSubscriptionsForUser(jwt)
                .map(subscriptions -> {
                    logger.info("Retrieved subscriptions for user: {}", jwt.getSubject());
                    return ResponseEntity.ok(subscriptions);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }
}