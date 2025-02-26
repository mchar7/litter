package org.ac.cst8277.chard.matt.litter.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.constraints.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.dto.CreateMessageRequest;
import org.ac.cst8277.chard.matt.litter.model.Message;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.security.LogSanitizer;
import org.ac.cst8277.chard.matt.litter.service.MessageService;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.method.configuration.EnableReactiveMethodSecurity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Endpoint for handling message-related requests.
 */
@Slf4j
@RestController
@Tag(name = "Message API")
@RequestMapping("/messages")
@EnableReactiveMethodSecurity
public class MessageController {
    private final MessageService messageService;

    /**
     * MessageController constructor.
     *
     * @param messageService The MessageService for facilitating message operations.
     */
    public MessageController(MessageService messageService) {
        this.messageService = messageService;
    }

    /**
     * Creates a new message with the given content for the authenticated user.
     *
     * <p>This endpoint returns a reactive Mono that emits the created message and responds with a 201 Created HTTP status.
     *
     * @param jwt     A JWT representing the authenticated user.
     * @param request The request containing the message content.
     * @return A Mono emitting the created message with HTTP 201 Created status.
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Message> createMessage(@AuthenticationPrincipal Jwt jwt, @RequestBody CreateMessageRequest request) {
        return messageService.createMessage(jwt, request.getContent())
                .doOnSuccess(message -> log.info("User '{}' successfully created message with ID: {}",
                        LogSanitizer.sanitize(jwt.getSubject()), message.getMessageId()))
                .doOnError(e -> log.error("User '{}' failed to create message. Error: {}",
                        LogSanitizer.sanitize(jwt.getSubject()), e.getMessage(), e));
    }

    /**
     * Deletes a message by its ID.
     *
     * <p>This endpoint returns a reactive Mono that signals completion
     * and responds with a 204 No Content HTTP status upon successful deletion.
     *
     * @param id  The ID of the message to delete.
     * @param jwt A JWT representing the authenticated user.
     * @return An empty Mono signaling completion with HTTP 204 No Content status.
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteMessage(
            @AuthenticationPrincipal Jwt jwt, @PathVariable @Pattern(regexp = Message.ID_REGEX) String id) {
        return messageService.deleteMessage(id, jwt)
                .doFirst(() -> log.info("Attempting to delete message with ID: {}", id))
                .doOnSuccess(v -> log.info("Successfully deleted message with ID: {}", id))
                .doOnError(e -> log.error("Failed to delete message with ID: {}. Error: {}",
                        id, e.getMessage(), e));
    }

    /**
     * Retrieves a message by its ID.
     *
     * <p>This endpoint returns a reactive Mono that emits the message with the specified ID
     * and responds with a 200 OK HTTP status. If the message is not found, an empty Mono is returned
     * which the framework will translate to a 404 Not Found response.
     *
     * @param id The ID of the message to retrieve.
     * @return A Mono emitting the message with the specified ID with HTTP 200 OK status.
     */
    @GetMapping("/{id}")
    public Mono<Message> getMessage(@PathVariable @Pattern(regexp = Message.ID_REGEX) String id) {
        return messageService.getMessageById(id)
                .doFirst(() -> log.info("Retrieving message with ID: {}", id))
                .doOnSuccess(message -> log.info("Retrieved message with ID: {}", id))
                .doOnError(e -> log.error("Failed to retrieve message with ID: {}. Error: {}",
                        id, e.getMessage(), e));
    }

    /**
     * Retrieves messages for a specific producer.
     *
     * <p>This endpoint returns a reactive Flux that emits messages posted by the specified producer
     * and responds with a 200 OK HTTP status. If no messages are found, an empty Flux is returned
     * which will result in an empty array in the response.
     *
     * @param producerUsername The username of the producer whose messages to retrieve.
     * @return A Flux emitting the messages for the specified producer with HTTP 200 OK status.
     */
    @GetMapping("/producer/{producerUsername}")
    public Flux<Message> getMessagesForProducer(
            @PathVariable @Pattern(regexp = User.USERNAME_REGEX_STR) String producerUsername) {
        return messageService.getMessagesForProducer(producerUsername)
                .doFirst(() -> log.info("Retrieving messages for producer {}", LogSanitizer.sanitize(producerUsername)))
                .doOnComplete(() -> log.info("Retrieved messages for producer {}", LogSanitizer.sanitize(producerUsername)))
                .doOnError(e -> log.error("Failed to retrieve messages for producer {}. Error: {}",
                        LogSanitizer.sanitize(producerUsername), e.getMessage(), e));
    }

    /**
     * Retrieves all messages for the authenticated subscriber based on their subscriptions.
     *
     * <p>This endpoint returns a reactive Flux that emits messages from producers the subscriber
     * is following and responds with a 200 OK HTTP status. If no messages are found, an empty Flux
     * is returned which will result in an empty array in the response.
     *
     * @param jwt A JWT representing the authenticated user.
     * @return A Flux emitting the messages for the subscriber with HTTP 200 OK status.
     */
    @GetMapping("/subscribed")
    public Flux<Message> getAllMessagesForSubscriber(@AuthenticationPrincipal Jwt jwt) {
        return messageService.findAllMessagesForSubscriber(jwt)
                .doFirst(() -> log.info("Retrieving subscribed messages for user {}", LogSanitizer.sanitize(jwt.getSubject())))
                .doOnComplete(() -> log.info("Retrieved subscribed messages for user {}", LogSanitizer.sanitize(jwt.getSubject())))
                .doOnError(e -> log.error("Failed to retrieve subscribed messages for user {}. Error: {}",
                        LogSanitizer.sanitize(jwt.getSubject()), e.getMessage(), e));
    }

    /**
     * Retrieves all messages in the system.
     *
     * <p>This endpoint returns a reactive Flux that emits all messages stored in the system
     * and responds with a 200 OK HTTP status. If no messages are found, an empty Flux is returned
     * which will result in an empty array in the response.
     *
     * @return A Flux emitting all available messages with HTTP 200 OK status.
     */
    @GetMapping("/all")
    public Flux<Message> getAllMessages() {
        return messageService.getAllMessages()
                .doFirst(() -> log.info("Retrieving all messages"))
                .doOnComplete(() -> log.info("Finished retrieving all messages"))
                .doOnError(e -> log.error("Failed to retrieve all messages. Error: {}", e.getMessage(), e));
    }
}
