package org.ac.cst8277.chard.matt.litter.exception;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.Instant;


/**
 * Standard error response to be used for API error responses.
 */
@Getter
@Setter
@SuppressWarnings("ClassWithoutLogger")
@Schema(contentMediaType = "application/json")
public class ErrorResponse {
    /**
     * Timestamp when the error occurred.
     */
    @Schema(accessMode = Schema.AccessMode.READ_ONLY)
    @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
    private Instant timestamp;

    /**
     * Human-readable error message.
     */
    @Schema(accessMode = Schema.AccessMode.READ_ONLY, example = "Specific error shown here.")
    private String message;

    /**
     * Overloaded constructor that sets the error message.
     *
     * @param message the error message.
     */
    ErrorResponse(String message) {
        timestamp = Instant.now();
        this.message = message;
    }
}
