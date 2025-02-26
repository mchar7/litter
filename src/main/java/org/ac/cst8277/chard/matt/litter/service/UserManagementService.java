package org.ac.cst8277.chard.matt.litter.service;

import lombok.extern.slf4j.Slf4j;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.repository.UserRepository;
import org.ac.cst8277.chard.matt.litter.security.JwtUtils;
import org.ac.cst8277.chard.matt.litter.security.LogSanitizer;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.security.oauth2.jwt.JwtException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.resource.NoResourceFoundException;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.List;
import java.util.regex.Pattern;

/**
 * Service class for User objects.
 */
@Slf4j
@Service
public class UserManagementService {
    // messages for exceptions
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    private static final String USERNAME_TAKEN_MESSAGE = "User already exists";
    private static final String USER_NOT_FOUND_MESSAGE = "User not found";
    private static final Pattern USERNAME_REGEX_STR = Pattern.compile(User.USERNAME_REGEX_STR);
    private static final Pattern PASSWORD_REGEX_STR = Pattern.compile(User.PASSWORD_REGEX_STR);
    private static final String JWT_EMPTY_SUBJECT = "No subject in JWT";

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;

    /**
     * Constructor for the UserManagementService.
     *
     * @param userRepository  Repository for User objects
     * @param jwtUtils        Utility class for JWT operations
     * @param passwordEncoder Password encoder for hashing passwords
     */
    public UserManagementService(UserRepository userRepository, JwtUtils jwtUtils, PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Verify that a given username is not blank and matches the regex.
     *
     * @param username the username to validate
     * @return true if the username is valid, false otherwise
     * @see User#USERNAME_REGEX_STR
     */
    static boolean isUsernameErroneous(String username) {
        return null == username || username.isBlank() || !USERNAME_REGEX_STR.matcher(username).matches();
    }

    /**
     * Verify that a given password is not blank and matches the regex.
     *
     * @param password the password to validate
     * @return true if the password is valid, false otherwise
     * @see User#PASSWORD_REGEX_STR
     */
    private static boolean isPasswordErroneous(CharSequence password) {
        return null == password || password.isEmpty() || !PASSWORD_REGEX_STR.matcher(password.toString()).matches();
    }

    /**
     * Register a user.
     *
     * @param username username of the user
     * @param password password of the user (will be hashed)
     * @return Mono of the registered user, or error if the user already exists
     */
    public Mono<User> register(String username, CharSequence password) {
        if (isUsernameErroneous(username) || isPasswordErroneous(password)) {
            return Mono.error(new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
        }
        return userRepository.findByUsername(username)
                .doFirst(() -> log.info("Attempting to register user: {}", LogSanitizer.sanitize(username)))
                // if user already exists:
                .flatMap(existingUser -> Mono.<User>error(new IllegalArgumentException(USERNAME_TAKEN_MESSAGE)))
                .switchIfEmpty(Mono.defer(() -> {
                    log.info("Registering new user: {}", LogSanitizer.sanitize(username));
                    User user = new User();
                    user.setUsername(username);
                    user.setPasswordHash(passwordEncoder.encode(password));
                    user.setRoles(List.of(User.DB_USER_ROLE_SUBSCRIBER_NAME));
                    return userRepository.save(user);
                }))
                .doOnError(e -> log.error(
                        "Failed to register user '{}'. Error: {}", LogSanitizer.sanitize(username), e.getMessage(), e));
    }

    /**
     * Log a user in.
     *
     * @param username username of the user
     * @param password password of the user
     * @return Mono of the user's token, or Mono error if no match
     */
    public Mono<String> login(String username, CharSequence password) {
        if (isUsernameErroneous(username) || isPasswordErroneous(password)) {
            return Mono.error(new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE));
        }
        return userRepository.findByUsername(username)
                .doFirst(() -> log.info("Attempting to log in user: {}", LogSanitizer.sanitize(username)))
                // if user does not exist:
                .switchIfEmpty(Mono.error(new BadCredentialsException(USER_NOT_FOUND_MESSAGE)))
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .map(user -> {
                    log.info("User logged in successfully: {}", LogSanitizer.sanitize(username));
                    return jwtUtils.generateToken(user);
                }).switchIfEmpty(Mono.error(new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE)));
    }

    /**
     * Find a user by their JWT.
     *
     * @param jwt the JWT used to authenticate the user
     * @return Mono of the user
     */
    Mono<User> getUserByJwt(JwtClaimAccessor jwt) {
        return Mono.just(jwt.getSubject())
                .doFirst(() -> log.info("Fetching user by JWT: {}", LogSanitizer.sanitize(jwt.getSubject())))
                .switchIfEmpty(Mono.error(new JwtException(JWT_EMPTY_SUBJECT)))
                .flatMap(this::getUserByUsername)
                .switchIfEmpty(Mono.error(new NoResourceFoundException(USER_NOT_FOUND_MESSAGE)));
    }

    /**
     * Find a user by their username.
     *
     * @param username username of the user
     * @return Mono of the user
     */
    Mono<User> getUserByUsername(String username) {
        if (isUsernameErroneous(username)) {
            return Mono.error(new IllegalArgumentException(INVALID_CREDENTIALS_MESSAGE));
        }
        return userRepository.findByUsername(username)
                .doFirst(() -> log.info("Fetching user by username: {}", LogSanitizer.sanitize(username)))
                .switchIfEmpty(Mono.error(new IllegalArgumentException(USER_NOT_FOUND_MESSAGE)));
    }

    /**
     * Find all users with a specific role.
     *
     * @param role the role of users to find.
     * @return Flux of User objects with the specified role.
     */
    public Flux<User> getAllUsersByRole(String role) {
        return userRepository.findByRolesContains(role)
                .doFirst(() -> log.info("Getting all users with role: {}", role));
    }

    /**
     * Getting all users.
     *
     * @return Flux of all users
     */
    public Flux<User> getAllUsers() {
        return userRepository.findAll()
                .doFirst(() -> log.info("Fetching all users"))
                .doOnComplete(() -> log.info("Finished fetching all users"));
    }
}
