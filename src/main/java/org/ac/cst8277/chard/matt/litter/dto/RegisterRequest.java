package org.ac.cst8277.chard.matt.litter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * DTO for regster requests.
 */
@Getter
@Setter
@Slf4j
@Schema(description = "DTO for register requests")
public class RegisterRequest {
    @Schema(
            description = "User's username - at least 3 alphanumeric characters.",
            example = "john_doe",
            pattern = "^[a-zA-Z0-9]{3,}$"
    )
    private String username;

    @Schema(
            description = "User's password - min 8 chars (at least 1 uppercase, 1 lowercase, 1 digit, and 1 special char.",
            example = "P@ssw0rd!",
            pattern = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,}$"
    )
    private String password;
}
