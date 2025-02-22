package org.ac.cst8277.chard.matt.litter.service;

import org.ac.cst8277.chard.matt.litter.model.Subscription;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.repository.SubscriptionRepository;
import org.bson.types.ObjectId;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * Unit test class for SubscriptionService.
 * <p>
 * This class contains tests for subscribing, unsubscribing,
 * and retrieving subscriptions, ensuring business logic behaves as expected.
 */
class SubscriptionServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(SubscriptionServiceTest.class);

    // valid test data
    private static final ObjectId TEST_SUBSCRIBER_ID = new ObjectId();
    private static final ObjectId TEST_PRODUCER_ID = new ObjectId();
    private static final String TEST_SUBSCRIBER_USERNAME = "ImASubscriber";
    private static final String TEST_PRODUCER_USERNAME = "ImAProducer";

    // error messages (mimicking the style used in SubscriptionService)
    private static final String USER_ALREADY_SUBSCRIBED_MESSAGE = "User is already subscribed.";
    private static final String NO_SUBSCRIPTION_FOUND_MESSAGE = "No subscription found for the given users.";
    private static final String USER_NOT_SUBSCRIBER_MESSAGE = "User is not a subscriber.";
    private static final String PRODUCER_USERNAME_EMPTY_MESSAGE = "Producer username must not be null or empty.";

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserManagementService userManagementService;

    @InjectMocks
    private SubscriptionService subscriptionService;

    private AutoCloseable closeable;

    /**
     * Initializes mocks before each test.
     */
    @BeforeEach
    void setUp() {
        logger.debug("Setting up mocks for SubscriptionServiceTest...");
        closeable = MockitoAnnotations.openMocks(this);
        logger.debug("Mocks initialized successfully.");
    }

    /**
     * Closes mocks after each test.
     */
    @AfterEach
    void tearDown() {
        try {
            closeable.close();
            logger.debug("Mocks and resources cleaned up.");
        } catch (Exception e) {
            logger.error("Error closing resources during teardown", e);
        }
        logger.debug("Mocks closed for SubscriptionServiceTest.");
    }

    /**
     * Tests subscribing when the subscription does not exist.
     * <p>
     * Tests on: subscribe(JwtClaimAccessor jwt, String producerUsername)
     * Expected: Successfully creates a new subscription
     */
    @Test
    void testSubscribeNewSubscription_CreatesSubscription() {
        logger.info("Testing subscribe with new subscription...");

        // create test subscriber & producer
        User subscriber = new User();
        subscriber.setId(TEST_SUBSCRIBER_ID);
        subscriber.setUsername(TEST_SUBSCRIBER_USERNAME);
        JwtClaimAccessor subscriberJwt = () -> Map.of("sub", TEST_SUBSCRIBER_USERNAME);

        User producer = new User();
        producer.setId(TEST_PRODUCER_ID);
        producer.setUsername(TEST_PRODUCER_USERNAME);

        // mock user fetches
        when(userManagementService.getUserByJwt(subscriberJwt)).thenReturn(Mono.just(subscriber));
        when(userManagementService.getUserByUsername(TEST_PRODUCER_USERNAME)).thenReturn(Mono.just(producer));

        // mock repository: no existing subscription
        when(subscriptionRepository.findBySubscriberIdAndProducerId(TEST_SUBSCRIBER_ID, TEST_PRODUCER_ID))
                .thenReturn(Mono.empty());

        // mock saving subscription
        when(subscriptionRepository.save(any(Subscription.class)))
                .thenAnswer(invocation -> {
                    Subscription sub = invocation.getArgument(0);
                    sub.setSubscriptionId(new ObjectId());
                    return Mono.just(sub);
                });

        // expect new subscription
        StepVerifier.create(subscriptionService.subscribe(subscriberJwt, TEST_PRODUCER_USERNAME))
                .assertNext(subscription -> {
                    assertNotNull(subscription.getSubscriptionId(), "Subscription ID should not be null");
                    assertEquals(TEST_SUBSCRIBER_ID, subscription.getSubscriberId(), "Subscriber ID should match");
                    assertEquals(TEST_PRODUCER_ID, subscription.getProducerId(), "Producer ID should match");
                })
                .verifyComplete();

        verify(subscriptionRepository).save(any(Subscription.class));
        logger.info("Subscribe test with new subscription passed.");
    }

    /**
     * Tests subscribing when the subscription already exists.
     * <p>
     * Tests on: subscribe(JwtClaimAccessor jwt, String producerUsername)
     * Expected: IllegalArgumentException
     */
    @Test
    void testSubscribeAlreadySubscribed_ThrowsError() {
        logger.info("Testing subscribe with existing subscription...");

        User subscriber = new User();
        subscriber.setId(TEST_SUBSCRIBER_ID);
        subscriber.setUsername(TEST_SUBSCRIBER_USERNAME);
        JwtClaimAccessor subscriberJwt = () -> Map.of("sub", TEST_SUBSCRIBER_USERNAME);

        User producer = new User();
        producer.setId(TEST_PRODUCER_ID);

        when(userManagementService.getUserByJwt(subscriberJwt)).thenReturn(Mono.just(subscriber));
        when(userManagementService.getUserByUsername(TEST_PRODUCER_USERNAME)).thenReturn(Mono.just(producer));

        // mock existing subscription
        Subscription existingSub = new Subscription();
        existingSub.setSubscriberId(TEST_SUBSCRIBER_ID);
        existingSub.setProducerId(TEST_PRODUCER_ID);
        when(subscriptionRepository.findBySubscriberIdAndProducerId(TEST_SUBSCRIBER_ID, TEST_PRODUCER_ID))
                .thenReturn(Mono.just(existingSub));

        // expect error
        StepVerifier.create(subscriptionService.subscribe(subscriberJwt, TEST_PRODUCER_USERNAME))
                .expectErrorMatches(err ->
                        err instanceof IllegalArgumentException
                                && Objects.equals(USER_ALREADY_SUBSCRIBED_MESSAGE, err.getMessage()))
                .verify();

        verify(subscriptionRepository, never()).save(any(Subscription.class));
        logger.info("Subscribe test with existing subscription threw the correct error.");
    }

    /**
     * Tests subscribing with null or empty producer username.
     * <p>
     * Tests on: subscribe(JwtClaimAccessor jwt, String producerUsername)
     * Expected: IllegalArgumentException
     */
    @Test
    void testSubscribeEmptyProducerUsername_ThrowsError() {
        logger.info("Testing subscribe with null/empty producer username...");

        JwtClaimAccessor mockJwt = () -> Map.of("sub", TEST_SUBSCRIBER_USERNAME);

        // with "", expect error
        StepVerifier.create(subscriptionService.subscribe(mockJwt, ""))
                .expectNextCount(0L) // no onNext
                .expectErrorMatches(err ->
                        err instanceof IllegalArgumentException
                                && Objects.equals(PRODUCER_USERNAME_EMPTY_MESSAGE, err.getMessage()))
                .verify();

        // with null, expect same error
        StepVerifier.create(subscriptionService.subscribe(mockJwt, null))
                .expectNextCount(0L) // no onNext
                .expectErrorMatches(err ->
                        err instanceof IllegalArgumentException
                                && Objects.equals(PRODUCER_USERNAME_EMPTY_MESSAGE, err.getMessage()))
                .verify();

        verifyNoInteractions(subscriptionRepository);
        logger.info("Subscribe test with empty producer username handled error gracefully.");
    }

    /**
     * Tests unsubscribing from an existing subscription record.
     * <p>
     * Tests on: unsubscribe(JwtClaimAccessor jwt, String producerUsername)
     * Expected: Successfully removes the subscription
     */
    @Test
    void testUnsubscribeExistingSubscription_RemovesSubscription() {
        logger.info("Testing unsubscribe with existing subscription...");

        User subscriber = new User();
        subscriber.setId(TEST_SUBSCRIBER_ID);
        subscriber.setUsername(TEST_SUBSCRIBER_USERNAME);
        JwtClaimAccessor subscriberJwt = () -> Map.of("sub", TEST_SUBSCRIBER_USERNAME);

        User producer = new User();
        producer.setId(TEST_PRODUCER_ID);

        Subscription sub = new Subscription();
        sub.setSubscriberId(TEST_SUBSCRIBER_ID);
        sub.setProducerId(TEST_PRODUCER_ID);

        // mocks
        when(userManagementService.getUserByJwt(subscriberJwt)).thenReturn(Mono.just(subscriber));
        when(userManagementService.getUserByUsername(TEST_PRODUCER_USERNAME)).thenReturn(Mono.just(producer));
        when(subscriptionRepository.findBySubscriberIdAndProducerId(TEST_SUBSCRIBER_ID, TEST_PRODUCER_ID))
                .thenReturn(Mono.just(sub));
        when(subscriptionRepository.delete(sub)).thenReturn(Mono.empty());

        // expect completion
        StepVerifier.create(subscriptionService.unsubscribe(subscriberJwt, TEST_PRODUCER_USERNAME))
                .verifyComplete();

        verify(subscriptionRepository).delete(sub);
        logger.info("Unsubscribe test with existing subscription passed.");
    }

    /**
     * Tests unsubscribing when no matching subscription is found.
     * <p>
     * Tests on: unsubscribe(JwtClaimAccessor jwt, String producerUsername)
     * Expected: IllegalArgumentException
     */
    @Test
    void testUnsubscribeNoMatching_SubscriptionThrowsError() {
        logger.info("Testing unsubscribe with no matching subscription...");

        User subscriber = new User();
        subscriber.setId(TEST_SUBSCRIBER_ID);
        subscriber.setUsername(TEST_SUBSCRIBER_USERNAME);
        JwtClaimAccessor subscriberJwt = () -> Map.of("sub", TEST_SUBSCRIBER_USERNAME);

        User producer = new User();
        producer.setId(TEST_PRODUCER_ID);

        when(userManagementService.getUserByJwt(subscriberJwt)).thenReturn(Mono.just(subscriber));
        when(userManagementService.getUserByUsername(TEST_PRODUCER_USERNAME)).thenReturn(Mono.just(producer));
        when(subscriptionRepository.findBySubscriberIdAndProducerId(TEST_SUBSCRIBER_ID, TEST_PRODUCER_ID))
                .thenReturn(Mono.empty());

        // expect error
        StepVerifier.create(subscriptionService.unsubscribe(subscriberJwt, TEST_PRODUCER_USERNAME))
                .expectErrorMatches(err ->
                        err instanceof IllegalArgumentException
                                && Objects.equals(NO_SUBSCRIPTION_FOUND_MESSAGE, err.getMessage()))
                .verify();

        verify(subscriptionRepository, never()).delete(any());
        logger.info("Unsubscribe test with no matching subscription threw the correct error.");
    }

    /**
     * Tests retrieving subscriptions for a user with ROLE_SUBSCRIBER.
     * <p>
     * Test on: getSubscriptionsForUser(JwtClaimAccessor jwt)
     * Expected: Successfully retrieves subscriptions for the user
     */
    @Test
    void testGetSubscriptionsForUser_HasRoleSubscriber() {
        logger.info("Testing getSubscriptionsForUser with valid subscriber role...");

        User subscriber = new User();
        subscriber.setId(TEST_SUBSCRIBER_ID);
        subscriber.setUsername(TEST_SUBSCRIBER_USERNAME);
        subscriber.setRoles(List.of(User.DB_USER_ROLE_SUBSCRIBER_NAME));
        JwtClaimAccessor subscriberJwt = () -> Map.of("sub", TEST_SUBSCRIBER_USERNAME);

        Subscription sub1 = new Subscription();
        sub1.setSubscriberId(TEST_SUBSCRIBER_ID);
        Subscription sub2 = new Subscription();
        sub2.setSubscriberId(TEST_SUBSCRIBER_ID);

        // mock
        when(userManagementService.getUserByJwt(subscriberJwt)).thenReturn(Mono.just(subscriber));
        when(subscriptionRepository.findBySubscriberId(TEST_SUBSCRIBER_ID)).thenReturn(Flux.just(sub1, sub2));

        // expect flux of subscriptions
        StepVerifier.create(subscriptionService.getSubscriptionsForUser(subscriberJwt))
                .expectNext(sub1, sub2)
                .verifyComplete();

        verify(subscriptionRepository).findBySubscriberId(TEST_SUBSCRIBER_ID);
        logger.info("getSubscriptionsForUser with subscriber role test passed.");
    }

    /**
     * Tests retrieving subscriptions for a user without ROLE_SUBSCRIBER.
     * <p>
     * Test on: getSubscriptionsForUser(JwtClaimAccessor jwt)
     * Expected: IllegalArgumentException
     */
    @Test
    void testGetSubscriptionsForUser_NotSubscriber_ThrowsError() {
        logger.info("Testing getSubscriptionsForUser with non-subscriber role...");

        // mock non-subscriber
        User nonSubscriber = new User();
        nonSubscriber.setId(TEST_SUBSCRIBER_ID);
        nonSubscriber.setUsername(TEST_SUBSCRIBER_USERNAME);
        nonSubscriber.setRoles(List.of(User.DB_USER_ROLE_PRODUCER_NAME));
        JwtClaimAccessor mockJwt = () -> Map.of("sub", TEST_SUBSCRIBER_USERNAME);
        when(userManagementService.getUserByJwt(mockJwt)).thenReturn(Mono.just(nonSubscriber));

        // expect error
        StepVerifier.create(subscriptionService.getSubscriptionsForUser(mockJwt))
                .expectErrorMatches(err ->
                        err instanceof IllegalArgumentException
                                && Objects.equals(USER_NOT_SUBSCRIBER_MESSAGE, err.getMessage()))
                .verify();

        verifyNoInteractions(subscriptionRepository);
        logger.info("getSubscriptionsForUser with non-subscriber role threw correct error.");
    }
}
