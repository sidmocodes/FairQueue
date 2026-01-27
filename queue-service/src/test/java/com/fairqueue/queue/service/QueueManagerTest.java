package com.fairqueue.queue.service;

import com.fairqueue.common.model.QueueEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueManagerTest {

    private QueueManager queueManager;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        meterRegistry = new SimpleMeterRegistry();
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        
        queueManager = new QueueManager(redisTemplate, objectMapper, meterRegistry);
    }

    @Test
    void addToQueue_shouldCreateNewEntry() throws JsonProcessingException {
        // Given
        String userId = "user123";
        String eventId = "event456";
        String queueToken = "token789";

        when(valueOperations.get(anyString())).thenReturn(null); // No existing entry
        when(zSetOperations.rank(anyString(), anyString())).thenReturn(0L);

        // When
        QueueEntry result = queueManager.addToQueue(userId, eventId, queueToken);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(eventId, result.getEventId());
        assertEquals(queueToken, result.getQueueToken());
        assertEquals(0, result.getPenaltyScore());
        assertNotNull(result.getJoinTimestamp());
        assertNotNull(result.getLastHeartbeat());

        verify(valueOperations).set(eq("qentry:event456:user123"), anyString());
        verify(zSetOperations).add(eq("queue:event456"), eq(userId), anyDouble());
    }

    @Test
    void addToQueue_shouldReturnExistingEntryIfUserAlreadyInQueue() throws JsonProcessingException {
        // Given
        String userId = "user123";
        String eventId = "event456";
        String queueToken = "token789";

        QueueEntry existingEntry = QueueEntry.builder()
                .userId(userId)
                .eventId(eventId)
                .queueToken("oldToken")
                .joinTimestamp(Instant.now())
                .lastHeartbeat(Instant.now())
                .penaltyScore(0)
                .position(5L)
                .build();

        String existingJson = objectMapper.writeValueAsString(existingEntry);
        when(valueOperations.get("qentry:event456:user123")).thenReturn(existingJson);
        when(zSetOperations.rank("queue:event456", userId)).thenReturn(4L);

        // When
        QueueEntry result = queueManager.addToQueue(userId, eventId, queueToken);

        // Then
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(eventId, result.getEventId());

        // Should not add again
        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void addToQueue_shouldCalculateCorrectPosition() throws JsonProcessingException {
        // Given
        String userId = "user123";
        String eventId = "event456";
        String queueToken = "token789";

        when(valueOperations.get(anyString())).thenReturn(null);
        when(zSetOperations.rank("queue:event456", userId)).thenReturn(9L); // 10th position (0-indexed)

        // When
        QueueEntry result = queueManager.addToQueue(userId, eventId, queueToken);

        // Then
        assertEquals(10L, result.getPosition()); // Should be rank + 1
    }

    @Test
    void removeFromQueue_shouldDeleteEntryAndRemoveFromSortedSet() {
        // Given
        String userId = "user123";
        String eventId = "event456";

        // When
        queueManager.removeFromQueue(eventId, userId);

        // Then
        verify(valueOperations).getAndDelete("qentry:event456:user123");
        verify(zSetOperations).remove("queue:event456", userId);
    }

    @Test
    void applyPenalty_shouldIncreaseScoreInSortedSet() throws JsonProcessingException {
        // Given
        String userId = "user123";
        String eventId = "event456";

        QueueEntry entry = QueueEntry.builder()
                .userId(userId)
                .eventId(eventId)
                .queueToken("token")
                .joinTimestamp(Instant.now())
                .lastHeartbeat(Instant.now())
                .penaltyScore(0)
                .build();

        String entryJson = objectMapper.writeValueAsString(entry);
        when(valueOperations.get("qentry:event456:user123")).thenReturn(entryJson);

        // When
        queueManager.applyPenalty(eventId, userId);

        // Then
        verify(valueOperations).set(eq("qentry:event456:user123"), anyString());
        verify(zSetOperations).add(eq("queue:event456"), eq(userId), anyDouble());
    }

    @Test
    void getTopUsers_shouldReturnCorrectNumberOfUsers() {
        // Given
        String eventId = "event456";
        int limit = 10;

        Set<String> mockUsers = Set.of("user1", "user2", "user3");
        when(zSetOperations.range("queue:event456", 0, 9)).thenReturn(mockUsers);

        // When
        Set<String> result = queueManager.getTopUsers(eventId, limit);

        // Then
        assertEquals(mockUsers, result);
        verify(zSetOperations).range("queue:event456", 0, 9);
    }

    @Test
    void getQueueDepth_shouldReturnCorrectCount() {
        // Given
        String eventId = "event456";
        when(zSetOperations.size("queue:event456")).thenReturn(42L);

        // When
        long depth = queueManager.getQueueDepth(eventId);

        // Then
        assertEquals(42L, depth);
    }
}
