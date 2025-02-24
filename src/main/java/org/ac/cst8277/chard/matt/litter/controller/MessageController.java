package org.ac.cst8277.chard.matt.litter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.model.Message;
import org.ac.cst8277.chard.matt.litter.security.LogSanitizer;
import org.ac.cst8277.chard.matt.litter.service.MessageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controller for handling message-related requests.
 */
@Slf4j
@RestController
@RequestMapping("/messages")
@Tag(name = "Message API", description = "Endpoints for managing messages")
public class MessageController {

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
     * @param jwt      JWT representing the authenticated user (provided in the Authorization header)
     * @param postData A JSON object containing the message content; the field "content" is required.
     * @return Mono of the created message
     */
    @Operation(
            summary = "Create a new message",
            description = "Creates a new message for the authenticated producer with a given text content. Assigns current timestamp automatically.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "Message created successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Message.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid message content"),
                    @ApiResponse(responseCode = "403", description = "User is not authorized to create message")
            }
    )
    @PostMapping("")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<Message> createMessage(
            @Parameter(description = "JWT token representing the authenticated user")
            @AuthenticationPrincipal Jwt jwt,
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "A JSON object containing the message content. Example: {\"content\": \"Hello, world!\"}",
                    required = true,
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "object", example = "{\"content\": \"Hello, world!\"}"))
            )
            @RequestBody Map<String, String> postData) {
        return messageService.createMessage(jwt, postData.get("content"))
                .doFirst(() -> log.info("Creating message for user {}", LogSanitizer.sanitize(jwt.getSubject())))
                .doOnSuccess(message -> log.info("Message {} created by user {}",
                        LogSanitizer.sanitize(message.getMessageId().toString()), LogSanitizer.sanitize(jwt.getSubject())))
                .doOnError(e -> log.error("Failed to create message for user {}: {}",
                        LogSanitizer.sanitize(jwt.getSubject()), LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Deletes a message by its ID.
     *
     * @param id  ID of the message to delete
     * @param jwt JWT representing the authenticated user (provided in the Authorization header)
     * @return Mono of completion
     */
    @Operation(
            summary = "Delete a message",
            description = "Deletes the message identified by its ID. Only the message producer or an administrator can perform this action.",
            responses = {
                    @ApiResponse(responseCode = "204", description = "Message deleted successfully"),
                    @ApiResponse(responseCode = "400", description = "Invalid message ID"),
                    @ApiResponse(responseCode = "403", description = "User not authorized to delete the message")
            }
    )
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public Mono<Void> deleteMessage(
            @Parameter(description = "ID of the message to delete", required = true, example = "606c76a7e3a4e17abc321654")
            @PathVariable String id,
            @Parameter(description = "JWT token representing the authenticated user")
            @AuthenticationPrincipal Jwt jwt) {
        return messageService.deleteMessage(id, jwt)
                .doFirst(() -> log.info("User {} attempting to delete message {}",
                        LogSanitizer.sanitize(jwt.getSubject()), LogSanitizer.sanitize(id)))
                .doOnSuccess(v -> log.info("Message {} deleted by user {}",
                        LogSanitizer.sanitize(id), LogSanitizer.sanitize(jwt.getSubject())))
                .doOnError(e -> log.error("Failed to delete message {}: {}",
                        LogSanitizer.sanitize(id), LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Retrieves a message by its ID.
     *
     * @param id ID of the message to retrieve
     * @return Mono of the message
     */
    @Operation(
            summary = "Get a message by ID",
            description = "Retrieves a message by its unique ID.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Message retrieved successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Message.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid message ID"),
                    @ApiResponse(responseCode = "404", description = "Message not found")
            }
    )
    @GetMapping("/{id}")
    public Mono<Message> getMessage(
            @Parameter(description = "ID of the message to retrieve", required = true, example = "606c76a7e3a4e17abc321654")
            @PathVariable String id) {
        return messageService.getMessageById(id)
                .doFirst(() -> log.info("Retrieving message {}", LogSanitizer.sanitize(id)))
                .doOnSuccess(message -> log.info("Retrieved message {}", LogSanitizer.sanitize(message.getMessageId().toString())))
                .doOnError(e -> log.error("Failed to retrieve message {}: {}",
                        LogSanitizer.sanitize(id), LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Retrieves messages for a specific producer.
     *
     * @param producerUsername The username of the producer
     * @return Flux of messages for the producer
     */
    @Operation(
            summary = "Get messages for a producer",
            description = "Retrieves all messages posted by the specified producer. Provide the producer's username (e.g., \"producer1\").",
            responses = {
                    @ApiResponse(
                            responseCode = "200", description = "Messages retrieved for producer",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(type = "array", implementation = Message.class)
                            )
                    ),
                    @ApiResponse(responseCode = "400", description = "Invalid producer username"),
                    @ApiResponse(responseCode = "404", description = "Producer not found or no messages")
            }
    )
    @GetMapping("/producer/{producerUsername}")
    public Flux<Message> getMessagesForProducer(
            @Parameter(description = "Username of the producer", required = true, example = "producer1")
            @PathVariable String producerUsername) {
        return messageService.getMessagesForProducer(producerUsername)
                .doFirst(() -> log.info("Retrieving messages for producer {}", LogSanitizer.sanitize(producerUsername)))
                .doOnComplete(() -> log.info("Retrieved messages for producer {}", LogSanitizer.sanitize(producerUsername)))
                .doOnError(e -> log.error("Failed to retrieve messages for producer {}: {}",
                        LogSanitizer.sanitize(producerUsername), LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Retrieves all messages for the authenticated subscriber.
     *
     * @param jwt JWT representing the authenticated user (provided in the Authorization header)
     * @return Flux of messages for the subscriber
     */
    @Operation(
            summary = "Get messages for subscriber",
            description = "Retrieves all messages for the authenticated subscriber based on their subscriptions.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "Messages retrieved for subscriber",
                            content = @Content(
                                    mediaType = "application/json",
                                    schema = @Schema(type = "array", implementation = Message.class))),
                    @ApiResponse(responseCode = "204", description = "No messages found")
            }, security = @SecurityRequirement(name = "bearerAuth")
    )
    @GetMapping("/subscribed")
    public Flux<Message> getAllMessagesForSubscriber(
            @Parameter(description = "JWT token representing the authenticated user")
            @AuthenticationPrincipal Jwt jwt) {
        return messageService.findAllMessagesForSubscriber(jwt)
                .doFirst(() -> log.info("Retrieving subscribed messages for user {}", LogSanitizer.sanitize(jwt.getSubject())))
                .doOnComplete(() -> log.info("Retrieved subscribed messages for user {}", LogSanitizer.sanitize(jwt.getSubject())))
                .doOnError(e -> log.error("Failed to retrieve subscribed messages for user {}: {}",
                        LogSanitizer.sanitize(jwt.getSubject()), LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Retrieves all messages in the system.
     *
     * @return Flux of all messages
     */
    @Operation(summary = "Get all messages", description = "Retrieves all messages in the system.",
            responses = @ApiResponse(responseCode = "200", description = "All messages retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(type = "array", implementation = Message.class))))
    @GetMapping("/all")
    public Flux<Message> getAllMessages() {
        return messageService.getAllMessages()
                .doFirst(() -> log.info("Retrieving all messages"))
                .doOnComplete(() -> log.info("Finished retrieving all messages"))
                .doOnError(e -> log.error("Failed to retrieve all messages: {}",
                        LogSanitizer.sanitize(e.getMessage())));
    }
}
