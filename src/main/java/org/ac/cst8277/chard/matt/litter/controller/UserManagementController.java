package org.ac.cst8277.chard.matt.litter.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
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
    private static final String USERNAME_KEY = "username";
    private static final String PASSWORD_KEY = "password";
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
     * Endpoint for registering a new user.
     *
     * @param registerInfo Map containing the username and password for registration
     * @return Mono of the registered user
     */
    @Operation(summary = "Register a new user", description = "Registers a new user with provided username and password")
    @ApiResponse(responseCode = "201", description = "User created successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = User.class)))
    @ApiResponse(responseCode = "400", description = "Invalid input or user already exists", content = @Content)
    @PostMapping({"/register", "/register/"})
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<User> register(@RequestBody Map<String, String> registerInfo) {
        String username = registerInfo.get(USERNAME_KEY);
        String password = registerInfo.get(PASSWORD_KEY);

        return userManagementService.register(username, password)
                .doFirst(() -> log.info("Processing registration for username {}", LogSanitizer.sanitize(username)))
                .doOnSuccess(user -> log.info("User registered successfully: {}", LogSanitizer.sanitize(user.getUsername())))
                .doOnError(e -> log.error("Failed to register user {}: {}",
                        LogSanitizer.sanitize(username), LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Endpoint for logging in a user.
     *
     * @param loginInfo Map containing the username and password for login
     * @return String containing the JWT token
     */
    @Operation(summary = "User login", description = "Logs in a user and returns a JWT token")
    @ApiResponse(responseCode = "200", description = "User logged in successfully, JWT token returned",
            content = @Content(mediaType = "text/plain", schema = @Schema(type = "string")))
    @ApiResponse(responseCode = "400", description = "Invalid input", content = @Content)
    @ApiResponse(responseCode = "401", description = "Unauthorized, invalid credentials", content = @Content)
    @PostMapping({"/login", "/login/"})
    public Mono<String> login(@RequestBody Map<String, String> loginInfo) {
        String username = loginInfo.get(USERNAME_KEY);
        String password = loginInfo.get(PASSWORD_KEY);

        return userManagementService.login(username, password)
                .doFirst(() -> log.info("Processing login for user {}", LogSanitizer.sanitize(username)))
                .doOnSuccess(token -> log.info("User logged in successfully: {}", LogSanitizer.sanitize(username)))
                .doOnError(e -> log.error("Login failed for user {}: {}",
                        LogSanitizer.sanitize(username), LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Endpoint for getting all users.
     *
     * @return Flux of maps containing user information
     */
    @Operation(summary = "Get all users", description = "Retrieves a list of all registered users")
    @ApiResponse(responseCode = "200", description = "List of users retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = Map.class)))
    @GetMapping({"/all", "/all/"})
    public Flux<Map<String, Object>> getAllUsers() {
        return userManagementService.getAllUsers()
                .doFirst(() -> log.info("Retrieving all users"))
                .doOnComplete(() -> log.info("Finished retrieving all users"))
                .doOnError(e -> log.error("Failed to retrieve users: {}", LogSanitizer.sanitize(e.getMessage())));
    }

    /**
     * Endpoint for getting all producers.
     *
     * @return Flux of User objects representing producers
     */
    @Operation(summary = "Get all producers", description = "Retrieves a list of all users with the producer role")
    @ApiResponse(responseCode = "200", description = "List of producers retrieved successfully",
            content = @Content(mediaType = "application/json", schema = @Schema(implementation = User.class)))
    @GetMapping({"/producers", "/producers/"})
    public Flux<User> getAllProducers() {
        return userManagementService.getAllUsersByRole(User.DB_USER_ROLE_PRODUCER_NAME)
                .doFirst(() -> log.info("Retrieving all producers"))
                .doOnComplete(() -> log.info("Finished retrieving all producers"))
                .doOnError(e -> log.error("Failed to retrieve producers: {}", LogSanitizer.sanitize(e.getMessage())));
    }
}
