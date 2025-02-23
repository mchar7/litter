package org.ac.cst8277.chard.matt.litter.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Model class for a message.
 */
@Getter
@Setter
@Document(collection = "messages")
@SuppressWarnings("ClassWithoutLogger")
@Schema(description = "Details of a message published by a producer.")
public class Message {

    /**
     * Unique ID for the message.
     */
    @Id
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "Unique ID for the message", accessMode = Schema.AccessMode.READ_ONLY)
    private ObjectId messageId;

    /**
     * Timestamp when the message was created.
     */
    @Schema(description = "Timestamp when the message was created", accessMode = Schema.AccessMode.READ_ONLY, example = "2025-02-23T15:30:00Z")
    private Instant timestamp;

    /**
     * Reference to the producer's unique ID.
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "Unique ID for the producer", accessMode = Schema.AccessMode.READ_ONLY)
    private ObjectId producerId;

    /**
     * The textual content of the message.
     */
    @Schema(description = "Text content of the message", example = "Hello, world!")
    private String content;
}
