package org.ac.cst8277.chard.matt.litter.service;

import jakarta.validation.constraints.NotBlank;
import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.model.Subscription;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.repository.SubscriptionRepository;
import org.ac.cst8277.chard.matt.litter.security.LogSanitizer;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import static org.ac.cst8277.chard.matt.litter.model.User.DB_USER_ROLE_SUBSCRIBER_NAME;

/**
 * Service class for Subscription entities.
 */
@Slf4j
@Service
public class SubscriptionService {
    private static final String PRODUCER_NOT_FOUND_MESSAGE = "Producer not found";
    private final SubscriptionRepository subscriptionRepository;
    private final UserManagementService userManagementService;

    /**
     * Constructor for the SubscriptionService.
     *
     * @param subRepo    Repository for Subscription entities
     * @param usrMgmtSvc Service for user management operations
     */
    public SubscriptionService(SubscriptionRepository subRepo, UserManagementService usrMgmtSvc) {
        subscriptionRepository = subRepo;
        userManagementService = usrMgmtSvc;
    }

    /**
     * Subscribes a user to a producer.
     *
     * @param jwt              JWT of the subscribing user
     * @param producerUsername Username of the producer to subscribe to
     * @return Mono of the created Subscription
     */
    public Mono<Subscription> subscribe(JwtClaimAccessor jwt, @NotBlank String producerUsername) {
        if (UserManagementService.isUsernameErroneous(producerUsername)) {
            return Mono.error(new IllegalArgumentException("Invalid producer username"));
        }
        Mono<User> subscriberMono = userManagementService.getUserByJwt(jwt);
        Mono<User> producerMono = userManagementService.getUserByUsername(producerUsername);
        return subscriberMono.doFirst(() -> log.info("Attempting to subscribe user '{}' to producer '{}'",
                        LogSanitizer.sanitize(jwt.getSubject()), LogSanitizer.sanitize(producerUsername)))
                .switchIfEmpty(Mono.error(new AccessDeniedException("User not found")))
                .flatMap(subscriber -> producerMono
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(PRODUCER_NOT_FOUND_MESSAGE)))
                        .flatMap(producer -> findSubscription(subscriber, producer)
                                .doOnSuccess(sub -> log.warn("Attempted to create pre-existing subscription."))
                                .switchIfEmpty(Mono.defer(() -> createNewSubscription(subscriber, producer)))));
    }

    /**
     * Creates a new subscription between two users.
     *
     * @param subscriber User who is subscribing
     * @param producer   User who is producing content
     * @return Mono of the created Subscription
     */
    private Mono<Subscription> createNewSubscription(User subscriber, User producer) {
        Subscription sub = new Subscription();
        sub.setSubscriberId(subscriber.getId());
        sub.setProducerId(producer.getId());
        return subscriptionRepository.save(sub);
    }

    /**
     * Unsubscribes a user from a producer.
     *
     * @param jwt              JWT of the unsubscribing user
     * @param producerUsername Username of the producer to unsubscribe from
     * @return Mono indicating completion
     */
    public Mono<Void> unsubscribe(JwtClaimAccessor jwt, @NotBlank String producerUsername) {
        if (UserManagementService.isUsernameErroneous(producerUsername)) {
            return Mono.error(new IllegalArgumentException("Invalid producer username"));
        }
        return userManagementService.getUserByJwt(jwt)
                .doFirst(() -> log.info("Attempting to unsubscribe subscriber '{}' from producer '{}'",
                        LogSanitizer.sanitize(jwt.getSubject()), LogSanitizer.sanitize(producerUsername)))
                .switchIfEmpty(Mono.error(new AccessDeniedException("User not found")))
                .flatMap(subscriber -> userManagementService.getUserByUsername(producerUsername)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException(PRODUCER_NOT_FOUND_MESSAGE)))
                        .flatMap(producer -> findSubscription(subscriber, producer)
                                .switchIfEmpty(Mono.error(new IllegalArgumentException("Subscription not found")))
                                .flatMap(subscriptionRepository::delete)));
    }

    /**
     * Finds and deletes a subscription between two users.
     *
     * @param subscriber User who is subscribed
     * @param producer   User who is producing content
     * @return Mono indicating completion
     */
    private Mono<Subscription> findSubscription(User subscriber, User producer) {
        return subscriptionRepository.getSubscriptionBySubscriberIdAndProducerId(subscriber.getId(), producer.getId())
                .doOnNext(sub -> log.info("Found subscription between '{}' and '{}'",
                        LogSanitizer.sanitize(subscriber.getUsername()), LogSanitizer.sanitize(producer.getUsername())));
    }

    /**
     * Retrieves subscriptions for a given user.
     *
     * @param jwt JWT of the user whose subscriptions to retrieve
     * @return Flux of subscriptions for the user
     */
    public Flux<Subscription> getSubscriptionsForUser(JwtClaimAccessor jwt) {
        return userManagementService.getUserByJwt(jwt)
                .flatMapMany(user -> {
                    if (user.getRoles().contains(DB_USER_ROLE_SUBSCRIBER_NAME)) {
                        log.info("Fetching subscriptions for user: {}", LogSanitizer.sanitize(user.getUsername()));
                        return subscriptionRepository.getSubscriptionsBySubscriberId((user.getId()));
                    } else {
                        return Flux.error(new IllegalArgumentException("User is not a subscriber."));
                    }
                });
    }
}
