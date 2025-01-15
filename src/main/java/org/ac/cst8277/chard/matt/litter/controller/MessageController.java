package org.ac.cst8277.chard.matt.litter.controller;

import org.ac.cst8277.chard.matt.litter.model.Message;
import org.ac.cst8277.chard.matt.litter.service.MessageService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.net.URI;
import java.util.Map;

/**
 * Controller for handling message-related requests.
 */
@RestController
@RequestMapping("/messages")
public class MessageController {

    private static final Logger logger = LoggerFactory.getLogger(MessageController.class);
    private final MessageService messageService;

    /**
     * Constructor for MessageController class.
     *
     * @param messageService Service that handles message-related operations
     */
    @Autowired
    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Creates a new message with the given content for the authenticated user.
     *
     * @param jwt      Jwt used by the authenticated user, resolved by Spring Security
     * @param postData Map containing the content of the message
     * @return Mono of ResponseEntity containing the created message
     */
    @PostMapping({"", "/"})
    public Mono<ResponseEntity<Message>> createMessage(
            @AuthenticationPrincipal Jwt jwt, @RequestBody Map<String, String> postData) {
        String content = postData.get("content");

        if (null == content || content.isEmpty()) {
            logger.warn("Attempt to create message with invalid content");
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return messageService.createMessage(jwt, content)
                .map(message -> {
                    logger.info("Message created successfully by user: {}", jwt.getSubject());
                    String resourceUrl = "/messages/%s".formatted(message.getMessageId());
                    return ResponseEntity.created(URI.create(resourceUrl)).body(message);
                })
                .onErrorResume(e -> {
                    logger.error("Error creating message: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                });
    }

    /**
     * Deletes a message by its ID.
     *
     * @param id  ID of the message to delete
     * @param jwt Jwt used by the authenticated user, resolved by Spring Security
     * @return Mono of HTTP response entity indicating the result of the deletion
     */
    @DeleteMapping({"/{id}", "/{id}/"})
    public Mono<ResponseEntity<Void>> deleteMessage(@PathVariable String id, @AuthenticationPrincipal Jwt jwt) {
        if (null == id || id.isEmpty()) {
            logger.warn("Attempted to delete message with invalid ID");
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return messageService.deleteMessage(id, jwt)
                .then(Mono.just(ResponseEntity.noContent().<Void>build()))
                .onErrorResume(e -> {
                    logger.error("Error deleting message: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.FORBIDDEN).build());
                });
    }

    /**
     * Endpoint for getting a message by its ID.
     *
     * @param id ID of the message
     * @return Mono of HTTP response entity containing the message
     */
    @GetMapping({"/{id}", "/{id}/"})
    public Mono<ResponseEntity<Message>> getMessage(@PathVariable String id) {
        if (null == id || id.isEmpty()) {
            logger.warn("Attempted to get message with invalid ID");
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return messageService.getMessageById(id)
                .map(message -> {
                    logger.info("Message retrieved successfully: {}", id);
                    return ResponseEntity.ok(message);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves messages for a specific producer.
     *
     * @param producerUsername The username of the producer
     * @return Flux of messages or an appropriate error response
     */
    @GetMapping({"/producer/{producerUsername}", "/producer/{producerUsername}/"})
    public Mono<ResponseEntity<Flux<Message>>> getMessagesForProducer(@PathVariable String producerUsername) {
        if (null == producerUsername || producerUsername.isEmpty()) {
            logger.warn("Attempted to get messages for producer with invalid username");
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return messageService.getMessagesForProducer(producerUsername)
                .map(messages -> {
                    logger.info("Retrieved messages for producer: {}", producerUsername);
                    return ResponseEntity.ok(messages);
                })
                .defaultIfEmpty(ResponseEntity.notFound().build());
    }

    /**
     * Retrieves all messages for the authenticated subscriber.
     *
     * @param jwt Jwt used by the authenticated user, resolved by Spring Security
     * @return Flux of messages for the subscriber
     */
    @GetMapping({"/subscribed", "/subscribed/"})
    public Mono<ResponseEntity<Flux<Message>>> getAllMessagesForSubscriber(@AuthenticationPrincipal Jwt jwt) {
        return messageService.findAllMessagesForSubscriber(jwt)
                .map(messages -> {
                    logger.info("Retrieved subscribed messages for user: {}", jwt.getSubject());
                    return ResponseEntity.ok().body(messages);
                })
                .defaultIfEmpty(ResponseEntity.noContent().build());
    }

    /**
     * Endpoint for getting all messages, regardless of subscription status.
     *
     * @return Flux of all messages
     */
    @GetMapping({"/all", "/all/"})
    public Flux<Message> getAllMessages() {
        logger.info("Retrieving all messages");
        return messageService.getAllMessages();
    }
}