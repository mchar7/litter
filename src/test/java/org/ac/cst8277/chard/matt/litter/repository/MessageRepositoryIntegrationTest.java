package org.ac.cst8277.chard.matt.litter.repository;

import org.ac.cst8277.chard.matt.litter.model.Message;
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
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Integration test for MessageRepository using Testcontainers for real MongoDB.
 */
@ExtendWith(SpringExtension.class)
@DataMongoTest
@Testcontainers
@DirtiesContext
@Import({})
class MessageRepositoryIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(MessageRepositoryIntegrationTest.class);
    private static final String TEST_MESSAGE_CONTENT = "Container test message";

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0");
    @Autowired
    private MessageRepository messageRepository;

    @DynamicPropertySource
    static void setMongoDBProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @BeforeAll
    static void beforeAll() {
        logger.info("Starting up MongoDB container for MessageRepositoryIntegrationTest...");
        mongoDBContainer.start();
    }

    @AfterAll
    static void afterAll() {
        logger.info("Stopping MongoDB container for MessageRepositoryIntegrationTest...");
        mongoDBContainer.stop();
    }

    @BeforeEach
    void setUp() {
        logger.debug("Setting up test data for MessageRepositoryIntegrationTest.");
        messageRepository.deleteAll().block();
    }

    @AfterEach
    void tearDown() {
        logger.debug("Test completed, cleaning repository for MessageRepositoryIntegrationTest.");
        messageRepository.deleteAll().block();
    }

    /**
     * Tests saving messages and retrieving them by producer ID.
     * <p>
     * Tests on: findByProducerId(ObjectId producerId)
     * Expected: Successfully retrieves messages -- only those from the specified producer.
     */
    @Test
    void testFindByProducerId() {
        logger.info("Testing findByProducerId in container-based environment...");

        ObjectId producerIdA = new ObjectId();
        ObjectId producerIdB = new ObjectId();  // separate producer to test filtering

        Message msgA1 = new Message();
        msgA1.setMessageId(new ObjectId());
        msgA1.setProducerId(producerIdA);
        msgA1.setContent(TEST_MESSAGE_CONTENT);
        msgA1.setTimestamp(Instant.now());

        Message msgA2 = new Message();
        msgA2.setMessageId(new ObjectId());
        msgA2.setProducerId(producerIdA);
        msgA2.setContent(TEST_MESSAGE_CONTENT);
        msgA2.setTimestamp(Instant.now());

        Message msgB = new Message();
        msgB.setMessageId(new ObjectId());
        msgB.setProducerId(producerIdB);
        msgB.setContent(TEST_MESSAGE_CONTENT);
        msgB.setTimestamp(Instant.now());

        // insert all messages
        Mono<Void> insertAll =
                messageRepository.save(msgA1)
                        .then(messageRepository.save(msgA2))
                        .then(messageRepository.save(msgB))
                        .then();

        Flux<Message> testFlux = insertAll.thenMany(messageRepository.findByProducerId(producerIdA));

        // verify the content of the retrieved messages
        StepVerifier.create(testFlux)
                .expectSubscription() // ensure the subscription happens
                .assertNext(message -> {
                    assertNotNull(message, "Message should not be null.");
                    assertEquals(producerIdA, message.getProducerId(), "Producer ID should match producer A.");
                    assertEquals(TEST_MESSAGE_CONTENT, message.getContent(), "Message content should match.");
                })
                .assertNext(message -> {
                    assertNotNull(message, "Message should not be null.");
                    assertEquals(producerIdA, message.getProducerId(), "Producer ID should match producer A.");
                    assertEquals(TEST_MESSAGE_CONTENT, message.getContent(), "Message content should match.");
                })
                .verifyComplete();

        logger.info("testFindByProducerId returned expected messages for ProducerA only.");
    }

    /**
     * Tests saving a message and retrieving it by its ID.
     * <p>
     * Tests on: save(Message msg) and findById(String messageId)
     * Expected: Successfully retrieves the saved message by its ID.
     */
    @Test
    void testSaveMessageAndRetrieveById() {
        logger.info("Testing saveMessageAndRetrieveById...");

        Message message = new Message();
        message.setMessageId(new ObjectId());
        message.setProducerId(new ObjectId());
        message.setContent(TEST_MESSAGE_CONTENT);
        message.setTimestamp(Instant.now());

        Mono<Message> saveOperation = messageRepository.save(message);
        Mono<Message> findOperation = saveOperation.then(
                messageRepository.findById(message.getMessageId().toHexString()));

        StepVerifier.create(findOperation)
                .assertNext(found -> {
                    assertNotNull(found, "Message should exist after save operation");
                    assertEquals(TEST_MESSAGE_CONTENT, found.getContent(), "Content should match saved message");
                })
                .verifyComplete();

        logger.info("testSaveMessageAndRetrieveById completed successfully.");
    }

    /**
     * Tests getting a message from a non-existent producer ID.
     * <p>
     * Tests on: findByProducerId(ObjectId producerId)
     * Expected: Verifies that no messages are emitted (empty).
     */
    @Test
    void testFindByNonExistentProducerId() {
        logger.info("Testing findByNonExistentProducerId...");

        // collect results into a list for synchronous assertions
        List<Message> messages = messageRepository.findByProducerId(new ObjectId())
                .collectList()
                .defaultIfEmpty(List.of())
                .blockOptional()     // wrap in Optional to avoid unboxing risks
                .orElseThrow(() -> new IllegalStateException("Unexpected null result"));

        // Assertions on the collected list
        assertNotNull(messages, "Messages list should not be null.");
        assertTrue(messages.isEmpty(), "Expected no messages for a non-existent producer ID.");

        logger.info("testFindByNonExistentProducerId completed successfully.");
    }
}
