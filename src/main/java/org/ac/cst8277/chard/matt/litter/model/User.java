package org.ac.cst8277.chard.matt.litter.model;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.Hidden;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;
import org.springframework.data.annotation.Id;
import org.springframework.data.annotation.ReadOnlyProperty;
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
     * Regular expression for a valid user ID.
     * <p>BSON ObjectIds, when converted to strings, are a 24-character hexadecimal.
     */
    public static final String ID_REGEX = "^[a-f0-9]{24}$";

    /**
     * Regular expression string for a valid username.
     * <p>Valid characters: a-z, A-Z, 0-9, underscore, hyphen.
     * <p>Length: 4-32 characters.
     */
    public static final String USERNAME_REGEX_STR = "^[a-zA-Z0-9_-]{4,32}$";

    /**
     * Regular expression for a secure password.
     */
    public static final String PASSWORD_REGEX_STR = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^a-zA-Z\\d]).{8,}$";

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
     * Example roles for a user.
     * <p>Valid roles:
     * <ul>
     *     <li>{@code ROLE_ADMIN}</li>
     *     <li>{@code ROLE_SUBSCRIBER}</li>
     *     <li>{@code ROLE_PRODUCER}</li>
     * </ul>
     */
    public static final String EXAMPLE_ROLES = "[\"ROLE_ADMIN\", \"ROLE_SUBSCRIBER\"]";

    /**
     * Example username for a user.
     */
    public static final String EXAMPLE_USERNAME = "john_doe";

    /**
     * Unique ID of the user.
     */
    @Id // primary key
    @ReadOnlyProperty
    @Schema(type = "string")
    @Pattern(regexp = ID_REGEX)
    @JsonSerialize(using = ToStringSerializer.class)
    private ObjectId id;

    /**
     * The username of the user.
     */
    @Pattern(regexp = USERNAME_REGEX_STR)
    @Schema(example = EXAMPLE_USERNAME, accessMode = Schema.AccessMode.READ_ONLY)
    private String username;

    /**
     * Roles assigned to the user.
     */
    @Schema(example = EXAMPLE_ROLES, accessMode = Schema.AccessMode.READ_ONLY)
    private List<String> roles;

    /**
     * Hashed password.
     */
    @Hidden
    private String passwordHash;

    /**
     * Lockout time, if the account is locked.
     */
    @Hidden
    private LocalDateTime lockedUntil;

    /**
     * Count of failed login attempts.
     */
    @Hidden
    private int failedLoginAttempts;
}
