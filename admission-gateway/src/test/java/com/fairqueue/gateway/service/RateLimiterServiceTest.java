package com.fairqueue.gateway.service;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class RateLimiterServiceTest {

    private RateLimiterService rateLimiterService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        rateLimiterService = new RateLimiterService(redisTemplate, meterRegistry);
    }

    @Test
    void allowRequest_shouldAllowFirstRequest() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        when(valueOperations.increment(anyString())).thenReturn(1L);

        // When
        boolean allowed = rateLimiterService.allowRequest(userId, eventId);

        // Then
        assertTrue(allowed);
        verify(valueOperations).increment("ratelimit:user123:event456");
        verify(redisTemplate).expire("ratelimit:user123:event456", Duration.ofMinutes(1));
    }

    @Test
    void allowRequest_shouldAllowRequestUnderLimit() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        when(valueOperations.increment(anyString())).thenReturn(5L);

        // When
        boolean allowed = rateLimiterService.allowRequest(userId, eventId);

        // Then
        assertTrue(allowed);
    }

    @Test
    void allowRequest_shouldAllowRequestAtLimit() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        when(valueOperations.increment(anyString())).thenReturn(10L);

        // When
        boolean allowed = rateLimiterService.allowRequest(userId, eventId);

        // Then
        assertTrue(allowed);
    }

    @Test
    void allowRequest_shouldRejectRequestOverLimit() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        when(valueOperations.increment(anyString())).thenReturn(11L);

        // When
        boolean allowed = rateLimiterService.allowRequest(userId, eventId);

        // Then
        assertFalse(allowed);
    }

    @Test
    void allowRequest_shouldRejectRequestWayOverLimit() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        when(valueOperations.increment(anyString())).thenReturn(100L);

        // When
        boolean allowed = rateLimiterService.allowRequest(userId, eventId);

        // Then
        assertFalse(allowed);
    }

    @Test
    void allowRequest_shouldSetExpiryOnlyForFirstRequest() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        when(valueOperations.increment(anyString())).thenReturn(3L);

        // When
        rateLimiterService.allowRequest(userId, eventId);

        // Then
        verify(redisTemplate, never()).expire(anyString(), any(Duration.class));
    }

    @Test
    void allowRequest_shouldIncrementCounterWhenRateLimitExceeded() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        when(valueOperations.increment(anyString())).thenReturn(11L);

        // When
        rateLimiterService.allowRequest(userId, eventId);

        // Then
        Counter counter = meterRegistry.find("fairqueue.ratelimit.exceeded").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void allowRequest_shouldUseDifferentKeysForDifferentUsers() {
        // Given
        when(valueOperations.increment(anyString())).thenReturn(1L);

        // When
        rateLimiterService.allowRequest("user1", "event1");
        rateLimiterService.allowRequest("user2", "event1");

        // Then
        verify(valueOperations).increment("ratelimit:user1:event1");
        verify(valueOperations).increment("ratelimit:user2:event1");
    }

    @Test
    void allowRequest_shouldUseDifferentKeysForDifferentEvents() {
        // Given
        when(valueOperations.increment(anyString())).thenReturn(1L);

        // When
        rateLimiterService.allowRequest("user1", "event1");
        rateLimiterService.allowRequest("user1", "event2");

        // Then
        verify(valueOperations).increment("ratelimit:user1:event1");
        verify(valueOperations).increment("ratelimit:user1:event2");
    }
}
