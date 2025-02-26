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

/**
 * Model class subscriptions.
 * Uses Lombok Getter and Setter annotations for auto getter and setter methods.
 * Uses Spring's Document annotation for automagic MongoDB mapping.
 */
@Getter
@Setter
@Document(collection = "subscriptions")
@SuppressWarnings("ClassWithoutLogger")
public class Subscription {
    /**
     * Regular expression for a valid subscription ID.
     * <p>BSON ObjectIds, when converted to strings, are a 24-character hexadecimal.
     */
    private static final String ID_REGEX = "^[a-f0-9]{24}$";

    /**
     * Unique ID for the subscription.
     */
    @Id // primary key
    @ReadOnlyProperty
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(type = "string", accessMode = Schema.AccessMode.READ_ONLY, pattern = ID_REGEX)
    private ObjectId subscriptionId;

    /**
     * Unique ID of the subscriber.
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(type = "string", accessMode = Schema.AccessMode.READ_ONLY, pattern = ID_REGEX)
    private ObjectId subscriberId;

    /**
     * Unique ID of the producer.
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(type = "string", accessMode = Schema.AccessMode.READ_ONLY, pattern = ID_REGEX)
    private ObjectId producerId;
}
