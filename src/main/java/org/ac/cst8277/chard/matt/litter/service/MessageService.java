package org.ac.cst8277.chard.matt.litter.service;

import org.ac.cst8277.chard.matt.litter.model.Message;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.repository.MessageRepository;
import org.ac.cst8277.chard.matt.litter.repository.SubscriptionRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Service class for Message objects.
 */
@Service
public class MessageService {
    private static final Logger logger = LoggerFactory.getLogger(MessageService.class);

    private final MessageRepository messageRepository;
    private final SubscriptionRepository subscriptionRepository;
    private final UserManagementService userManagementService;

    /**
     * Constructor for the MessageService.
     *
     * @param messageRepo      Repository for Message objects
     * @param subscriptionRepo Repository for Subscription objects
     * @param usrMgmtSvc       Service for user management operations
     */
    @Autowired
    public MessageService(
            MessageRepository messageRepo,
            SubscriptionRepository subscriptionRepo,
            UserManagementService usrMgmtSvc
    ) {
        messageRepository = messageRepo;
        subscriptionRepository = subscriptionRepo;
        userManagementService = usrMgmtSvc;
    }

    /**
     * Creates a new message for the given user with the provided content.
     *
     * @param jwt     JWT of the user creating the message
     * @param content The content of the message
     * @return Mono of the created message
     */
    public Mono<Message> createMessage(JwtClaimAccessor jwt, String content) {
        if (null == content || content.isEmpty()) {
            logger.warn("User attempted to create a message with empty content");
            return Mono.error(new IllegalArgumentException("Message content cannot be empty."));
        }

        return userManagementService.getUserByJwt(jwt)
                .flatMap(user -> {
                    if (!user.getRoles().contains(User.DB_USER_ROLE_PRODUCER_NAME)) {
                        logger.warn("User {} attempted to create a message without producer role", user.getUsername());
                        return Mono.error(new IllegalArgumentException("User does not have producer privileges."));
                    }
                    Message message = new Message();
                    message.setContent(content);
                    message.setProducerId(user.getId());
                    message.setTimestamp(Instant.now());
                    logger.info("Creating message for user: {}", user.getUsername());
                    return messageRepository.save(message);
                })
                .doOnSuccess(message ->
                        logger.info("Message created successfully with id: {}", message.getMessageId()));
    }

    /**
     * Deletes a message by its ID for the authenticated user.
     *
     * @param id  ID of the message to delete
     * @param jwt JWT of the user attempting to delete the message
     * @return Mono indicating the result of the delete operation
     */
    public Mono<Void> deleteMessage(String id, JwtClaimAccessor jwt) {
        logger.info("Attempting to delete message with id: {}", id);

        return userManagementService.getUserByJwt(jwt)
                .flatMap(user -> messageRepository.findById(id)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Message not found.")))
                        .flatMap(message -> {
                            // check if the user is the producer of the message OR is an admin
                            if (!message.getProducerId().equals(user.getId()) &&
                                    !user.getRoles().contains(User.DB_USER_ROLE_ADMIN_NAME)) {
                                return Mono.error(
                                        new IllegalArgumentException("User is not authorized to delete this message."));
                            }

                            logger.info("User {} deleted message with id: {}", user.getUsername(), id);
                            return messageRepository.deleteById(id);
                        })
                );
    }

    /**
     * Method for getting a message by its ID.
     *
     * @param id ID of the message
     * @return Mono of the message if found
     */
    public Mono<Message> getMessageById(String id) {
        logger.info("Fetching message with id: {}", id);
        return messageRepository.findById(id)
                .doOnSuccess(message -> logger.info("Retrieved message with id: {}", id))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Message not found.")));
    }

    /**
     * Retrieves messages for a given producer.
     *
     * @param producerUsername The username of the producer
     * @return Mono containing a Flux of messages or an empty Flux if no messages are found
     */
    public Mono<Flux<Message>> getMessagesForProducer(String producerUsername) {
        logger.info("Fetching messages for producer: {}", producerUsername);
        return userManagementService.getUserByUsername(producerUsername)
                .map(producer -> messageRepository.findByProducerId(producer.getId()))
                .doOnSuccess(messages -> logger.info("Retrieved messages for producer: {}", producerUsername));
    }

    /**
     * Finds all messages for a given subscriber.
     *
     * @param jwt JWT of the subscriber
     * @return Mono containing a Flux of Message objects for the subscriber
     */
    public Mono<Flux<Message>> findAllMessagesForSubscriber(JwtClaimAccessor jwt) {
        return userManagementService.getUserByJwt(jwt)
                .flatMap(subscriber -> {
                    if (!subscriber.getRoles().contains(User.DB_USER_ROLE_SUBSCRIBER_NAME)) {
                        return Mono.error(new IllegalArgumentException("User is not a subscriber."));
                    }
                    logger.info("Fetching messages for subscriber: {}", subscriber.getUsername());
                    return Mono.just(subscriptionRepository.findBySubscriberId(subscriber.getId())
                            .flatMap(subscription -> messageRepository.findByProducerId(subscription.getProducerId())));
                })
                .doOnSuccess(messages -> logger.info("Retrieved messages for subscriber"));
    }

    /**
     * Method for getting all messages.
     *
     * @return Flux of all messages
     */
    public Flux<Message> getAllMessages() {
        logger.info("Fetching all messages");
        return messageRepository.findAll()
                .doOnComplete(() -> logger.info("Finished fetching all messages"));
    }
}
