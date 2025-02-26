package org.ac.cst8277.chard.matt.litter.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;

/**
 * Model class for a message.
 */
@Getter
@Setter
@Document(collection = "messages")
@SuppressWarnings("ClassWithoutLogger")
public class Message {
    /**
     * Regular expression for a valid message.
     * <p>All characters are allowed, including whitespace.
     * <p>Length: 1-512 characters.
     */
    public static final String TEXT_REGEX = "^.{1,512}$";

    /**
     * Regular expression for a valid user ID.
     * <p>BSON ObjectIds, when converted to strings, are a 24-character hexadecimal.
     */
    public static final String ID_REGEX = "^[a-f0-9]{24}$";

    /**
     * Example message content.
     */
    public static final String EXAMPLE_TEXT = "Hello, world!";

    /**
     * Unique ID for the message.
     */
    @Id // primary key
    @ReadOnlyProperty
    @Schema(type = "string", pattern = ID_REGEX)
    @JsonSerialize(using = ToStringSerializer.class)
    private ObjectId messageId;

    /**
     * Timestamp when the message was created.
     */
    @ReadOnlyProperty
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant timestamp;

    /**
     * Reference to the producer's unique ID.
     */
    @Schema(type = "string", accessMode = Schema.AccessMode.READ_ONLY, pattern = ID_REGEX)
    @JsonSerialize(using = ToStringSerializer.class)
    private ObjectId producerId;

    /**
     * The text content of the message.
     */
    @Schema(example = EXAMPLE_TEXT, pattern = TEXT_REGEX)
    private String content;
}
