package org.ac.cst8277.chard.matt.litter.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
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
    @Id                                                 // mark messageId as primary key
    @JsonSerialize(using = ToStringSerializer.class)    // serialize ObjectId as a string in JSON
    private ObjectId subscriptionId;                    // ID of the subscription
    @JsonSerialize(using = ToStringSerializer.class)    // serialize ObjectId as a string in JSON
    private ObjectId subscriberId;                      // ID of the user who is subscribed to the producer
    @JsonSerialize(using = ToStringSerializer.class)    // serialize ObjectId as a string in JSON
    private ObjectId producerId;                        // ID of the user to whom the subscriber is subscribed
}