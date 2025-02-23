package org.ac.cst8277.chard.matt.litter.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * DTO for login requests.
 */
@Setter
@Getter
@Slf4j
@Schema(description = "DTO for login requests")
public class LoginRequest {
    @Schema(description = "User's username", example = "john_doe")
    private String username;

    @Schema(description = "User's password", example = "P@ssw0rd!")
    private String password;
}
