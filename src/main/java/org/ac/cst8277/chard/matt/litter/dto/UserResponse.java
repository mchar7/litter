package org.ac.cst8277.chard.matt.litter.dto;

import com.fasterxml.jackson.databind.annotation.JsonSerialize;
import com.fasterxml.jackson.databind.ser.std.ToStringSerializer;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.bson.types.ObjectId;

import java.util.List;

/**
 * DTO for user information in API responses.
 * <p>Provides a controlled subset of user information to prevent exposing sensitive data.
 */
@Getter
@Setter
@Slf4j
public class UserResponse {
    /**
     * Unique ID of the user.
     */
    @Schema(type = "string", pattern = User.ID_REGEX)
    @JsonSerialize(using = ToStringSerializer.class)
    private ObjectId id;

    /**
     * The username of the user.
     */
    @Schema(example = User.EXAMPLE_USERNAME, pattern = User.USERNAME_REGEX_STR)
    private String username;

    /**
     * Roles assigned to the user.
     */
    @Schema(example = User.EXAMPLE_ROLES)
    private List<String> roles;

    /**
     * Factory method to create UserResponse from User entity.
     *
     * @param user The user entity to convert
     * @return A UserResponse containing only the appropriate fields for API responses
     */
    public static UserResponse fromUser(User user) {
        log.info("Generating UserResponse from User entity");
        UserResponse response = new UserResponse();
        response.setId(user.getId());
        response.setUsername(user.getUsername());
        response.setRoles(user.getRoles());
        return response;
    }
}
