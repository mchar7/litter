package org.ac.cst8277.chard.matt.litter.service;

import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.repository.UserRepository;
import org.ac.cst8277.chard.matt.litter.security.JwtUtils;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Unit test class for UserManagementService.
 * <p>
 * This class contains tests for user registration, login, and other user-related functionality,
 * ensuring all business logic behaves as expected.
 */
class UserManagementServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(UserManagementServiceTest.class);

    // reusable, known-to-be-valid test data
    private static final String TEST_USERNAME_VALID = "unknownUser";
    private static final String TEST_PASSWORD_VALID = "RandomPassword123!";

    // error messages
    private static final String CREDENTIALS_INVALID_MESSAGE = "Invalid credentials";
    private static final String USERNAME_TAKEN_MESSAGE = "User already exists";
    private static final String USER_NOT_FOUND_MESSAGE = "User not found";
    private static final String USERNAME_INVALID_MESSAGE = """
            Invalid username. \
            Username must be at least 3 characters long \
            and contain only alphanumeric characters.""";
    private static final String PASSWORD_INSECURE_MESSAGE = """
            Invalid password. \
            Password must be at least 8 characters long \
            and contain at least: \
            one uppercase letter, \
            one lowercase letter, \
            one number, and \
            one special character.""";

    @Mock
    private UserRepository userRepository;

    @Mock
    private JwtUtils jwtUtils;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private UserManagementService userManagementService;

    private AutoCloseable closeable;

    /**
     * Sets up mocks before each test.
     */
    @BeforeEach
    void setUp() {
        logger.debug("Setting up mocks...");
        closeable = MockitoAnnotations.openMocks(this);

        // mock password encoder behavior
        Mockito.when(passwordEncoder.encode(ArgumentMatchers.anyString()))
                .thenAnswer(invocation -> {
                    String password = invocation.getArgument(0);
                    return String.format("mocked_hash_for_%s", password);
                });

        logger.info("Mocks initialized successfully.");
    }

    /**
     * Cleans up resources after each test.
     */
    @AfterEach
    void tearDown() {
        try {
            closeable.close();
            logger.info("Mocks and resources cleaned up.");
        } catch (Exception e) {
            logger.error("Error closing resources during teardown", e);
        }
    }

    /**
     * Tests registering a new user with valid credentials.
     * <p>
     * Tests on: register(String username, String password)
     * Expected: Successfully returns the new user.
     */
    @Test
    void testRegisterNewUser_Success() {
        logger.info("Testing registration with new user: {}", TEST_USERNAME_VALID);

        // mock repository behavior to return no existing user
        Mockito.when(userRepository.findByUsername(TEST_USERNAME_VALID))
                .thenReturn(Mono.empty());

        String expectedHash = String.format("mocked_hash_for_%s", TEST_PASSWORD_VALID);

        // repository should save the user
        Mockito.when(userRepository.save(ArgumentMatchers.any(User.class)))
                .thenAnswer(invocation -> {
                    User user = invocation.getArgument(0);
                    user.setId(new ObjectId());
                    return Mono.just(user);
                });

        // expect a new user to be created
        StepVerifier.create(userManagementService.register(TEST_USERNAME_VALID, TEST_PASSWORD_VALID))
                .assertNext(user -> {
                    assertNotNull(user.getId(), "User ID should not be null");
                    assertEquals(TEST_USERNAME_VALID, user.getUsername(), "Expected correct username");
                    assertEquals(List.of(User.DB_USER_ROLE_SUBSCRIBER_NAME), user.getRoles(),
                            "Expected correct roles");
                    assertEquals(expectedHash, user.getPasswordHash(),
                            "Expected mock password hash");
                })
                .expectComplete()
                .verify();

        // verify mock interactions
        Mockito.verify(passwordEncoder).encode(TEST_PASSWORD_VALID);
        Mockito.verify(userRepository).save(ArgumentMatchers.any(User.class));

        logger.info("Test for registering new user completed.");
    }

    /**
     * Tests registering a user with an existing username.
     * <p>
     * Tests on: register(String username, String password)
     * Expected: IllegalArgumentException
     */
    @Test
    void testRegisterExistingUser_ThrowsError() {
        logger.info("Testing registration with existing user: {}", TEST_USERNAME_VALID);

        // mock existing user
        User existingUser = new User();
        existingUser.setUsername(TEST_USERNAME_VALID);
        logger.debug("Simulating existing user: {}", existingUser);

        // repository returns a user matching the given username
        Mockito.when(userRepository.findByUsername(TEST_USERNAME_VALID)).thenReturn(Mono.just(existingUser));

        // expect an error for existing user
        StepVerifier.create(userManagementService.register(TEST_USERNAME_VALID, TEST_PASSWORD_VALID))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                                Objects.equals(USERNAME_TAKEN_MESSAGE, throwable.getMessage()))
                .verify();
        logger.info("Verified error for existing user registration.");

        // confirm no further calls happen
        Mockito.verify(userRepository, Mockito.times(1)).findByUsername(TEST_USERNAME_VALID);
        Mockito.verifyNoMoreInteractions(userRepository);
        logger.info("Test for registering existing user completed.");
    }

    /**
     * Tests registering a user with invalid usernames.
     * <p>
     * Tests on: register(String username, String password)
     * Expected: IllegalArgumentException
     */
    @Test
    void testRegisterInvalidUsername_ThrowsError() {
        // multiple invalid passwords to test different regex failures
        // key: password we are testing, value: reason for expected failure
        Map<String, String> invalidUsernames = Map.of(
                "", "Empty username.",
                "ye", "Too short (min 3 chars).",
                "Its Me Mario", "Spaces not allowed.",
                "It'sMeMario", "Special characters not allowed."
        );

        // test each invalid username
        invalidUsernames.forEach((username, reason) -> {
            logger.info("Testing registration with invalid username: '{}' reason: {}", username, reason);

            // repository returns no existing user
            Mockito.when(userRepository.findByUsername(username)).thenReturn(Mono.empty());

            // expect an error for invalid username
            StepVerifier.create(userManagementService.register(username, TEST_PASSWORD_VALID))
                    .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().contains(USERNAME_INVALID_MESSAGE))
                    .verify();

            // ensure no user is saved
            Mockito.verify(userRepository, Mockito.never()).save(ArgumentMatchers.any(User.class));
        });

        logger.info("All invalid username tests completed successfully.");
    }

    /**
     * Tests registering a user with insecure passwords.
     * <p>
     * Tests on: register(String username, String password)
     * Expected: IllegalArgumentException
     */
    @Test
    void testRegisterInvalidPassword_ThrowsError() {
        // multiple invalid passwords to test different regex failures
        Map<String, String> invalidPasswords = Map.of(
                "", "Empty password.",
                "Ye123!", "Too short (min 8 chars).",
                "mysecurepassword?", "Missing uppercase letter.",
                "MYSECUREPASSWORD!", "Missing lowercase letter.",
                "MyInSecurePassword123", "Missing special character.",
                "ThisGottaBeOkThoRight?", "Missing number."
        );

        // test each invalid password
        invalidPasswords.forEach((password, reason) -> {
            logger.info("Testing registration with insecure password: '{}' reason: {}", password, reason);

            // repository returns no existing user
            Mockito.when(userRepository.findByUsername(TEST_USERNAME_VALID)).thenReturn(Mono.empty());

            // expect an error for insecure password
            StepVerifier.create(userManagementService.register(TEST_USERNAME_VALID, password))
                    .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().contains(PASSWORD_INSECURE_MESSAGE))
                    .verify();

            // ensure no user is saved
            Mockito.verify(userRepository, Mockito.never()).save(ArgumentMatchers.any(User.class));
        });

        logger.info("All insecure password tests completed successfully.");
    }

    /**
     * Tests registering a user with invalid usernames.
     * <p>
     * Tests on: register(String username, String password)
     * Expected: IllegalArgumentException
     */
    @Test
    void testLoginInvalidUsername_ThrowsError() {
        // multiple invalid passwords to test different regex failures
        // key: password we are testing, value: reason for expected failure
        Map<String, String> invalidUsernames = Map.of(
                "", "Empty username.",
                "ye", "Too short (min 3 chars).",
                "Its Me Mario", "Spaces not allowed.",
                "It'sMeMario", "Special characters not allowed."
        );

        // test each invalid username
        invalidUsernames.forEach((username, reason) -> {
            logger.info("Testing login with invalid username: '{}' reason: {}", username, reason);

            // should error due to invalid username
            StepVerifier.create(userManagementService.login(username, TEST_PASSWORD_VALID))
                    .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().contains(USERNAME_INVALID_MESSAGE))
                    .verify();
        });
        Mockito.verifyNoInteractions(userRepository);
        logger.info("All invalid username login tests completed successfully.");
    }

    /**
     * Tests registering a user with insecure passwords.
     * <p>
     * Tests on: register(String username, String password)
     * Expected: IllegalArgumentException
     */
    @Test
    void testLoginInvalidPassword_ThrowsError() {
        // multiple invalid passwords to test different regex failures
        Map<String, String> invalidPasswords = Map.of(
                "", "Empty password.",
                "Ye123!", "Too short (min 8 chars).",
                "mysecurepassword?", "Missing uppercase letter.",
                "MYSECUREPASSWORD!", "Missing lowercase letter.",
                "MyInSecurePassword123", "Missing special character.",
                "ThisGottaBeOkThoRight?", "Missing number."
        );

        // test each invalid password
        invalidPasswords.forEach((password, reason) -> {
            logger.info("Testing login with insecure password: '{}' reason: {}", password, reason);

            // repository returns no existing user
            Mockito.when(userRepository.findByUsername(TEST_USERNAME_VALID)).thenReturn(Mono.empty());

            // should error due to insecure password
            StepVerifier.create(userManagementService.login(TEST_USERNAME_VALID, password))
                    .expectErrorMatches(throwable -> throwable instanceof IllegalArgumentException &&
                            throwable.getMessage().contains(PASSWORD_INSECURE_MESSAGE))
                    .verify();
        });
        Mockito.verifyNoInteractions(userRepository);
        logger.info("All insecure password login tests completed successfully.");
    }

    /**
     * Tests logging in with valid credentials.
     * <p>
     * Tests on: login(String username, String password)
     * Expected: Successfully returns a JWT token
     */
    @Test
    void testLoginCorrectCredentials_ReturnsActualToken() {
        logger.info("Testing login with valid credentials for user: {}", TEST_USERNAME_VALID);

        // create test user with valid credentials
        User validUser = new User();
        validUser.setUsername(TEST_USERNAME_VALID);
        validUser.setId(ObjectId.get());
        validUser.setRoles(List.of(User.DB_USER_ROLE_SUBSCRIBER_NAME));
        validUser.setPasswordHash(TEST_PASSWORD_VALID);
        Mockito.when(userRepository.findByUsername(TEST_USERNAME_VALID))
                .thenReturn(Mono.just(validUser));

        // mock password encoder to validate password
        Mockito.when(passwordEncoder.matches(TEST_PASSWORD_VALID, validUser.getPasswordHash())).thenReturn(true);

        Mockito.when(jwtUtils.generateToken(ArgumentMatchers.any(User.class))).thenReturn(validUser.getPasswordHash());

        // test the login, expect a token
        StepVerifier.create(userManagementService.login(TEST_USERNAME_VALID, TEST_PASSWORD_VALID))
                .expectNext(validUser.getPasswordHash())
                .verifyComplete();

        // verify our mocks were called correctly
        Mockito.verify(userRepository).findByUsername(TEST_USERNAME_VALID);
        Mockito.verify(passwordEncoder).matches(TEST_PASSWORD_VALID, validUser.getPasswordHash());
        Mockito.verify(jwtUtils).generateToken(validUser);

        logger.info("Login test with valid credentials completed successfully");
    }

    /**
     * Tests logging in with incorrect password for an existing user.
     * <p>
     * Tests on: login(String username, String password)
     * Expected: BadCredentialsException
     */
    @Test
    void testLoginRealUserWrongPassword_ThrowsBadCredentials() {
        logger.info("Testing login with invalid credentials for real user: {}", TEST_USERNAME_VALID);

        String wrongPassword = "WrongPassword123!";
        User user = new User();
        user.setUsername(TEST_USERNAME_VALID);
        user.setPasswordHash(passwordEncoder.encode(TEST_PASSWORD_VALID));

        // repository finds user exactly once
        Mockito.when(userRepository.findByUsername(TEST_USERNAME_VALID))
                .thenReturn(Mono.just(user));

        // expect an error for bad credentials
        StepVerifier.create(userManagementService.login(TEST_USERNAME_VALID, wrongPassword))
                .expectErrorMatches(throwable ->
                        throwable instanceof BadCredentialsException &&
                                throwable.getMessage().contains(CREDENTIALS_INVALID_MESSAGE))
                .verify();

        // verify repository called once
        Mockito.verify(userRepository, Mockito.times(1))
                .findByUsername(TEST_USERNAME_VALID);

        logger.info("Test for invalid password for real user completed.");
    }

    /**
     * Tests logging in with a non-existent user.
     * <p>
     * Tests on: login(String username, String password)
     * Expected: BadCredentialsException.
     */
    @Test
    void testLoginNonExistentUser_ThrowsBadCredentials() {
        logger.info("Testing login for non-existent user: {}", TEST_USERNAME_VALID);

        // repository returns no user found
        Mockito.when(userRepository.findByUsername(TEST_USERNAME_VALID)).thenReturn(Mono.empty());

        // expect an error for bad credentials
        StepVerifier.create(userManagementService.login(TEST_USERNAME_VALID, TEST_PASSWORD_VALID))
                .expectErrorMatches(throwable ->
                        throwable instanceof BadCredentialsException &&
                                throwable.getMessage().contains(USER_NOT_FOUND_MESSAGE))
                .verify();

        // verify single repository call
        Mockito.verify(userRepository).findByUsername(TEST_USERNAME_VALID);
        logger.info("Test for non-existent user login completed.");
    }

    /**
     * Tests retrieving a user by JWT with a valid user subject.
     * <p>
     * Tests on: getUserByJwt(JwtClaimAccessor jwt)
     * Expected: Successfully returns the user
     */
    @Test
    void testGetUserByJwt_ValidUser_ReturnsUser() {
        String subject = TEST_USERNAME_VALID;
        logger.info("Testing getUserByJwt with valid user subject: {}", subject);

        // mock user found
        User mockUser = new User();
        mockUser.setUsername(subject);
        mockUser.setId(ObjectId.get());
        Mockito.when(userRepository.findByUsername(subject)).thenReturn(Mono.just(mockUser));

        // mock JWT claim accessor providing the sub
        JwtClaimAccessor mockClaimAccessor = () -> Map.of("sub", subject);

        // verify the user is fetched
        StepVerifier.create(userManagementService.getUserByJwt(mockClaimAccessor))
                .assertNext(user -> assertEquals(subject, user.getUsername(), "Expected correct user fetched"))
                .verifyComplete();
        logger.info("Test for getUserByJwt with valid user completed.");
    }

    /**
     * Tests retrieving a user by JWT with a non-existent user subject.
     * <p>
     * Tests on: getUserByJwt(JwtClaimAccessor jwt)
     * Expected: IllegalArgumentException
     */
    @Test
    void testGetUserByJwt_NonExistentUser_ThrowsError() {
        logger.info("Testing getUserByJwt with non-existent user subject: {}", TEST_USERNAME_VALID);

        // no user found by that username
        Mockito.when(userRepository.findByUsername(TEST_USERNAME_VALID)).thenReturn(Mono.empty());
        JwtClaimAccessor mockClaimAccessor = () -> Map.of("sub", TEST_USERNAME_VALID);

        // expect an error for missing user
        StepVerifier.create(userManagementService.getUserByJwt(mockClaimAccessor))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                                throwable.getMessage().contains(USER_NOT_FOUND_MESSAGE))
                .verify();

        // verify that the repository was queried once
        Mockito.verify(userRepository, Mockito.times(1)).findByUsername(TEST_USERNAME_VALID);
        logger.info("Test for getUserByJwt non-existent user completed.");
    }

    /**
     * Tests retrieving a user by username when the user exists.
     * <p>
     * Tests on: getUserByUsername(String username)
     * Expected: Successfully returns the user
     */
    @Test
    void testGetUserByUsername_FoundUser_ReturnsUser() {
        logger.info("Testing getUserByUsername for existing user: {}", TEST_USERNAME_VALID);

        // user found in the repository
        User existingUser = new User();
        existingUser.setUsername(TEST_USERNAME_VALID);
        existingUser.setId(ObjectId.get());
        Mockito.when(userRepository.findByUsername(TEST_USERNAME_VALID)).thenReturn(Mono.just(existingUser));

        // expect success
        StepVerifier.create(userManagementService.getUserByUsername(TEST_USERNAME_VALID))
                .assertNext(user -> assertEquals(
                        TEST_USERNAME_VALID, user.getUsername(), "Expected correct user fetched"))
                .verifyComplete();
        logger.info("Test for getUserByUsername with existing user completed.");
    }

    /**
     * Tests retrieving a user by username who does not exist.
     * <p>
     * Tests on: getUserByUsername(String username)
     * Expected: IllegalArgumentException
     */
    @Test
    void testGetUserByUsername_NotFoundUser_ThrowsError() {
        logger.info("Testing getUserByUsername for non-existent user: {}", TEST_USERNAME_VALID);

        // repository returns empty
        Mockito.when(userRepository.findByUsername(TEST_USERNAME_VALID)).thenReturn(Mono.empty());

        // expect an error
        StepVerifier.create(userManagementService.getUserByUsername(TEST_USERNAME_VALID))
                .expectErrorMatches(throwable ->
                        throwable instanceof IllegalArgumentException &&
                                throwable.getMessage().contains(USER_NOT_FOUND_MESSAGE))
                .verify();

        // verify that the repository was queried once
        Mockito.verify(userRepository, Mockito.times(1)).findByUsername(TEST_USERNAME_VALID);
        logger.info("Test for getUserByUsername with non-existent user completed.");
    }

    /**
     * Tests retrieving all users by a specified role.
     * <p>
     * Tests on: getAllUsersByRole(String role)
     * Expected: Successfully returns all users with the specified role.
     */
    @Test
    void testGetAllUsersByRole_ReturnsUsers() {
        String targetRole = User.DB_USER_ROLE_PRODUCER_NAME;
        logger.info("Testing getAllUsersByRole for role: {}", targetRole);

        // create three different users with roles
        User user1 = new User();
        user1.setUsername("producer1");
        user1.setRoles(List.of(User.DB_USER_ROLE_PRODUCER_NAME));

        User user2 = new User();
        user2.setUsername("producer2");
        user2.setRoles(List.of(User.DB_USER_ROLE_PRODUCER_NAME, User.DB_USER_ROLE_SUBSCRIBER_NAME));

        User user3 = new User();
        user3.setUsername("subscriber1");
        user3.setRoles(List.of(User.DB_USER_ROLE_SUBSCRIBER_NAME));

        // mock repository returning three users
        Mockito.when(userRepository.findAll()).thenReturn(Flux.just(user1, user2, user3));

        // expect only users with producer role
        StepVerifier.create(userManagementService.getAllUsersByRole(targetRole))
                .expectNext(user1)
                .expectNext(user2)
                .verifyComplete();

        // verify that the repository was queried once
        Mockito.verify(userRepository, Mockito.times(1)).findAll();
        logger.info("Test for getAllUsersByRole completed.");
    }

    /**
     * Tests retrieving all users.
     * <p>
     * Tests on: getAllUsers()
     * Expected: Successfully returns a flux of all user documents.
     */
    @Test
    void testGetAllUsers_ReturnsAllUsers() {
        logger.info("Testing getAllUsers.");

        // create two sample users
        User userA = new User();
        userA.setUsername("userA");
        userA.setId(ObjectId.get());
        userA.setRoles(List.of(User.DB_USER_ROLE_SUBSCRIBER_NAME));

        User userB = new User();
        userB.setUsername("userB");
        userB.setId(ObjectId.get());
        userB.setRoles(List.of(User.DB_USER_ROLE_SUBSCRIBER_NAME, User.DB_USER_ROLE_PRODUCER_NAME));

        // repository returns two users
        Mockito.when(userRepository.findAll()).thenReturn(Flux.just(userA, userB));

        // verify flux of mapped user data
        StepVerifier.create(userManagementService.getAllUsers())
                .assertNext(map -> {
                    // confirm userA is present in map
                    assertTrue(map.containsValue(userA.getUsername()), "Should contain user A username");
                    assertTrue(map.containsValue(userA.getRoles()), "Should contain user A roles");
                })
                .assertNext(map -> {
                    // confirm userB is present in map
                    assertTrue(map.containsValue(userB.getUsername()), "Should contain user B username");
                    assertTrue(map.containsValue(userB.getRoles()), "Should contain user B roles");
                })
                .verifyComplete();
        logger.info("Test for getAllUsers completed.");
    }
}