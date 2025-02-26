package org.ac.cst8277.chard.matt.litter.service;

import org.ac.cst8277.chard.matt.litter.model.Message;
import org.ac.cst8277.chard.matt.litter.model.Subscription;
import org.ac.cst8277.chard.matt.litter.model.User;
import org.ac.cst8277.chard.matt.litter.repository.MessageRepository;
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
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.oauth2.jwt.JwtClaimAccessor;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.test.StepVerifier;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.*;

/**
 * Unit test class for MessageService.
 * <p>
 * These tests verify creation, retrieval, and deletion of messages, ensuring
 * role-based access is enforced and invalid inputs are handled gracefully.
 */
class MessageServiceTest {
    private static final Logger logger = LoggerFactory.getLogger(MessageServiceTest.class);

    // valid test constants
    private static final ObjectId TEST_PRODUCER_ID = new ObjectId();
    private static final ObjectId TEST_SUBSCRIBER_ID = new ObjectId();
    private static final ObjectId TEST_MESSAGE_ID = new ObjectId();
    private static final String TEST_MESSAGE_CONTENT = "Eyo what's up!";
    private static final String TEST_PRODUCER_USERNAME = "ImAProducer";
    private static final String TEST_SUBSCRIBER_USERNAME = "ImASubscriber";
    private static final String TEST_OTHER_USERNAME = "NameIsJeff";

    // error messages (following style from service)
    private static final String MESSAGE_NOT_FOUND_MESSAGE = "Message not found.";
    private static final String TEST_ADMIN_USERNAME = "adminUser";

    @Mock
    private MessageRepository messageRepository;

    @Mock
    private SubscriptionRepository subscriptionRepository;

    @Mock
    private UserManagementService userManagementService;

    @InjectMocks
    private MessageService messageService;

    private AutoCloseable closeable;

    /**
     * Initializes mock objects before each test.
     */
    @BeforeEach
    void setUp() {
        logger.debug("Initializing mocks for MessageServiceTest...");
        closeable = MockitoAnnotations.openMocks(this);
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
    }

    /**
     * Tests creating a message when the user has ROLE_PRODUCER.
     * <p>
     * Tests on: createMessage(JwtClaimAccessor, String)
     * Expected: Successfully creates a message.
     */
    @Test
    void testCreateMessageWithProducer_Success() {
        logger.info("Testing creating message with producer privileges...");

        // mock user with ROLE_PRODUCER
        User producerUser = new User();
        producerUser.setId(TEST_PRODUCER_ID);
        producerUser.setUsername(TEST_PRODUCER_USERNAME);
        producerUser.setRoles(List.of(User.DB_USER_ROLE_PRODUCER_NAME));
        JwtClaimAccessor jwtMock = () -> Map.of("sub", TEST_PRODUCER_USERNAME);
        when(userManagementService.getUserByJwt(jwtMock)).thenReturn(Mono.just(producerUser));

        // mock repository save operation to return a saved message with an ID
        when(messageRepository.save(any(Message.class)))
                .thenAnswer(invocation -> {
                    Message msg = invocation.getArgument(0);
                    msg.setMessageId(TEST_MESSAGE_ID);
                    return Mono.just(msg);
                });

        // act and assert
        StepVerifier.create(messageService.createMessage(jwtMock, TEST_MESSAGE_CONTENT))
                .assertNext(msg -> {
                    assertEquals(TEST_MESSAGE_ID, msg.getMessageId(), "Message ID should match");
                    assertEquals(TEST_MESSAGE_CONTENT, msg.getContent(), "Content should match");
                    assertEquals(TEST_PRODUCER_ID, msg.getProducerId(), "Producer ID should match");
                    assertNotNull(msg.getTimestamp(), "Timestamp should not be null");
                })
                .verifyComplete();

        verify(messageRepository).save(any(Message.class));
        logger.info("Create message test with producer privileges passed.");
    }

