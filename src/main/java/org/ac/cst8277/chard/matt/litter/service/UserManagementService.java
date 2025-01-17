package org.ac.cst8277.chard.matt.litter.service;

import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.repository.UserRepository;
import org.ac.cst8277.chard.matt.litter.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;

import static org.ac.cst8277.chard.matt.litter.model.User.ROLES_HASHMAP_DEFAULT_CAP;

/**
 * Service class for User objects.
 */
@Service
public class UserManagementService {
    private static final String USERNAME_INVALID_MESSAGE = """
            Invalid username. \
            Username must be at least 3 characters long \
            and contain only alphanumeric characters.""";
    private static final Logger logger = LoggerFactory.getLogger(UserManagementService.class);
    // field names as stored in the database
    private static final String FIELD_ID = "id";
    private static final String FIELD_ROLES = "roles";
    private static final String FIELD_USERNAME = "username";
    // messages for exceptions
    private static final String INVALID_CREDENTIALS_MESSAGE = "Invalid credentials";
    private static final String USERNAME_TAKEN_MESSAGE = "User already exists";
    private static final String USER_NOT_FOUND_MESSAGE = "User not found";
    private static final String PASSWORD_INSECURE_MESSAGE = """
            Invalid password. \
            Password must be at least 8 characters long \
            and contain at least: \
            one uppercase letter, \
            one lowercase letter, \
            one number, and \
            one special character.""";

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
    @Autowired
    public UserManagementService(UserRepository userRepository,
                                 JwtUtils jwtUtils,
                                 PasswordEncoder passwordEncoder) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
        this.passwordEncoder = passwordEncoder;
    }

    /**
     * Ensure that a username meets the requirements.
     * <p>
     * Username must be at least 3 characters (only alphanumeric).
     *
     * @param testUsername the username to test
     * @return true if the username meets the requirements, false otherwise
     */
    private static boolean isUsernameErroneous(String testUsername) {
        if (null == testUsername || testUsername.isBlank()) {
            return true;
        }
        Pattern regex = Pattern.compile("^[a-zA-Z0-9]{3,}$");
        return !regex.matcher(testUsername).matches();
    }

    /**
     * Ensure that a password meets the requirements.
     * <p>
     * Password must be at least 8 characters long and contain at least:
     * <ul>
     *     <li>one uppercase letter,</li>
     *     <li>one lowercase letter,</li>
     *     <li>one number, and</li>
     *     <li>one special character.</li>
     * </ul>
     *
     * @param testPassword the password to test
     * @return true if the password meets the requirements, false otherwise
     */
    private static boolean isPasswordWeak(String testPassword) {
        if (null == testPassword || testPassword.isBlank()) {
            return true;
        }
        Pattern regex = Pattern.compile("(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[^A-Za-z0-9]).{8,256}");
        return !regex.matcher(testPassword).matches();
    }

    /**
     * Register a user.
     *
     * @param username username of the user
     * @param password password of the user (will be hashed)
     * @return Mono of the registered user, or error if the user already exists
     */
    public Mono<User> register(String username, String password) {
        if (isUsernameErroneous(username)) {
            logger.warn("Attempt to register with invalid username: {}", username);
            return Mono.error(new IllegalArgumentException(USERNAME_INVALID_MESSAGE));
        }
        if (isPasswordWeak(password)) {
            logger.warn("Attempt to register with insecure password. Username: {}", username);
            return Mono.error(new IllegalArgumentException(PASSWORD_INSECURE_MESSAGE));
        }
        return userRepository.findByUsername(username)
                // run through passwordMeetsRequirements to ensure data is valid
                .flatMap(existingUser -> {
                    logger.warn("Attempt to register existing username: {}", username);
                    return Mono.<User>error(new IllegalArgumentException(USERNAME_TAKEN_MESSAGE));
                })
                .switchIfEmpty(Mono.defer(() -> {
                    User user = new User();
                    user.setUsername(username);
                    user.setPasswordHash(passwordEncoder.encode(password));
                    user.setRoles(List.of(User.DB_USER_ROLE_SUBSCRIBER_NAME));
                    logger.info("Registering new user: {}", username);
                    return userRepository.save(user);
                }));
    }

    /**
     * Log a user in.
     *
     * @param username username of the user
     * @param password password of the user
     * @return Mono of the user's token, or Mono error if no match
     */
    public Mono<String> login(String username, String password) {
        if (isUsernameErroneous(username)) {
            logger.warn("Attempt to login with invalid username: {}", username);
            return Mono.error(new IllegalArgumentException(USERNAME_INVALID_MESSAGE));
        }
        if (isPasswordWeak(password)) {
            logger.warn("Attempt to login with insecure password. Username: {}", username);
            return Mono.error(new IllegalArgumentException(PASSWORD_INSECURE_MESSAGE));
        }
        return userRepository.findByUsername(username)
                // if user does not exist:
                .switchIfEmpty(Mono.error(new BadCredentialsException(USER_NOT_FOUND_MESSAGE)))
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .map(user -> {
                    logger.info("User logged in successfully: {}", username);
                    return jwtUtils.generateToken(user);
                })
                .switchIfEmpty(Mono.error(new BadCredentialsException(INVALID_CREDENTIALS_MESSAGE)));
    }

    /**
     * Find a user by their JWT.
     *
     * @param jwt the JWT used to authenticate the user
     * @return Mono of the user
     */
    Mono<User> getUserByJwt(JwtClaimAccessor jwt) {
        return Mono.just(jwt.getSubject())
                .flatMap(this::getUserByUsername);
    }

    /**
     * Find a user by their username.
     *
     * @param username username of the user
     * @return Mono of the user
     */
    Mono<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new IllegalArgumentException(USER_NOT_FOUND_MESSAGE)));
    }

    /**
     * Find all users with a specific role.
     *
     * @param role the role of users to find.
     * @return Flux of User objects with the specified role.
     */
    public Flux<User> getAllUsersByRole(String role) {
        logger.info("Fetching all users with role: {}", role);
        return userRepository.findAll()
                .filter(user -> user.getRoles().contains(role));
    }

    /**
     * Getting all users.
     *
     * @return Flux of all users
     */
    public Flux<Map<String, Object>> getAllUsers() {
        logger.info("Fetching all users");
        return userRepository.findAll()
                .map(user -> {
                    Map<String, Object> userMap = HashMap.newHashMap(ROLES_HASHMAP_DEFAULT_CAP);
                    userMap.put(FIELD_ID, user.getId().toString());
                    userMap.put(FIELD_USERNAME, user.getUsername());
                    userMap.put(FIELD_ROLES, user.getRoles());
                    return userMap;
                });
    }
}
