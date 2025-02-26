package org.ac.cst8277.chard.matt.litter.dto;

import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.Setter;
import org.ac.cst8277.chard.matt.litter.model.User;

/**
 * DTO for register requests.
 */
@Getter
@Setter
@SuppressWarnings("ClassWithoutLogger")
public class RegisterRequest {
    /**
     * User's desired username.
     */
    @Pattern(regexp = User.USERNAME_REGEX_STR)
    private String username;

    /**
     * User's desired password.
     */
    @Pattern(regexp = User.PASSWORD_REGEX_STR)
    private String password;
}
