package org.ac.cst8277.chard.matt.litter.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

/**
 * Model class subscriptions.
 * Uses Lombok Getter and Setter annotations for auto getter and setter methods.
 * Uses Spring's Document annotation for automagic MongoDB mapping.
 */
@Setter
@Getter
@Document(collection = "subscriptions")
@SuppressWarnings("ClassWithoutLogger")
public class Subscription {
    /**
     * Unique ID for the subscription.
     */
    @Id
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "Unique ID for the subscription", accessMode = Schema.AccessMode.READ_ONLY)
    private ObjectId subscriptionId;

    /**
     * Unique ID of the subscriber.
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "Unique ID of the subscriber", accessMode = Schema.AccessMode.READ_ONLY)
    private ObjectId subscriberId;

    /**
     * Unique ID of the producer.
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "Unique ID of the producer to whom the user is subscribed", accessMode = Schema.AccessMode.READ_ONLY)
    private ObjectId producerId;
}