    /**
     * Tests creating a message when the user is missing the ROLE_PRODUCER role.
     * <p>
     * Tests on: createMessage(JwtClaimAccessor, String)
     * Expected: Throws IllegalArgumentException
     */
    @Test
    void testCreateMessageWithoutProducerPrivilege_ThrowsError() {
        logger.info("Testing creating message without producer permission...");

        User nonProducerUser = new User();
        nonProducerUser.setId(new ObjectId());
        nonProducerUser.setRoles(List.of(User.DB_USER_ROLE_SUBSCRIBER_NAME));
        nonProducerUser.setUsername(TEST_PRODUCER_USERNAME);
        JwtClaimAccessor nonProducerJwtMock = () -> Map.of("sub", TEST_PRODUCER_USERNAME);
        when(userManagementService.getUserByJwt(nonProducerJwtMock)).thenReturn(Mono.just(nonProducerUser));

        StepVerifier.create(messageService.createMessage(nonProducerJwtMock, TEST_MESSAGE_CONTENT))
                .verifyError(AccessDeniedException.class);

        verify(userManagementService).getUserByJwt(nonProducerJwtMock);
        verifyNoMoreInteractions(messageRepository);

        logger.info("Create message test without producer privileges threw correct error.");
    }

    /**
     * Tests creating a message with null or empty content.
     * <p>
     * Tests on: createMessage(JwtClaimAccessor, String)
     * Expected: Throws IllegalArgumentException
     */
    @Test
    void testCreateMessageEmptyContent_ThrowsError() {
        logger.info("Testing creating message with null/empty content...");

        // mock user with producer role
        User producerUser = new User();
        producerUser.setId(TEST_PRODUCER_ID);
        producerUser.setUsername(TEST_PRODUCER_USERNAME);
        producerUser.setRoles(List.of(User.DB_USER_ROLE_PRODUCER_NAME));
        JwtClaimAccessor jwtMock = () -> Map.of("sub", TEST_PRODUCER_USERNAME);
        when(userManagementService.getUserByJwt(jwtMock)).thenReturn(Mono.just(producerUser));

        // empty content
        StepVerifier.create(messageService.createMessage(jwtMock, ""))
                .verifyError(IllegalArgumentException.class);

        // expect null content
        StepVerifier.create(messageService.createMessage(jwtMock, null))
                .verifyError(IllegalArgumentException.class);

        // no message should have been saved
        verify(messageRepository, never()).save(any(Message.class));
        logger.info("Create message with empty content test threw appropriate errors.");
    }

    /**
     * Tests deleting a message if user is producer of that message or has admin role.
     * <p>
     * Tests on: deleteMessage(String id, JwtClaimAccessor)
     * Expected: Successful message deletion
     */
    @Test
    void testDeleteMessageByProducerOrAdmin_Success() {
        logger.info("Testing deleting message by valid producer or admin...");

        // set up user who is producer of message
        User producerUser = new User();
        producerUser.setId(TEST_PRODUCER_ID);
        producerUser.setUsername(TEST_PRODUCER_USERNAME);
        producerUser.setRoles(List.of(User.DB_USER_ROLE_PRODUCER_NAME));
        JwtClaimAccessor jwtMock = () -> Map.of("sub", TEST_PRODUCER_USERNAME);

        // set up admin user
        User adminUser = new User();
        adminUser.setId(new ObjectId());
        adminUser.setRoles(List.of(User.DB_USER_ROLE_ADMIN_NAME));
        adminUser.setUsername(TEST_ADMIN_USERNAME);

        Message msg = new Message();
        msg.setMessageId(TEST_MESSAGE_ID);
        msg.setProducerId(TEST_PRODUCER_ID);

        // mock user with producer role and message in repository
        when(userManagementService.getUserByJwt(jwtMock)).thenReturn(Mono.just(producerUser));
        when(messageRepository.findById(TEST_MESSAGE_ID.toHexString())).thenReturn(Mono.just(msg));
        when(messageRepository.deleteById(TEST_MESSAGE_ID.toHexString())).thenReturn(Mono.empty());

        StepVerifier.create(messageService.deleteMessage(TEST_MESSAGE_ID.toHexString(), jwtMock))
                .verifyComplete();

        verify(messageRepository).deleteById(TEST_MESSAGE_ID.toHexString());
        logger.info("Delete message by valid producer user test passed.");

        when(userManagementService.getUserByJwt(jwtMock)).thenReturn(Mono.just(adminUser));
        when(messageRepository.findById(TEST_MESSAGE_ID.toHexString())).thenReturn(Mono.just(msg));

        // reset or reuse
        StepVerifier.create(messageService.deleteMessage(TEST_MESSAGE_ID.toHexString(), jwtMock))
                .verifyComplete();

        logger.info("Delete message by admin user test passed.");
    }

