package org.ac.cst8277.chard.matt.litter.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import reactor.core.publisher.Mono;

/**
 * Global exception handler that converts exceptions to a standardized ErrorResponse.
 */
@Slf4j
@ControllerAdvice
public class GlobalExceptionHandler {
    /**
     * Handles BadCredentialsException.
     * <p>This method is called when a BadCredentialsException is thrown.
     *
     * @param err the exception to handle
     * @return Unauthorized status with error message
     */
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    @ExceptionHandler(BadCredentialsException.class)
    public Mono<ErrorResponse> handleBadCredentialsException(BadCredentialsException err) {
        log.trace("Handling BadCredentialsException: {}", err.getMessage());
        return Mono.just(new ErrorResponse(err.getMessage()));
    }

    /**
     * Handles IllegalArgumentException.
     * <p>This method is called when an IllegalArgumentException is thrown.
     *
     * @param err the exception to handle
     * @return Bad Request status with error message
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public Mono<ErrorResponse> handleIllegalArgumentException(IllegalArgumentException err) {
        log.trace("Handling IllegalArgumentException: {}", err.getMessage());
        return Mono.just(new ErrorResponse(err.getMessage()));
    }
}
