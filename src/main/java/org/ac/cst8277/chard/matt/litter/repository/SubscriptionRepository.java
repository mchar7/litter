package org.ac.cst8277.chard.matt.litter.repository;

import org.ac.cst8277.chard.matt.litter.model.Subscription;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Repository interface for Subscription entities.
 * Extends Spring's ReactiveMongoRepository for hands-off reactive MongoDB operations.
 * Spring's Repository annotation indicates that it's a repository.
 */
@Repository
public interface SubscriptionRepository extends ReactiveMongoRepository<Subscription, String> {

    /**
     * Method for finding subscriptions by the subscriber's user ID.
     *
     * @param subscriberId user ID of the subscriber
     * @return Flux of subscriptions found
     */
    Flux<Subscription> findBySubscriberId(ObjectId subscriberId);

    /**
     * Method for looking up a subscription by the subscriber and producer's ID.
     *
     * @param subscriberId user ID of the subscriber
     * @param producerId   userID of the producer
     * @return Mono of the subscription found
     */
    Mono<Subscription> findBySubscriberIdAndProducerId(ObjectId subscriberId, ObjectId producerId);
}