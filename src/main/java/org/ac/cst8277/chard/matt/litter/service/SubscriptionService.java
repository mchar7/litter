package org.ac.cst8277.chard.matt.litter.service;

import org.ac.cst8277.chard.matt.litter.model.Subscription;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Service class for Subscription entities.
 */
@Service
public class SubscriptionService {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionService.class);

    private final SubscriptionRepository subscriptionRepository;
    private final UserManagementService userManagementService;

    /**
     * Constructor for the SubscriptionService.
     *
     * @param subscriptionRepo Repository for Subscription entities
     * @param usrMgmtSvc       Service for user management operations
     */
    @Autowired
    public SubscriptionService(SubscriptionRepository subscriptionRepo, UserManagementService usrMgmtSvc) {
        subscriptionRepository = subscriptionRepo;
        userManagementService = usrMgmtSvc;
    }

    /**
     * Subscribes a user to a producer.
     *
     * @param jwt              JWT of the subscribing user
     * @param producerUsername Username of the producer to subscribe to
     * @return Mono of the created Subscription
     */
    public Mono<Subscription> subscribe(JwtClaimAccessor jwt, String producerUsername) {
        // Validate producerUsername
        if (null == producerUsername || producerUsername.isBlank()) {
            return Mono.error(new IllegalArgumentException("Producer username must not be null or empty."));
        }

        Mono<User> subscriberMono = userManagementService.getUserByJwt(jwt);
        Mono<User> producerMono = userManagementService.getUserByUsername(producerUsername);

        return subscriberMono.zipWith(producerMono)
                .flatMap(tuple -> {
                    User subscriber = tuple.getT1();
                    User producer = tuple.getT2();
                    logger.info("Attempting to subscribe user {} to producer {}",
                            subscriber.getUsername(), producer.getUsername());
                    return subscriptionRepository.findBySubscriberIdAndProducerId(subscriber.getId(), producer.getId())
                            .flatMap(existingSubscription -> {
                                logger.warn("User {} is already subscribed to {}",
                                        subscriber.getUsername(), producer.getUsername());
                                return Mono.<Subscription>error(
                                        new IllegalArgumentException("User is already subscribed."));
                            })
                            .switchIfEmpty(Mono.defer(() -> {
                                Subscription subscription = new Subscription();
                                subscription.setSubscriberId(subscriber.getId());
                                subscription.setProducerId(producer.getId());
                                logger.info("Creating new subscription for user {} to producer {}",
                                        subscriber.getUsername(), producer.getUsername());
                                return subscriptionRepository.save(subscription);
                            }));
                });
    }

    /**
     * Unsubscribes a user from a producer.
     *
     * @param jwt              JWT of the unsubscribing user
     * @param producerUsername Username of the producer to unsubscribe from
     * @return Mono indicating completion
     */
    public Mono<Void> unsubscribe(JwtClaimAccessor jwt, String producerUsername) {
        return userManagementService.getUserByJwt(jwt)
                .zipWith(userManagementService.getUserByUsername(producerUsername))
                .flatMap(tuple -> {
                    User subscriber = tuple.getT1();
                    User producer = tuple.getT2();
                    logger.info("Attempting to unsubscribe user {} from producer {}",
                            subscriber.getUsername(), producer.getUsername());
                    return subscriptionRepository.findBySubscriberIdAndProducerId(subscriber.getId(), producer.getId())
                            .switchIfEmpty(Mono.error(
                                    new IllegalArgumentException("No subscription found for the given users.")))
                            .flatMap(subscription -> {
                                logger.info("Deleting subscription for user {} from producer {}",
                                        subscriber.getUsername(), producer.getUsername());
                                return subscriptionRepository.delete(subscription);
                            });
                });
    }

    /**
     * Retrieves subscriptions for a given user.
     *
     * @param jwt JWT of the user whose subscriptions to retrieve
     * @return Mono containing a Flux of subscriptions for the user
     */
    public Mono<Flux<Subscription>> getSubscriptionsForUser(JwtClaimAccessor jwt) {
        return userManagementService.getUserByJwt(jwt)
                .flatMap(user -> {
                    if (user.getRoles().contains(User.DB_USER_ROLE_SUBSCRIBER_NAME)) {
                        logger.info("Fetching subscriptions for user: {}", user.getUsername());
                        return Mono.just(subscriptionRepository.findBySubscriberId(user.getId()));
                    } else {
                        logger.warn(
                                "User {} attempted to fetch subscriptions without subscriber role", user.getUsername());
                        return Mono.error(new IllegalArgumentException("User is not a subscriber."));
                    }
                });
    }
}
