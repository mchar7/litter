package org.ac.cst8277.chard.matt.litter.repository;

import org.ac.cst8277.chard.matt.litter.model.Message;
import org.bson.types.ObjectId;
import org.springframework.data.mongodb.repository.ReactiveMongoRepository;
import org.springframework.stereotype.Repository;
import reactor.core.publisher.Flux;

/**
 * Repository interface for Message entities.
 * Extends Spring's ReactiveMongoRepository for hands-off reactive MongoDB operations.
 * Spring's Repository annotation indicates that it's a repository.
 */
@Repository
public interface MessageRepository extends ReactiveMongoRepository<Message, String> {

    /**
     * Method for getting messages by the producer's user ObjectID.
     *
     * @param producerId user ID of the producer
     * @return Flux of messages produced by the given producer
     */
    Flux<Message> findByProducerId(ObjectId producerId);
}