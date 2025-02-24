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
@Getter
@Setter
@Document(collection = "subscriptions")
@SuppressWarnings("ClassWithoutLogger")
@Schema(description = "Subscription details linking a subscriber with a producer.")
public class Subscription {
    /**
     * Unique ID for the subscription.
     */
    @Id
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "Unique ID for the subscription",
            accessMode = Schema.AccessMode.READ_ONLY,
            type = "string",
            example = "65c1e20f4bd9587f9b6bd94d")
    private ObjectId subscriptionId;

    /**
     * Unique ID of the subscriber.
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "Unique ID of the subscriber",
            accessMode = Schema.AccessMode.READ_ONLY,
            type = "string",
            example = "65c1e20f4bd9587f9b6bd94d")
    private ObjectId subscriberId;

    /**
     * Unique ID of the producer.
     */
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "Unique ID of the producer to whom the user is subscribed",
            accessMode = Schema.AccessMode.READ_ONLY,
            type = "string",
            example = "65c1e20f4bd9587f9b6bd94d")
    private ObjectId producerId;
}
