package org.ac.cst8277.chard.matt.litter.service;

import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.repository.UserRepository;
import org.ac.cst8277.chard.matt.litter.security.JwtUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import org.springframework.stereotype.Service;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import static org.ac.cst8277.chard.matt.litter.model.User.ROLES_HASHMAP_DEFAULT_CAP;

/**
 * Service class for User objects.
 */
@Service
public class UserManagementService {
    private static final Logger logger = LoggerFactory.getLogger(UserManagementService.class);
    private static final String PASSWORD_INSECURE_MESSAGE =
            """
                    Password must be at least 8 characters long and contain at least: \
                    one uppercase letter, \
                    one lowercase letter, \
                    one number, and \
                    one special character.""";

    // field names for user properties
    private static final String FIELD_ID = "id";
    private static final String FIELD_USERNAME = "username";
    private static final String FIELD_ROLES = "roles";

    private final UserRepository userRepository;
    private final Argon2PasswordEncoder passwordEncoder;
    private final JwtUtils jwtUtils;
    @Value("${litter.security.password-validation-regex}")
    private String passwordValidationRegex;


    /**
     * Constructor for the UserManagementService.
     *
     * @param userRepository Repository for User objects
     * @param jwtUtils       Utility class for JWT operations
     */
    @Autowired
    public UserManagementService(UserRepository userRepository, JwtUtils jwtUtils) {
        this.userRepository = userRepository;
        this.jwtUtils = jwtUtils;
        passwordEncoder = Argon2PasswordEncoder.defaultsForSpringSecurity_v5_8();
    }

    /**
     * Method for registering a user.
     *
     * @param username username of the user
     * @param password password of the user (will be hashed)
     * @return Mono of the registered user, or error if the user already exists
     */
    public Mono<User> register(String username, String password) {
        return userRepository.findByUsername(username)
                // run through passwordMeetsRequirements to ensure password is secure:
                .switchIfEmpty(Mono.defer(() -> {
                    if (!isPasswordSecure(password)) {
                        logger.info("Attempted password: {}", password);
                        logger.warn("Attempt to register with insecure password. Username: {}", username);
                        return Mono.error(new IllegalArgumentException(PASSWORD_INSECURE_MESSAGE));
                    }
                    return Mono.empty();
                }))
                .flatMap(existingUser -> {
                    logger.warn("Attempt to register existing username: {}", username);
                    return Mono.<User>error(new IllegalArgumentException("User already exists"));
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
     * Method for logging in a user.
     *
     * @param username username of the user
     * @param password password of the user
     * @return Mono of the user's token, or Mono error if no match
     */
    public Mono<String> login(String username, CharSequence password) {
        return userRepository.findByUsername(username)
                .filter(user -> passwordEncoder.matches(password, user.getPasswordHash()))
                .map(user -> {
                    logger.info("User logged in successfully: {}", username);
                    return jwtUtils.generateToken(user);
                })
                .switchIfEmpty(Mono.error(new BadCredentialsException("Invalid credentials")));
    }

    /**
     * Method for finding a user by their JWT.
     *
     * @param jwt the JWT used to authenticate the user
     * @return Mono of the user
     */
    Mono<User> getUserByJwt(JwtClaimAccessor jwt) {
        return Mono.just(jwt.getSubject())
                .flatMap(this::getUserByUsername);
    }

    /**
     * Method for finding a user by their username.
     *
     * @param username username of the user
     * @return Mono of the user
     */
    Mono<User> getUserByUsername(String username) {
        return userRepository.findByUsername(username)
                .switchIfEmpty(Mono.error(new IllegalArgumentException("User not found")));
    }

    /**
     * Method to find all users with a specific role.
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
     * Method for getting all users.
     *
     * @return Flux of all users
     */
    public Flux<Map<String, Object>> getAllUsers() {
        logger.info("Fetching all users");
        return userRepository.findAll()
                .map(user -> {
                    Map<String, Object> userMap = new HashMap<>(ROLES_HASHMAP_DEFAULT_CAP);
                    userMap.put(FIELD_ID, user.getId().toString());
                    userMap.put(FIELD_USERNAME, user.getUsername());
                    userMap.put(FIELD_ROLES, user.getRoles());
                    return userMap;
                });
    }

    /**
     * Method for ensuring a password meets the requirements.
     *
     * @param testPassword the password to test
     * @return true if the password meets the requirements, false otherwise
     */
    private boolean isPasswordSecure(String testPassword) {
        return testPassword.matches(passwordValidationRegex);
    }
}