    /**
     * Tests deleting a message if user is neither the original producer nor an admin.
     * <p>
     * Tests on: deleteMessage(String id, JwtClaimAccessor)
     * Expected: Throws IllegalArgumentException
     */
    @Test
    void testDeleteMessageUnauthorizedUser_ThrowsError() {
        logger.info("Testing delete message by unauthorized user...");

        // set up user
        User randomUser = new User();
        randomUser.setId(new ObjectId());
        randomUser.setRoles(List.of(User.DB_USER_ROLE_SUBSCRIBER_NAME));
        randomUser.setUsername(TEST_MESSAGE_CONTENT);
        JwtClaimAccessor jwtMock = () -> Map.of("sub", TEST_OTHER_USERNAME);

        // set up message by another producer
        Message msg = new Message();
        msg.setMessageId(TEST_MESSAGE_ID);
        msg.setProducerId(TEST_PRODUCER_ID);
        msg.setContent(TEST_MESSAGE_CONTENT);

        // mock user and message retrieval
        when(userManagementService.getUserByJwt(jwtMock)).thenReturn(Mono.just(randomUser));
        when(messageRepository.findById(TEST_MESSAGE_ID.toHexString())).thenReturn(Mono.just(msg));

        // expect error about unauthorized user
        StepVerifier.create(messageService.deleteMessage(TEST_MESSAGE_ID.toHexString(), jwtMock))
                .verifyError(AccessDeniedException.class);

        // no delete operation should have been called
        verify(messageRepository, never()).deleteById(anyString());
        logger.info("Delete message test by unauthorized user threw correct error.");
    }

    /**
     * Tests deleting a message with an invalid or nonexistent message ID.
     * <p>
     * Tests on: deleteMessage(String id, JwtClaimAccessor)
     * Expected: Throws IllegalArgumentException
     */
    @Test
    void testDeleteMessageInvalidId_ThrowsError() {
        logger.info("Testing delete message with invalid/nonexistent ID...");

        // mock user with producer role
        User producerUser = new User();
        producerUser.setId(TEST_PRODUCER_ID);
        producerUser.setUsername(TEST_PRODUCER_USERNAME);
        producerUser.setRoles(List.of(User.DB_USER_ROLE_PRODUCER_NAME));
        JwtClaimAccessor jwtMock = () -> Map.of("sub", TEST_PRODUCER_USERNAME);

        // repository returns empty
        when(userManagementService.getUserByJwt(jwtMock)).thenReturn(Mono.just(producerUser));
        when(messageRepository.findById("bogusId")).thenReturn(Mono.empty());

        // expect error about message not found
        StepVerifier.create(messageService.deleteMessage("bogusId", jwtMock))
                .expectErrorMatches(err ->
                        err instanceof IllegalArgumentException
                                && Objects.equals(MESSAGE_NOT_FOUND_MESSAGE, err.getMessage()))
                .verify();

        verify(messageRepository, never()).deleteById(anyString());
        logger.info("Delete message test with invalid ID threw correct error.");
    }

    /**
     * Tests retrieving a message by its valid ID.
     * <p>
     * Tests on: getMessageById(String id)
     * Expected: Successfully retrieves the message
     */
    @Test
    void testGetMessageById_ExistingMessageReturnsMessage() {
        logger.info("Testing getMessageById with existing message...");

        // mock message
        Message msg = new Message();
        msg.setMessageId(TEST_MESSAGE_ID);
        msg.setContent(TEST_MESSAGE_CONTENT);
        when(messageRepository.findById(TEST_MESSAGE_ID.toHexString())).thenReturn(Mono.just(msg));

        // expect message to be retrieved
        StepVerifier.create(messageService.getMessageById(TEST_MESSAGE_ID.toHexString()))
                .assertNext(retrieved -> assertEquals(TEST_MESSAGE_CONTENT, retrieved.getContent(), "Content should match"))
                .verifyComplete();

        logger.info("getMessageById test with existing message passed.");
    }

    /**
     * Tests retrieving a message by an ID that does not exist.
     * <p>
     * Tests on: getMessageById(String id)
     * Expected: Throws IllegalArgumentException
     */
    @Test
    void testGetMessageById_NonexistentId_ThrowsError() {
        logger.info("Testing getMessageById with nonexistent ID...");

        // mock empty repository response
        ObjectId madeUpId = new ObjectId();
        when(messageRepository.findById(madeUpId.toHexString())).thenReturn(Mono.empty());

        // expect IllegalArgumentException
        StepVerifier.create(messageService.getMessageById(madeUpId.toHexString()))
                .verifyError(IllegalArgumentException.class);

        verify(messageRepository).findById(madeUpId.toHexString());
        logger.info("getMessageById test with nonexistent ID threw correct error.");
    }

