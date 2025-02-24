package org.ac.cst8277.chard.matt.litter.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "User details including ID, username, and roles. Sensitive fields are hidden.")
public class User {
    /**
     * Role name for administrators.
     */
    public static final String DB_USER_ROLE_ADMIN_NAME = "ROLE_ADMIN";
    /**
     * Role name for subscribers.
     */
    public static final String DB_USER_ROLE_SUBSCRIBER_NAME = "ROLE_SUBSCRIBER";
    /**
     * Role name for producers.
     */
    public static final String DB_USER_ROLE_PRODUCER_NAME = "ROLE_PRODUCER";

    /**
     * Default capacity for roles hashmap.
     */
    @SuppressWarnings("StaticMethodOnlyUsedInOneClass") // will be more relevant once role management is implemented
    public static final Integer ROLES_HASHMAP_DEFAULT_CAP = 4;

    /**
     * Unique ID of the user.
     */
    @Id // marks this field as the primary key
    @JsonSerialize(using = ToStringSerializer.class)
    @Schema(description = "Unique ID of the user",
            accessMode = Schema.AccessMode.READ_ONLY,
            type = "string",
            example = "65c1e20f4bd9587f9b6bd94d")
    private ObjectId id;

    /**
     * The username of the user.
     */
    @Schema(description = "Username of the user", example = "john_doe")
    private String username;

    /**
     * Roles assigned to the user.
     */
    @Schema(description = "Roles assigned to the user", example = "[\"ROLE_SUBSCRIBER\"]")
    private List<String> roles;

    /**
     * Hashed password.
     */
    @Schema(hidden = true)
    private String passwordHash;

    /**
     * Lockout time, if the account is locked.
     */
    @Schema(hidden = true, description = "The time until which the account is locked, if applicable")
    private LocalDateTime lockedUntil;

    /**
     * Count of failed login attempts.
     */
    @Schema(hidden = true, description = "Number of failed login attempts")
    private int failedLoginAttempts;
}
