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
import java.util.Set;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class QueueManagerTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private ObjectMapper objectMapper;
    private MeterRegistry meterRegistry;
    private QueueManager queueManager;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        meterRegistry = new SimpleMeterRegistry();

        lenient().when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        lenient().when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);

        queueManager = new QueueManager(redisTemplate, objectMapper, meterRegistry);
    }

    @Test
    void addToQueue_shouldAddNewUserToQueue() throws JsonProcessingException {
        // Arrange
        String userId = "user123";
        String eventId = "event456";
        String queueToken = "token789";
        
        when(valueOperations.get(anyString())).thenReturn(null); // No existing entry
        when(zSetOperations.rank(anyString(), anyString())).thenReturn(4L); // Position 5

        // Act
        QueueEntry result = queueManager.addToQueue(userId, eventId, queueToken);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(eventId, result.getEventId());
        assertEquals(queueToken, result.getQueueToken());
        assertEquals(5L, result.getPosition()); // Rank 4 = Position 5
        assertEquals(0, result.getPenaltyScore());
        assertNotNull(result.getJoinTimestamp());
        assertNotNull(result.getLastHeartbeat());

        verify(valueOperations).set(eq("qentry:event456:user123"), anyString());
        verify(zSetOperations).add(eq("queue:event456"), eq("user123"), anyDouble());
    }

    @Test
    void addToQueue_shouldReturnExistingEntryIfUserAlreadyInQueue() throws JsonProcessingException {
        // Arrange
        String userId = "user123";
        String eventId = "event456";
        String queueToken = "token789";
        
        QueueEntry existingEntry = QueueEntry.builder()
                .userId(userId)
                .eventId(eventId)
                .queueToken("oldToken")
                .joinTimestamp(Instant.now().minusSeconds(300))
                .lastHeartbeat(Instant.now().minusSeconds(10))
                .penaltyScore(0)
                .build();
        
        String existingJson = objectMapper.writeValueAsString(existingEntry);
        when(valueOperations.get("qentry:event456:user123")).thenReturn(existingJson);

        // Act
        QueueEntry result = queueManager.addToQueue(userId, eventId, queueToken);

        // Assert
        assertNotNull(result);
        assertEquals(userId, result.getUserId());
        assertEquals(0L, result.getPosition()); // Position not set in returned existing entry
        
        // Should not add again
        verify(valueOperations, never()).set(anyString(), anyString());
        verify(zSetOperations, never()).add(anyString(), anyString(), anyDouble());
    }

    @Test
    void getQueueDepth_shouldReturnCorrectSize() {
        // Arrange
        String eventId = "event456";
        when(zSetOperations.size("queue:event456")).thenReturn(42L);

        // Act
        long depth = queueManager.getQueueDepth(eventId);

        // Assert
        assertEquals(42L, depth);
    }

    @Test
    void getQueueDepth_shouldReturnZeroWhenNoQueue() {
        // Arrange
        String eventId = "event456";
        when(zSetOperations.size("queue:event456")).thenReturn(null);

        // Act
        long depth = queueManager.getQueueDepth(eventId);

        // Assert
        assertEquals(0L, depth);
    }

    @Test
    void removeFromQueue_shouldRemoveUserFromQueue() {
        // Arrange
        String userId = "user123";
        String eventId = "event456";

        // Act
        queueManager.removeFromQueue(eventId, userId);

        // Assert
        verify(zSetOperations).remove("queue:event456", "user123");
        verify(redisTemplate).delete("qentry:event456:user123");
    }

    @Test
    void penalizeUser_shouldIncrementPenaltyScore() throws JsonProcessingException {
        // Arrange
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

        // Act
        queueManager.penalizeUser(eventId, userId);

        // Assert
        verify(valueOperations).set(eq("qentry:event456:user123"), anyString());
        verify(zSetOperations).add(eq("queue:event456"), eq("user123"), anyDouble());
    }

    @Test
    void refreshHeartbeat_shouldThrowExceptionWhenEntryNotFound() {
        // Arrange
        String eventId = "event456";
        String queueToken = "invalidToken";
        
        when(valueOperations.get(anyString())).thenReturn(null);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            queueManager.refreshHeartbeat(eventId, queueToken);
        });
    }

    @Test
    void getNextBatch_shouldReturnEmptyListWhenNoEntries() {
        // Arrange
        String eventId = "event456";
        when(zSetOperations.range("queue:event456", 0, 9)).thenReturn(Set.of());

        // Act
        var result = queueManager.getNextBatch(eventId, 10);

        // Assert
        assertNotNull(result);
        assertTrue(result.isEmpty());
    }

    @Test
    void getNextBatch_shouldReturnRequestedNumberOfEntries() throws JsonProcessingException {
        // Arrange
        String eventId = "event456";
        Set<String> userIds = Set.of("user1", "user2", "user3");
        
        when(zSetOperations.range("queue:event456", 0, 2)).thenReturn(userIds);
        
        for (String userId : userIds) {
            QueueEntry entry = QueueEntry.builder()
                    .userId(userId)
                    .eventId(eventId)
                    .queueToken("token_" + userId)
                    .joinTimestamp(Instant.now())
                    .lastHeartbeat(Instant.now())
                    .penaltyScore(0)
                    .build();
            String json = objectMapper.writeValueAsString(entry);
            when(valueOperations.get("qentry:" + eventId + ":" + userId)).thenReturn(json);
        }

        // Act
        var result = queueManager.getNextBatch(eventId, 3);

        // Assert
        assertNotNull(result);
        assertEquals(3, result.size());
    }
}
