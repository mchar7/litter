package org.ac.cst8277.chard.matt.litter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.dto.LoginRequest;
import org.ac.cst8277.chard.matt.litter.dto.RegisterRequest;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.security.LogSanitizer;
import org.ac.cst8277.chard.matt.litter.service.UserManagementService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controller for handling user management-related requests.
 */
@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "User Management", description = "Operations for user registration, login, and retrieval")
public class UserManagementController {
    private final UserManagementService userManagementService;

    /**
     * Constructor for UserManagementController class.
     *
     * @param usrMgmtSvc Service for handling user management-related operations
     */
    @Autowired
    public UserManagementController(UserManagementService usrMgmtSvc) {
        userManagementService = usrMgmtSvc;
    }


    /**
     * Registers a new user.
     *
     * @param registerRequest A JSON object containing "username" and "password"
     * @return Mono of the registered user
     */
    @Operation(
            summary = "Register a new user",
            description = "Registers a new user with the provided username and password. " +
                    "Username must be at least 3 alphanumeric characters. " +
                    "Password must be at least 8 characters long with at least: " +
                    "1 uppercase letter, 1 lowercase letter, 1 digit, and 1 special character.",
            responses = {
                    @ApiResponse(responseCode = "201", description = "User created successfully",
                            content = @Content(mediaType = "application/json", schema = @Schema(implementation = User.class))),
                    @ApiResponse(responseCode = "400", description = "Invalid input or user already exists")
            }
    )
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> register(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "JSON payload containing 'username' and 'password'. Example: {\"username\": \"john_doe\", \"password\": \"P@ssw0rd!\"}",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = RegisterRequest.class)
                    )
            )
            @RequestBody RegisterRequest registerRequest) {
        String username = registerRequest.getUsername();
        String password = registerRequest.getPassword();

        return userManagementService.register(username, password)
                .doFirst(() -> log.info("Processing registration for username {}", LogSanitizer.sanitize(username)))
                .doOnSuccess(user -> log.info("User registered successfully: {}", LogSanitizer.sanitize(user.getUsername())))
                .doOnError(e -> log.error("Failed to register user {}: {}",
                        LogSanitizer.sanitize(username), LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Logs in a user.
     *
     * @param loginRequest A JSON object containing "username" and "password"
     * @return Mono containing a JWT token if login is successful
     */
    @Operation(
            summary = "User login",
            description = "Logs in a user and returns a JWT token. The JSON payload must include 'username' and 'password'.",
            responses = {
                    @ApiResponse(responseCode = "200", description = "User logged in successfully, JWT token returned",
                            content = @Content(mediaType = "text/plain",
                                    schema = @Schema(type = "string", example = "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."))),
                    @ApiResponse(responseCode = "400", description = "Invalid input"),
                    @ApiResponse(responseCode = "401", description = "Unauthorized, invalid credentials")
            }
    )
    @PostMapping("/login")
    public Mono<String> login(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(
                    description = "JSON payload containing 'username' and 'password'. Example: {\"username\": \"john_doe\", \"password\": \"P@ssw0rd!\"}",
                    required = true,
                    content = @Content(
                            mediaType = "application/json",
                            schema = @Schema(implementation = LoginRequest.class)
                    )
            )
            @RequestBody LoginRequest loginRequest) {
        String username = loginRequest.getUsername();
        String password = loginRequest.getPassword();

        return userManagementService.login(username, password)
                .doFirst(() -> log.info("Processing login for user {}", LogSanitizer.sanitize(username)))
                .doOnSuccess(token -> log.info("User logged in successfully: {}", LogSanitizer.sanitize(username)))
                .doOnError(e -> log.error("Login failed for user {}: {}",
                        LogSanitizer.sanitize(username), LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Retrieves all registered users.
     *
     * @return Flux of maps containing user details
     */
    @Operation(summary = "Get all users",
            description = "Retrieves a list of all registered users with basic details (id, username, and roles).",
            responses = @ApiResponse(responseCode = "200", description = "List of users retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class))))
    @GetMapping("/all")
    public Flux<Map<String, Object>> getAllUsers() {
        return userManagementService.getAllUsers()
                .doFirst(() -> log.info("Retrieving all users"))
                .doOnComplete(() -> log.info("Finished retrieving all users"))
                .doOnError(e -> log.error("Failed to retrieve users: {}", LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Retrieves all users with the producer role.
     *
     * @return Flux of user objects representing producers
     */
    @Operation(summary = "Get all producers", description = "Retrieves a list of all users with the producer role.",
            responses = @ApiResponse(responseCode = "200", description = "List of producers retrieved successfully",
                    content = @Content(mediaType = "application/json", schema = @Schema(implementation = User.class))))
    @GetMapping("/producers")
    public Flux<User> getAllProducers() {
        return userManagementService.getAllUsersByRole(User.DB_USER_ROLE_PRODUCER_NAME)
                .doFirst(() -> log.info("Retrieving all producers"))
                .doOnComplete(() -> log.info("Finished retrieving all producers"))
                .doOnError(e -> log.error("Failed to retrieve producers: {}", LogSanitizer.sanitize(e.getMessage())));
    }
}