    /**
     * Tests retrieving all messages for a subscriber with ROLE_SUBSCRIBER.
     * <p>
     * Tests on: findAllMessagesForSubscriber(JwtClaimAccessor)
     * Expected: Successfully returns all messages from the producers subscribed to
     */
    @Test
    void testFindAllMessagesForSubscriber_SubscriberReturnsAll() {
        logger.info("Testing findAllMessagesForSubscriber with subscriber user...");

        // mock subscriber user with ROLE_SUBSCRIBER
        User subscriber = new User();
        subscriber.setId(TEST_SUBSCRIBER_ID);
        subscriber.setUsername(TEST_SUBSCRIBER_USERNAME);
        subscriber.setRoles(List.of(User.DB_USER_ROLE_SUBSCRIBER_NAME));
        JwtClaimAccessor jwt = () -> Map.of("sub", TEST_SUBSCRIBER_USERNAME);

        // mock subscription
        Subscription subscription = new Subscription();
        subscription.setSubscriberId(TEST_SUBSCRIBER_ID);
        subscription.setProducerId(TEST_PRODUCER_ID);

        // mock 2x messages
        Message msg1 = new Message();
        msg1.setMessageId(new ObjectId());
        msg1.setProducerId(TEST_PRODUCER_ID);
        msg1.setContent("Message 1");
        msg1.setTimestamp(Instant.now());

        Message msg2 = new Message();
        msg2.setMessageId(new ObjectId());
        msg2.setProducerId(TEST_PRODUCER_ID);
        msg2.setContent("Message 2");
        msg2.setTimestamp(Instant.now());

        when(userManagementService.getUserByJwt(jwt)).thenReturn(Mono.just(subscriber));
        when(subscriptionRepository.getSubscriptionsBySubscriberId(TEST_SUBSCRIBER_ID)).thenReturn(Flux.just(subscription));
        when(messageRepository.findByProducerId(TEST_PRODUCER_ID)).thenReturn(Flux.just(msg1, msg2));

        // expect messages to be retrieved
        StepVerifier.create(messageService.findAllMessagesForSubscriber(jwt))
                .expectNext(msg1, msg2)
                .verifyComplete();

        // verify interactions
        verify(userManagementService).getUserByJwt(jwt);
        verify(subscriptionRepository).getSubscriptionsBySubscriberId(TEST_SUBSCRIBER_ID);
        verify(messageRepository).findByProducerId(TEST_PRODUCER_ID);

        logger.info("findAllMessagesForSubscriber test with subscriber user passed.");
    }

    /**
     * Tests retrieving all messages for a subscriber without ROLE_SUBSCRIBER.
     * <p>
     * Tests on: findAllMessagesForSubscriber(JwtClaimAccessor)
     * Expected: Throws IllegalArgumentException
     */
    @Test
    void testFindAllMessagesForSubscriber_UserWithoutSubscriberRole_ThrowsError() {
        logger.info("Testing findAllMessagesForSubscriber with user without subscriber role...");

        // mock user without subscriber role
        User user = new User();
        user.setId(TEST_SUBSCRIBER_ID);
        user.setUsername(TEST_SUBSCRIBER_USERNAME);
        user.setRoles(List.of()); // no roles (not a subscriber)
        JwtClaimAccessor jwt = () -> Map.of("sub", TEST_SUBSCRIBER_USERNAME);
        when(userManagementService.getUserByJwt(jwt)).thenReturn(Mono.just(user));

        // expect error about missing subscriber role
        StepVerifier.create(messageService.findAllMessagesForSubscriber(jwt))
                .verifyError(AccessDeniedException.class);

        // verify interactions
        verify(userManagementService).getUserByJwt(jwt);
        verifyNoInteractions(subscriptionRepository, messageRepository);

        logger.info("findAllMessagesForSubscriber test without subscriber role threw correct error.");
    }

