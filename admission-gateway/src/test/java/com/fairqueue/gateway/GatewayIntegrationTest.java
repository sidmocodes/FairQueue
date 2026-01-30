package com.fairqueue.gateway;

import com.fairqueue.common.dto.JoinQueueRequest;
import com.fairqueue.common.dto.JoinQueueResponse;
import com.fairqueue.gateway.service.GatewayService;
import com.fairqueue.gateway.service.RateLimiterService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/**
 * Simple integration test for the Gateway Service.
 * Tests the full flow with Spring context loaded.
 */
@SpringBootTest
class GatewayIntegrationTest {

    @Autowired
    private GatewayService gatewayService;

    @Autowired
    private RateLimiterService rateLimiterService;

    @MockBean
    private RestTemplate restTemplate;

    @MockBean
    private RedisTemplate<String, Object> redisTemplate;

    @BeforeEach
    void setUp() {
        // Clear any previous state
        reset(restTemplate, redisTemplate);
    }

    /**
     * Test successful queue join flow
     */
    @Test
    void testSuccessfulQueueJoin() {
        // Arrange
        String userId = "testUser123";
        String eventId = "testEvent456";
        
        JoinQueueRequest request = new JoinQueueRequest(userId, eventId);
        
        JoinQueueResponse mockResponse = JoinQueueResponse.builder()
                .queueToken("test-token-123")
                .position(5)
                .estimatedWaitTimeSeconds(300)
                .message("You are in the queue")
                .build();

        when(restTemplate.postForEntity(anyString(), any(), eq(JoinQueueResponse.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockResponse));

        // Act
        JoinQueueResponse response = gatewayService.processJoinRequest(request);

        // Assert
        assertNotNull(response);
        assertEquals("test-token-123", response.getQueueToken());
        assertEquals(5, response.getPosition());
        assertEquals(300, response.getEstimatedWaitTimeSeconds());
        
        // Verify RestTemplate was called
        verify(restTemplate, times(1)).postForEntity(
                anyString(), 
                any(), 
                eq(JoinQueueResponse.class)
        );
    }

    /**
     * Test queue service returns null response
     */
    @Test
    void testQueueServiceNullResponse() {
        // Arrange
        String userId = "testUser123";
        String eventId = "testEvent456";
        
        JoinQueueRequest request = new JoinQueueRequest(userId, eventId);

        when(restTemplate.postForEntity(anyString(), any(), eq(JoinQueueResponse.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(null));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            gatewayService.processJoinRequest(request);
        });

        assertTrue(exception.getMessage().contains("Empty response from queue service"));
    }

    /**
     * Test queue service throws exception
     */
    @Test
    void testQueueServiceException() {
        // Arrange
        String userId = "testUser123";
        String eventId = "testEvent456";
        
        JoinQueueRequest request = new JoinQueueRequest(userId, eventId);

        when(restTemplate.postForEntity(anyString(), any(), eq(JoinQueueResponse.class)))
                .thenThrow(new RuntimeException("Queue service unavailable"));

        // Act & Assert
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            gatewayService.processJoinRequest(request);
        });

        assertTrue(exception.getMessage().contains("Failed to join queue"));
    }

    /**
     * Test multiple sequential requests from same user
     */
    @Test
    void testMultipleSequentialRequests() {
        // Arrange
        String userId = "testUser123";
        String eventId = "testEvent456";
        
        JoinQueueRequest request = new JoinQueueRequest(userId, eventId);
        
        JoinQueueResponse mockResponse1 = JoinQueueResponse.builder()
                .queueToken("token-1")
                .position(10)
                .estimatedWaitTimeSeconds(600)
                .message("You are in the queue")
                .build();
                
        JoinQueueResponse mockResponse2 = JoinQueueResponse.builder()
                .queueToken("token-2")
                .position(8)
                .estimatedWaitTimeSeconds(480)
                .message("You are in the queue")
                .build();

        when(restTemplate.postForEntity(anyString(), any(), eq(JoinQueueResponse.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockResponse1))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockResponse2));

        // Act
        JoinQueueResponse response1 = gatewayService.processJoinRequest(request);
        JoinQueueResponse response2 = gatewayService.processJoinRequest(request);

        // Assert
        assertNotNull(response1);
        assertNotNull(response2);
        assertEquals("token-1", response1.getQueueToken());
        assertEquals("token-2", response2.getQueueToken());
        assertEquals(10, response1.getPosition());
        assertEquals(8, response2.getPosition());
        
        // Verify RestTemplate was called twice
        verify(restTemplate, times(2)).postForEntity(
                anyString(), 
                any(), 
                eq(JoinQueueResponse.class)
        );
    }

    /**
     * Test with different users and events
     */
    @Test
    void testDifferentUsersAndEvents() {
        // Arrange
        JoinQueueRequest request1 = new JoinQueueRequest("user1", "event1");
        JoinQueueRequest request2 = new JoinQueueRequest("user2", "event2");
        
        JoinQueueResponse mockResponse1 = JoinQueueResponse.builder()
                .queueToken("token-user1-event1")
                .position(1)
                .estimatedWaitTimeSeconds(60)
                .message("First in queue")
                .build();
                
        JoinQueueResponse mockResponse2 = JoinQueueResponse.builder()
                .queueToken("token-user2-event2")
                .position(100)
                .estimatedWaitTimeSeconds(6000)
                .message("You are in the queue")
                .build();

        when(restTemplate.postForEntity(anyString(), any(), eq(JoinQueueResponse.class)))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockResponse1))
                .thenReturn(org.springframework.http.ResponseEntity.ok(mockResponse2));

        // Act
        JoinQueueResponse response1 = gatewayService.processJoinRequest(request1);
        JoinQueueResponse response2 = gatewayService.processJoinRequest(request2);

        // Assert
        assertNotNull(response1);
        assertNotNull(response2);
        assertEquals("token-user1-event1", response1.getQueueToken());
        assertEquals("token-user2-event2", response2.getQueueToken());
        assertNotEquals(response1.getPosition(), response2.getPosition());
    }
}
