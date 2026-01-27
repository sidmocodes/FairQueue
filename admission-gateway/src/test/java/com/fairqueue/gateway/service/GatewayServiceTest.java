package com.fairqueue.gateway.service;

import com.fairqueue.common.dto.JoinQueueRequest;
import com.fairqueue.common.dto.JoinQueueResponse;
import com.fairqueue.common.util.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class GatewayServiceTest {

    private GatewayService gatewayService;

    @Mock
    private TokenService tokenService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private RestTemplate restTemplate;

    private static final String QUEUE_SERVICE_URL = "http://localhost:8081";

    @BeforeEach
    void setUp() {
        gatewayService = new GatewayService(tokenService, redisTemplate, restTemplate, QUEUE_SERVICE_URL);
    }

    @Test
    void processJoinRequest_shouldGenerateTokenAndForwardToQueueService() {
        // Given
        JoinQueueRequest request = new JoinQueueRequest();
        request.setUserId("user123");
        request.setEventId("event456");

        String expectedToken = "generated.queue.token";
        JoinQueueResponse expectedResponse = new JoinQueueResponse();
        expectedResponse.setQueueToken(expectedToken);
        expectedResponse.setPosition(42);

        when(tokenService.generateQueueToken("user123", "event456")).thenReturn(expectedToken);
        when(restTemplate.postForEntity(anyString(), any(), eq(JoinQueueResponse.class)))
                .thenReturn(ResponseEntity.ok(expectedResponse));

        // When
        JoinQueueResponse response = gatewayService.processJoinRequest(request);

        // Then
        assertNotNull(response);
        assertEquals(expectedToken, response.getQueueToken());
        assertEquals(42, response.getPosition());

        verify(tokenService).generateQueueToken("user123", "event456");
        
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).postForEntity(urlCaptor.capture(), any(), eq(JoinQueueResponse.class));
        assertEquals(QUEUE_SERVICE_URL + "/internal/queue/add", urlCaptor.getValue());
    }

    @Test
    void processJoinRequest_shouldThrowExceptionWhenQueueServiceReturnsNull() {
        // Given
        JoinQueueRequest request = new JoinQueueRequest();
        request.setUserId("user123");
        request.setEventId("event456");

        when(tokenService.generateQueueToken(anyString(), anyString())).thenReturn("token");
        when(restTemplate.postForEntity(anyString(), any(), eq(JoinQueueResponse.class)))
                .thenReturn(ResponseEntity.ok(null));

        // When/Then
        assertThrows(IllegalStateException.class, () -> {
            gatewayService.processJoinRequest(request);
        });
    }

    @Test
    void processJoinRequest_shouldHandleQueueServiceException() {
        // Given
        JoinQueueRequest request = new JoinQueueRequest();
        request.setUserId("user123");
        request.setEventId("event456");

        when(tokenService.generateQueueToken(anyString(), anyString())).thenReturn("token");
        when(restTemplate.postForEntity(anyString(), any(), eq(JoinQueueResponse.class)))
                .thenThrow(new RestClientException("Service unavailable"));

        // When/Then
        IllegalStateException exception = assertThrows(IllegalStateException.class, () -> {
            gatewayService.processJoinRequest(request);
        });
        
        assertTrue(exception.getMessage().contains("Failed to join queue"));
    }

    @Test
    void processJoinRequest_shouldGenerateUniqueTokenForEachRequest() {
        // Given
        JoinQueueRequest request = new JoinQueueRequest();
        request.setUserId("user123");
        request.setEventId("event456");

        when(tokenService.generateQueueToken(anyString(), anyString()))
                .thenReturn("token1")
                .thenReturn("token2");
        
        JoinQueueResponse mockResponse = new JoinQueueResponse();
        mockResponse.setQueueToken("token");
        when(restTemplate.postForEntity(anyString(), any(), eq(JoinQueueResponse.class)))
                .thenReturn(ResponseEntity.ok(mockResponse));

        // When
        gatewayService.processJoinRequest(request);
        gatewayService.processJoinRequest(request);

        // Then
        verify(tokenService, times(2)).generateQueueToken("user123", "event456");
    }
}