    /**
     * Tests retrieving all messages in the repository.
     * <p>
     * Tests on: getAllMessages()
     * Expected: All messages are returned
     */
    @Test
    void testGetAllMessages_ReturnsAllMessages() {
        logger.info("Testing getAllMessages...");

        // mock messages
        Message msg1 = new Message();
        msg1.setMessageId(new ObjectId());
        msg1.setProducerId(TEST_PRODUCER_ID);
        msg1.setContent("Message 1");
        msg1.setTimestamp(Instant.now());

        Message msg2 = new Message();
        msg2.setMessageId(new ObjectId());
        msg2.setProducerId(TEST_PRODUCER_ID);
        msg2.setContent("Message 2");
        msg2.setTimestamp(Instant.now());

        // mock repository response
        when(messageRepository.findAll()).thenReturn(Flux.just(msg1, msg2));

        // expect messages to be retrieved
        StepVerifier.create(messageService.getAllMessages())
                .expectNext(msg1)
                .expectNext(msg2)
                .verifyComplete();

        // verify interactions
        verify(messageRepository).findAll();
        logger.info("getAllMessages test passed.");
    }

    /**
     * Tests retrieving messages for a specific producer.
     * <p>
     * Tests on: getMessagesForProducer(String user)
     * Expected: All messages for the producer are returned
     */
    @Test
    void testGetMessagesForProducer_ProducerReturnsMessages() {
        logger.info("Testing getMessagesForProducer...");

        // mock producer user
        User producer = new User();
        producer.setId(TEST_PRODUCER_ID);
        producer.setUsername(TEST_PRODUCER_USERNAME);

        // mock messages
        Message msg1 = new Message();
        msg1.setMessageId(new ObjectId());
        msg1.setProducerId(TEST_PRODUCER_ID);
        msg1.setContent("Message 1");
        msg1.setTimestamp(Instant.now());

        Message msg2 = new Message();
        msg2.setMessageId(new ObjectId());
        msg2.setProducerId(TEST_PRODUCER_ID);
        msg2.setContent("Message 2");
        msg2.setTimestamp(Instant.now());

        // mock user retrieval by username and message retrieval by producer ID
        when(userManagementService.getUserByUsername(TEST_PRODUCER_USERNAME)).thenReturn(Mono.just(producer));
        when(messageRepository.findByProducerId(TEST_PRODUCER_ID)).thenReturn(Flux.just(msg1, msg2));

        // expect messages to be retrieved
        StepVerifier.create(messageService.getMessagesForProducer(TEST_PRODUCER_USERNAME))
                .expectNext(msg1, msg2)
                .verifyComplete();

        // verify interactions
        verify(userManagementService).getUserByUsername(TEST_PRODUCER_USERNAME);
        verify(messageRepository).findByProducerId(TEST_PRODUCER_ID);

        logger.info("getMessagesForProducer test passed.");
    }

    /**
     * Tests retrieving a specific message by its ID.
     * <p>
     * Tests on: getMessageById(String id)
     * Expected: Successfully retrieves the message
     */
    @Test
    void testGetMessageById_MessageFound_ReturnsMessage() {
        logger.info("Testing getMessageById with correct message ID...");

        // mock message
        Message message = new Message();
        message.setMessageId(TEST_MESSAGE_ID);
        message.setProducerId(TEST_PRODUCER_ID);
        message.setContent("Test message");
        message.setTimestamp(Instant.now());

        // mock repository response
        when(messageRepository.findById(TEST_MESSAGE_ID.toHexString())).thenReturn(Mono.just(message));

        // expect message to be retrieved
        StepVerifier.create(messageService.getMessageById(TEST_MESSAGE_ID.toHexString()))
                .expectNext(message)
                .verifyComplete();

        // verify interactions
        verify(messageRepository).findById(TEST_MESSAGE_ID.toHexString());
    }

    /**
     * Tests retrieving a message by its ID when the message does not exist.
     * <p>
     * Tests on: getMessageById(String id)
     * Expected: An error is thrown.
     */
    @Test
    void testGetMessageById_MessageNotFound_ThrowsError() {
        // mock repository response
        when(messageRepository.findById(TEST_MESSAGE_ID.toHexString())).thenReturn(Mono.empty());

        // expect error about message not found
        StepVerifier.create(messageService.getMessageById(TEST_MESSAGE_ID.toHexString()))
                .verifyError(IllegalArgumentException.class);

        // verify interaction
        verify(messageRepository).findById(TEST_MESSAGE_ID.toHexString());
    }
}
