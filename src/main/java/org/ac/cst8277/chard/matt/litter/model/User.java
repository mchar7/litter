package org.ac.cst8277.chard.matt.litter.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Document;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Model class for a user.
 */
@Getter
@Setter
@Document(collection = "users")
@SuppressWarnings("ClassWithoutLogger")
public class User {

    /**
     * Role name for administrators.
     */
    public static final String DB_USER_ROLE_ADMIN_NAME = "ROLE_ADMIN";

    /**
     * Role name for administrators.
     */
    public static final Integer ROLES_HASHMAP_DEFAULT_CAP = 4;

    /**
     * Role name for subscribers.
     */
    public static final String DB_USER_ROLE_SUBSCRIBER_NAME = "ROLE_SUBSCRIBER";

    /**
     * Role name for producers.
     */
    public static final String DB_USER_ROLE_PRODUCER_NAME = "ROLE_PRODUCER";

    @Id
    @JsonSerialize(using = ToStringSerializer.class) // serialize ObjectId as a string in JSON
    private ObjectId id;
    private String username;
    private List<String> roles;

    private String passwordHash;
    private LocalDateTime lockedUntil;
    private int failedLoginAttempts;
}