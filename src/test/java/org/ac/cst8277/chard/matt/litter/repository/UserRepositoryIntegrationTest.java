package org.ac.cst8277.chard.matt.litter.repository;

import org.ac.cst8277.chard.matt.litter.model.User;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.extension.ExtendWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.data.mongo.DataMongoTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.junit.jupiter.SpringExtension;
import org.testcontainers.containers.MongoDBContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for UserRepository using Testcontainers for a real MongoDB instance.
 */
@ExtendWith(SpringExtension.class)
@DataMongoTest
@Testcontainers
@DirtiesContext
@Import({})
class UserRepositoryIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(UserRepositoryIntegrationTest.class);

    /**
     * Testcontainers MongoDB instance to ensure integration tests
     * run off a real (ephemeral) MongoDB rather than an in-memory mock.
     */
    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0");
    @Autowired
    private UserRepository userRepository;

    /**
     * Dynamically set the spring.data.mongodb.uri property so Spring Data picks up our container's DB.
     *
     * @param registry the property registry
     */
    @DynamicPropertySource
    static void setMongoDBProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @BeforeAll
    static void beforeAll() {
        logger.info("Starting up MongoDB container for UserRepositoryIntegrationTest...");
        mongoDBContainer.start();
    }

    @AfterAll
    static void afterAll() {
        logger.info("Stopping MongoDB container for UserRepositoryIntegrationTest...");
        mongoDBContainer.stop();
    }

    @BeforeEach
    void setUp() {
        logger.debug("Setting up test data for UserRepositoryIntegrationTest.");
        // clean out the repository before each test
        userRepository.deleteAll().block();
    }

    @AfterEach
    void tearDown() {
        logger.debug("Test completed, cleaning up for UserRepositoryIntegrationTest.");
        userRepository.deleteAll().block();
    }

    /**
     * Tests saving a user and retrieving it by username.
     * <p>
     * Tests on: findByUsername(String username)
     * Expected: Successfully retrieves the user by username.
     */
    @Test
    void testSaveUserThenFindByUsername() {
        logger.info("Testing saveUserAndFindByUsername in container-based environment...");

        User user = new User();
        user.setId(new ObjectId());
        user.setUsername("containerUser");
        user.setPasswordHash("totallyHashedPassword");
        user.setRoles(List.of("ROLE_SUBSCRIBER"));

        Mono<User> saveOperation = userRepository.save(user);
        Mono<User> findOperation = saveOperation.then(userRepository.findByUsername("containerUser"));

        // expect the user to be found
        StepVerifier.create(findOperation)
                .assertNext(foundUser -> {
                    assertNotNull(foundUser.getId(), "Saved user ID should not be null");
                    assertEquals("containerUser", foundUser.getUsername(), "Expected matching username");
                    logger.debug("Verified user with username: {}", foundUser.getUsername());
                })
                .verifyComplete();
        logger.info("testSaveUserAndFindByUsername completed successfully.");
    }

    /**
     * Tests that a non-existent username returns Mono.empty().
     * <p>
     * Tests on: findByUsername(String username)
     * Expected: No user is found for the non-existent username.
     */
    @Test
    void testFindByUsername_NonExistent() {
        logger.info("Testing findByUsername for non-existent user...");

        // search for a non-existent user, assert that no user is found
        Mono<User> findOperation = userRepository.findByUsername("nonExistentUser");
        assertNull(findOperation.block(), "No user should be found for non-existent username");

        logger.info("testFindByUsername_NonExistent verified that no user is found.");
    }
}