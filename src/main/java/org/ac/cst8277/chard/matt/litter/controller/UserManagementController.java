package org.ac.cst8277.chard.matt.litter.controller;

import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.service.UserManagementService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.web.bind.annotation.*;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.Map;

/**
 * Controller for handling user management-related requests.
 */
@RestController
@RequestMapping("/user")
public class UserManagementController {

    private static final Logger logger = LoggerFactory.getLogger(UserManagementController.class);

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

    private static boolean isRegisterBodyValid(CharSequence username, CharSequence password) {
        return null == username || null == password || username.isEmpty() || password.isEmpty();
    }

    /**
     * Endpoint for registering a new user.
     *
     * @param registerInfo Map containing the username and password for registration.
     * @return Mono of HTTP response entity containing the registered user
     */
    @PostMapping({"/register", "/register/"})
    public Mono<ResponseEntity<User>> register(@RequestBody Map<String, String> registerInfo) {
        String username = registerInfo.get(USERNAME_KEY);
        String password = registerInfo.get(PASSWORD_KEY);

        if (isRegisterBodyValid(username, password)) {
            logger.warn("Registration attempt with invalid input");
            return Mono.just(ResponseEntity.badRequest().build());
        }

        return userManagementService.register(username, password)
                .map(user -> {
                    logger.info("User registered successfully: {}", username);
                    return ResponseEntity.status(HttpStatus.CREATED).body(user);
                })
                .onErrorResume(e -> {
                    logger.error("Error during user registration: {}", e.getMessage());
                    return Mono.just(ResponseEntity.badRequest().build());
                });
    }

    /**
     * Endpoint for logging in a user.
     *
     * @param loginInfo Map containing the username and password for login
     * @return HTTP response entity containing the login token
     */
    @PostMapping({"/login", "/login/"})
    public Mono<ResponseEntity<String>> login(@RequestBody Map<String, String> loginInfo) {
        String username = loginInfo.get(USERNAME_KEY);
        String password = loginInfo.get(PASSWORD_KEY);

        return userManagementService.login(username, password)
                .map(token -> {
                    logger.info("User logged in successfully: {}", username);
                    return ResponseEntity.ok(token);
                })
                .onErrorResume(BadCredentialsException.class, e -> {
                    logger.warn("Failed login attempt for username: {}", username);
                    return Mono.just(ResponseEntity.status(HttpStatus.UNAUTHORIZED).body("Invalid credentials"));
                })
                .onErrorResume(e -> {
                    logger.error("Error during user login: {}", e.getMessage());
                    return Mono.just(ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body("An error occurred"));
                });
    }

    /**
     * Endpoint for getting all users.
     *
     * @return Flux of maps containing user information
     */
    @GetMapping({"/all", "/all/"})
    public Flux<Map<String, Object>> getAllUsers() {
        logger.info("Fetching all users");
        return userManagementService.getAllUsers();
    }

    /**
     * Endpoint for getting all producers.
     *
     * @return Flux of User objects representing producers
     */
    @GetMapping({"/producers", "/producers/"})
    public Flux<User> getAllProducers() {
        logger.info("Fetching all producers");
        return userManagementService.getAllUsersByRole(User.DB_USER_ROLE_PRODUCER_NAME);
    }
}