package org.ac.cst8277.chard.matt.litter.repository;

import org.ac.cst8277.chard.matt.litter.model.Subscription;
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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

/**
 * Integration test for SubscriptionRepository using Testcontainers for a real MongoDB instance.
 */
@ExtendWith(SpringExtension.class)
@DataMongoTest
@Testcontainers
@DirtiesContext
@Import({})
class SubscriptionRepositoryIntegrationTest {

    private static final Logger logger = LoggerFactory.getLogger(SubscriptionRepositoryIntegrationTest.class);

    @Container
    private static final MongoDBContainer mongoDBContainer = new MongoDBContainer("mongo:8.0");
    @Autowired
    private SubscriptionRepository subscriptionRepository;

    @DynamicPropertySource
    static void setMongoDBProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.data.mongodb.uri", mongoDBContainer::getReplicaSetUrl);
    }

    @BeforeAll
    static void beforeAll() {
        logger.info("Starting up MongoDB container for SubscriptionRepositoryIntegrationTest...");
        mongoDBContainer.start();
    }

    @AfterAll
    static void afterAll() {
        logger.info("Stopping MongoDB container for SubscriptionRepositoryIntegrationTest...");
        mongoDBContainer.stop();
    }

    @BeforeEach
    void setUp() {
        logger.debug("Preparing test data for SubscriptionRepositoryIntegrationTest.");
        subscriptionRepository.deleteAll().block();
    }

    @AfterEach
    void tearDown() {
        logger.debug("Cleaning up after test in SubscriptionRepositoryIntegrationTest.");
        subscriptionRepository.deleteAll().block();
    }

    /**
     * Tests saving a subscription and finding it by subscriberId and producerId.
     * <p>
     * Tests on: save(Subscription sub), getSubscriptionBySubscriberIdAndProducerId(ObjectId subId, ObjectId prodId)
     * Expected: The subscription is returned correctly.
     */
    @Test
    void testSaveAndFindBySubscriberAndProducer() {
        logger.info("Testing saveAndFindBySubscriberAndProducer...");

        ObjectId subscriberId = new ObjectId();
        ObjectId producerId = new ObjectId();

        Subscription subscription = new Subscription();
        subscription.setSubscriberId(subscriberId);
        subscription.setProducerId(producerId);

        // perform the save and find operations
        Mono<Void> saveOperation = subscriptionRepository.save(subscription).then();
        Mono<Subscription> findOperation = saveOperation.then(
                subscriptionRepository.getSubscriptionBySubscriberIdAndProducerId(subscriberId, producerId)
        );

        // verify the subscription was saved and found
        StepVerifier.create(findOperation)
                .assertNext(found -> {
                    assertNotNull(found.getSubscriptionId(), "Subscription ID should be auto-generated");
                    assertEquals(subscriberId, found.getSubscriberId(), "SubscriberId should match");
                    assertEquals(producerId, found.getProducerId(), "ProducerId should match");
                })
                .verifyComplete();
        logger.info("testSaveAndFindBySubscriberAndProducer completed successfully.");
    }

    /**
     * Tests saving multiple subscriptions and retrieving them by subscriberId.
     * <p>
     * Tests on: findBySubscriberId(ObjectId subscriberId)
     * Expected: Successfully retrieves subscriptions -- only those from the specified subscriber.
     */
    @Test
    void testGetAllForSubscriber() {
        logger.info("Testing findBySubscriberId with multiple subscriptions...");

        ObjectId subA = new ObjectId();
        ObjectId subB = new ObjectId(); // separate sub ID to ensure filtering

        Subscription subscription1 = new Subscription();
        subscription1.setSubscriberId(subA);
        subscription1.setProducerId(new ObjectId());

        Subscription subscription2 = new Subscription();
        subscription2.setSubscriberId(subA);
        subscription2.setProducerId(new ObjectId());

        Subscription subscription3 = new Subscription();
        subscription3.setSubscriberId(subB);
        subscription3.setProducerId(new ObjectId());

        Flux<Subscription> insertFlux = subscriptionRepository
                .save(subscription1)
                .thenMany(subscriptionRepository.save(subscription2))
                .thenMany(subscriptionRepository.save(subscription3));

        // expect 2 subscriptions for findFluxSubA, check that they match the subscriber ID
        StepVerifier.create(insertFlux.thenMany(subscriptionRepository.getSubscriptionsBySubscriberId(subA)))
                .assertNext(sub -> assertEquals(subA, sub.getSubscriberId(), "SubscriberId should match"))
                .assertNext(sub -> assertEquals(subA, sub.getSubscriberId(), "SubscriberId should match"))
                .verifyComplete();

        logger.info("testFindBySubscriberId verified filtering by subscriberId.");
    }
}
