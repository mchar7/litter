package org.ac.cst8277.chard.matt.litter.service;

import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.model.Message;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.repository.MessageRepository;
import org.ac.cst8277.chard.matt.litter.repository.SubscriptionRepository;
import org.ac.cst8277.chard.matt.litter.security.LogSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

/**
 * Service class for Message objects.
 */
@Slf4j
@Service
public class MessageService {
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
            log.warn("User attempted to create a message with empty content");
            return Mono.error(new IllegalArgumentException("Message content cannot be empty."));
        }

        return userManagementService.getUserByJwt(jwt)
                .flatMap(user -> {
                    if (!user.getRoles().contains(User.DB_USER_ROLE_PRODUCER_NAME)) {
                        log.warn("User {} attempted to create a message without producer role",
                                LogSanitizer.sanitize(user.getUsername()));
                        return Mono.error(new IllegalArgumentException("User does not have producer privileges."));
                    }
                    Message message = new Message();
                    message.setContent(content);
                    message.setProducerId(user.getId());
                    message.setTimestamp(Instant.now());
                    log.info("Creating message for user: {}", LogSanitizer.sanitize(user.getUsername()));
                    return messageRepository.save(message);
                })
                .doOnSuccess(message ->
                        log.info("Message created successfully with id: {}", message.getMessageId()));
    }

    /**
     * Deletes a message by its ID for the authenticated user.
     *
     * @param id  ID of the message to delete
     * @param jwt JWT of the user attempting to delete the message
     * @return Mono indicating the result of the delete operation
     */
    public Mono<Void> deleteMessage(String id, JwtClaimAccessor jwt) {
        log.info("Attempting to delete message with id: {}", LogSanitizer.sanitize(id));
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

                            log.info("User {} deleted message with id: {}",
                                    LogSanitizer.sanitize(user.getUsername()), LogSanitizer.sanitize(id));
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
        log.info("Fetching message with id: {}", LogSanitizer.sanitize(id));
        return messageRepository.findById(id)
                .doOnSuccess(message ->
                        log.info("Retrieved message with id: {}", LogSanitizer.sanitize(id)))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Message not found.")));
    }

    /**
     * Retrieves messages for a given producer.
     *
     * @param producerUsername The username of the producer
     * @return Flux of messages produced by the given producer
     */
    public Flux<Message> getMessagesForProducer(String producerUsername) {
        log.info("Fetching messages for producer: {}", LogSanitizer.sanitize(producerUsername));
        return userManagementService.getUserByUsername(producerUsername)
                .flatMapMany(producer -> messageRepository.findByProducerId(producer.getId()))
                .doOnComplete(() -> log.info("Retrieved messages for producer: {}",
                        LogSanitizer.sanitize(producerUsername)));
    }

    /**
     * Finds all messages for a given subscriber.
     *
     * @param jwt JWT of the subscriber
     * @return Flux of Message objects for the subscriber
     */
    public Flux<Message> findAllMessagesForSubscriber(JwtClaimAccessor jwt) {
        return userManagementService.getUserByJwt(jwt)
                .flatMapMany(subscriber -> {
                    if (!subscriber.getRoles().contains(User.DB_USER_ROLE_SUBSCRIBER_NAME)) {
                        return Flux.error(new IllegalArgumentException("User is not a subscriber."));
                    }
                    log.info("Fetching messages for subscriber: {}", LogSanitizer.sanitize(subscriber.getUsername()));
                    return subscriptionRepository.findBySubscriberId(subscriber.getId())
                            .flatMap(subscription -> messageRepository.findByProducerId(subscription.getProducerId()));
                })
                .doOnComplete(() -> log.info("Retrieved messages for subscriber"));
    }


    /**
     * Method for getting all messages.
     *
     * @return Flux of all messages
     */
    public Flux<Message> getAllMessages() {
        log.info("Fetching all messages");
        return messageRepository.findAll()
                .doOnComplete(() -> log.info("Finished fetching all messages"));
    }
}
