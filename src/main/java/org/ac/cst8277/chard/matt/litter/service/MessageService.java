package org.ac.cst8277.chard.matt.litter.service;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.model.Message;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.repository.MessageRepository;
import org.ac.cst8277.chard.matt.litter.repository.SubscriptionRepository;
import org.ac.cst8277.chard.matt.litter.security.LogSanitizer;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.time.Instant;

import static org.ac.cst8277.chard.matt.litter.model.User.DB_USER_ROLE_SUBSCRIBER_NAME;

/**
 * Service class for Message objects.
 */
@Slf4j
@Service
public class MessageService {
    private static final java.util.regex.Pattern MESSAGE_TEXT_REGEX_COMPILED = java.util.regex.Pattern.compile(Message.TEXT_REGEX);
    private final MessageRepository msgRepo;
    private final SubscriptionRepository subsRepo;
    private final UserManagementService usrMgmtSvc;

    /**
     * Constructor for the MessageService.
     *
     * @param msgRepo    Repository for Message objects
     * @param subsRepo   Repository for Subscription objects
     * @param usrMgmtSvc Service for user management operations
     */
    @Autowired
    public MessageService(MessageRepository msgRepo, SubscriptionRepository subsRepo, UserManagementService usrMgmtSvc) {
        this.msgRepo = msgRepo;
        this.subsRepo = subsRepo;
        this.usrMgmtSvc = usrMgmtSvc;
    }

    /**
     * Checks if the user is authorized to delete the message.
     *
     * @param user    User attempting to delete the message
     * @param message Message to be deleted
     * @return True if the user is authorized to delete the message, false otherwise
     */
    private static boolean isAuthorizedToDelete(User user, Message message) {
        return message.getProducerId().equals(user.getId()) || user.getRoles().contains(User.DB_USER_ROLE_ADMIN_NAME);
    }

    /**
     * Creates a new message for the given user with the provided content.
     *
     * @param jwt JWT of the user creating the message
     * @param txt The content of the message
     * @return Mono of the created message
     */
    public Mono<Message> createMessage(JwtClaimAccessor jwt, @NotBlank @Pattern(regexp = Message.TEXT_REGEX) String txt) {
        if (null == txt || !MESSAGE_TEXT_REGEX_COMPILED.matcher(txt).matches()) {
            return Mono.error(new IllegalArgumentException("Message content is invalid."));
        }
        return usrMgmtSvc.getUserByJwt(jwt)
                .doFirst(() -> log.info("Attempting to create message for user: {}", LogSanitizer.sanitize(jwt.getSubject())))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found.")))
                .filterWhen(user -> Mono.just(user.getRoles().contains(User.DB_USER_ROLE_PRODUCER_NAME)))
                .switchIfEmpty(Mono.error(new AccessDeniedException("User is not a producer")))
                .flatMap(user -> {
                            Message message = new Message();
                            message.setContent(txt);
                            message.setProducerId(user.getId());
                            message.setTimestamp(Instant.now());
                            return msgRepo.save(message);
                        }
                ).doOnSuccess(message -> log.info("Message created successfully. ID: {}", message.getMessageId()));
    }

    /**
     * Deletes a message by its ID for the authenticated user.
     *
     * @param id  ID of the message to delete
     * @param jwt JWT of the user attempting to delete the message
     * @return Mono indicating the result of the delete operation
     */
    public Mono<Void> deleteMessage(@Pattern(regexp = Message.ID_REGEX) String id, JwtClaimAccessor jwt) {
        return usrMgmtSvc.getUserByJwt(jwt)
                .doFirst(() -> log.info("Attempting to delete message with id: {}", id))
                .flatMap(user -> msgRepo.findById(id)
                        .switchIfEmpty(Mono.error(new IllegalArgumentException("Message not found.")))
                        .flatMap(message -> {
                            if (isAuthorizedToDelete(user, message)) {
                                log.info("User '{}' deleted message ID: {}", LogSanitizer.sanitize(user.getUsername()), id);
                                return msgRepo.deleteById(id);
                            } else {
                                return Mono.error(new AccessDeniedException("Not authorized to delete this message"));
                            }
                        }));
    }

    /**
     * Method for getting a message by its ID.
     *
     * @param id ID of the message
     * @return Mono of the message if found
     */
    public Mono<Message> getMessageById(String id) {
        return msgRepo.findById(id)
                .doFirst(() -> log.info("Retrieving message with id: {}", id))
                .switchIfEmpty(Mono.error(new IllegalArgumentException("Message not found.")))
                .doOnSuccess(message -> log.info("Retrieved message with id: {}", id));
    }

    /**
     * Retrieves messages for a given producer.
     *
     * @param producerUsername The username of the producer
     * @return Flux of messages produced by the given producer
     */
    public Flux<Message> getMessagesForProducer(String producerUsername) {
        if (UserManagementService.isUsernameErroneous(producerUsername)) {
            return Flux.error(new IllegalArgumentException("Invalid producer username"));
        }
        return usrMgmtSvc.getUserByUsername(producerUsername)
                .doFirst(() -> log.info("Fetching messages for producer: {}", LogSanitizer.sanitize(producerUsername)))
                .flatMapMany(producer -> msgRepo.findByProducerId(producer.getId()))
                .doOnComplete(() -> log.info("Retrieved messages for producer: {}", LogSanitizer.sanitize(producerUsername)));
    }

    /**
     * Finds all messages for a given subscriber.
     *
     * @param jwt JWT of the subscriber
     * @return Flux of Message objects for the subscriber
     */
    public Flux<Message> findAllMessagesForSubscriber(JwtClaimAccessor jwt) {
        return usrMgmtSvc.getUserByJwt(jwt)
                .doFirst(() -> log.info("Fetching messages for subscriber: {}", LogSanitizer.sanitize(jwt.getSubject())))
                .flatMap(user -> user.getRoles().contains(DB_USER_ROLE_SUBSCRIBER_NAME)
                        ? Mono.just(user) : Mono.error(new AccessDeniedException("User is not a subscriber")))
                .flatMapMany(subscriber -> subsRepo.getSubscriptionsBySubscriberId(subscriber.getId())
                        .flatMap(sub -> msgRepo.findByProducerId(sub.getProducerId())));
    }


    /**
     * Method for getting all messages.
     *
     * @return Flux of all messages
     */
    public Flux<Message> getAllMessages() {
        return msgRepo.findAll()
                .doFirst(() -> log.info("Fetching all messages"))
                .doOnComplete(() -> log.info("Finished fetching all messages"));
    }
}
