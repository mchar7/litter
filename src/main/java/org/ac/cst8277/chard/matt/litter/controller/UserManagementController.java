package org.ac.cst8277.chard.matt.litter.controller;

import io.swagger.v3.oas.annotations.enums.SecuritySchemeIn;
import io.swagger.v3.oas.annotations.enums.SecuritySchemeType;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.security.SecurityScheme;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.dto.LoginRequest;
import org.ac.cst8277.chard.matt.litter.dto.RegisterRequest;
import org.ac.cst8277.chard.matt.litter.dto.UserResponse;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.security.LogSanitizer;
import org.ac.cst8277.chard.matt.litter.service.UserManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Controller for handling user management-related requests.
 */
@Slf4j
@RestController
@RequestMapping("/user")
@Tag(name = "User Management API")
@SecurityScheme(
        name = "bearerAuth",
        type = SecuritySchemeType.HTTP,
        in = SecuritySchemeIn.HEADER,
        scheme = "bearer",
        bearerFormat = "JWT"
)
public class UserManagementController {
    private final UserManagementService userManagementService;

    /**
     * Constructor for UserManagementController class.
     *
     * @param usrMgmtSvc Service for handling user management-related operations
     */
    public UserManagementController(UserManagementService usrMgmtSvc) {
        userManagementService = usrMgmtSvc;
    }

    /**
     * Registers a new user with the provided username and password.
     *
     * <p>This endpoint returns a reactive Mono that emits the created user and responds with a 201 Created HTTP status.
     * Username must be at least 4 alphanumeric characters and password must be strong. If the username is already taken
     * or if the credentials don't meet the requirements, appropriate error responses will be returned.
     *
     * @param registerRequest A JSON object containing "username" and "password"
     * @return A Mono emitting the registered user with HTTP 201 Created status.
     */
    @PostMapping("/register")
    @ResponseStatus(HttpStatus.CREATED)
    public Mono<UserResponse> register(@RequestBody RegisterRequest registerRequest) {
        String username = registerRequest.getUsername();
        return userManagementService.register(username, registerRequest.getPassword())
                .map(UserResponse::fromUser)
                .doFirst(() -> log.info("Processing registration for username {}", LogSanitizer.sanitize(username)))
                .doOnSuccess(user -> log.info("User registered successfully: {}", LogSanitizer.sanitize(user.getUsername())))
                .doOnError(e -> log.error("Failed to register user {}. Error: {}",
                        LogSanitizer.sanitize(username), e.getMessage(), e));
    }

    /**
     * Logs in a user and returns a JWT token.
     *
     * <p>This endpoint returns a reactive Mono that emits the JWT token upon successful authentication
     * and responds with a 200 OK HTTP status. Credentials must be valid. If authentication fails,
     * a 401 Unauthorized response will be returned.
     *
     * @param loginRequest A JSON object containing "username" and "password"
     * @return A Mono emitting a JWT token with HTTP 200 OK status if login is successful.
     */
    @PostMapping("/login")
    public Mono<String> login(@RequestBody LoginRequest loginRequest) {
        String username = loginRequest.getUsername();
        return userManagementService.login(username, loginRequest.getPassword())
                .doFirst(() -> log.info("Processing login for user {}", LogSanitizer.sanitize(username)))
                .doOnSuccess(token -> log.info("User logged in successfully: {}", LogSanitizer.sanitize(username)))
                .doOnError(e -> log.error("Login failed for user {}. Error: {}",
                        LogSanitizer.sanitize(username), e.getMessage(), e));
    }

    /**
     * Retrieves a list of all registered users with basic details.
     *
     * <p>This endpoint returns a reactive Flux that emits all registered users
     * and responds with a 200 OK HTTP status. If no users are found, an empty Flux is returned
     * which will result in an empty array in the response.
     * This endpoint requires admin privileges.
     *
     * @return A Flux emitting all registered users with HTTP 200 OK status.
     */
    @GetMapping("/all")
    @SecurityRequirement(name = "bearerAuth")
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public Flux<UserResponse> getAllUsers() {
        return userManagementService.getAllUsers()
                .map(UserResponse::fromUser)
                .doFirst(() -> log.info("Retrieving all users"))
                .doOnComplete(() -> log.info("Finished retrieving all users"))
                .doOnError(e -> log.error("Failed to retrieve users. Error: {}", e.getMessage(), e));
    }

    /**
     * Retrieves a list of all users with the producer role.
     *
     * <p>This endpoint returns a reactive Flux that emits all users with the producer role
     * and responds with a 200 OK HTTP status. If no producers are found, an empty Flux is returned
     * which will result in an empty array in the response.
     *
     * @return A Flux emitting all users with the producer role with HTTP 200 OK status.
     */
    @GetMapping("/producers")
    @SecurityRequirement(name = "bearerAuth")
    public Flux<UserResponse> getAllProducers() {
        return userManagementService.getAllUsersByRole(User.DB_USER_ROLE_PRODUCER_NAME)
                .map(UserResponse::fromUser)
                .doFirst(() -> log.info("Retrieving all producers"))
                .doOnComplete(() -> log.info("Finished retrieving all producers"))
                .doOnError(e -> log.error("Failed to retrieve producers. Error: {}", e.getMessage(), e));
    }
}
