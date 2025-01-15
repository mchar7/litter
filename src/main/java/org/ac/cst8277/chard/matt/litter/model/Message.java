package org.ac.cst8277.chard.matt.litter.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.Instant;

/**
 * Model class for a message.
 * Uses Lombok Getter and Setter annotations for auto getter and setter methods.
 * Uses Spring's Document annotation for automagic MongoDB mapping.
 */
@Setter
@Getter
@Document(collection = "messages")
@SuppressWarnings("ClassWithoutLogger")
public class Message {
    @Id                                                 // mark messageId as primary key
    @JsonSerialize(using = ToStringSerializer.class)    // serialize ObjectId as a string in JSON
    private ObjectId messageId;                         // ID of the message
    private Instant timestamp;                          // timestamp of the message
    @JsonSerialize(using = ToStringSerializer.class)    // serialize ObjectId as a string in JSON
    private ObjectId producerId;                        // ID of the message producer's User object
    private String content;                             // message body
}