package com.fairqueue.queue.service;

import com.fairqueue.common.model.QueueEntry;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.ZSetOperations;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class HeartbeatMonitorTest {

    private HeartbeatMonitor heartbeatMonitor;

    @Mock
    private QueueManager queueManager;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private ZSetOperations<String, String> zSetOperations;

    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper();
        objectMapper.findAndRegisterModules();
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(redisTemplate.opsForZSet()).thenReturn(zSetOperations);
        
        heartbeatMonitor = new HeartbeatMonitor(queueManager, redisTemplate, objectMapper);
    }

    @Test
    void checkHeartbeats_shouldApplyPenaltyToStaleEntries() throws Exception {
        // Given
        String eventId = "event456";
        Set<String> queueKeys = Set.of("queue:event456");
        when(redisTemplate.keys("queue:*")).thenReturn(queueKeys);

        Set<String> users = Set.of("user1", "user2");
        when(zSetOperations.range("queue:event456", 0, -1)).thenReturn(users);

        // Create stale entry (last heartbeat > 2 minutes ago)
        QueueEntry staleEntry = QueueEntry.builder()
                .userId("user1")
                .eventId(eventId)
                .queueToken("token1")
                .joinTimestamp(Instant.now().minus(Duration.ofMinutes(10)))
                .lastHeartbeat(Instant.now().minus(Duration.ofMinutes(3)))
                .penaltyScore(0)
                .build();

        // Create fresh entry
        QueueEntry freshEntry = QueueEntry.builder()
                .userId("user2")
                .eventId(eventId)
                .queueToken("token2")
                .joinTimestamp(Instant.now())
                .lastHeartbeat(Instant.now())
                .penaltyScore(0)
                .build();

        when(valueOperations.get("qentry:event456:user1"))
                .thenReturn(objectMapper.writeValueAsString(staleEntry));
        when(valueOperations.get("qentry:event456:user2"))
                .thenReturn(objectMapper.writeValueAsString(freshEntry));

        // When
        heartbeatMonitor.checkHeartbeats();

        // Then
        verify(queueManager).applyPenalty(eventId, "user1");
        verify(queueManager, never()).applyPenalty(eventId, "user2");
    }

    @Test
    void checkHeartbeats_shouldHandleMultipleQueues() throws Exception {
        // Given
        Set<String> queueKeys = Set.of("queue:event1", "queue:event2");
        when(redisTemplate.keys("queue:*")).thenReturn(queueKeys);

        when(zSetOperations.range(anyString(), anyLong(), anyLong())).thenReturn(Set.of());

        // When
        heartbeatMonitor.checkHeartbeats();

        // Then
        verify(zSetOperations).range("queue:event1", 0, -1);
        verify(zSetOperations).range("queue:event2", 0, -1);
    }

    @Test
    void checkHeartbeats_shouldHandleNullEntryGracefully() throws Exception {
        // Given
        Set<String> queueKeys = Set.of("queue:event456");
        when(redisTemplate.keys("queue:*")).thenReturn(queueKeys);

        Set<String> users = Set.of("user1");
        when(zSetOperations.range("queue:event456", 0, -1)).thenReturn(users);
        when(valueOperations.get("qentry:event456:user1")).thenReturn(null);

        // When
        heartbeatMonitor.checkHeartbeats();

        // Then
        verify(queueManager, never()).applyPenalty(anyString(), anyString());
    }

    @Test
    void checkHeartbeats_shouldNotPenalizeUserWithRecentHeartbeat() throws Exception {
        // Given
        Set<String> queueKeys = Set.of("queue:event456");
        when(redisTemplate.keys("queue:*")).thenReturn(queueKeys);

        Set<String> users = Set.of("user1");
        when(zSetOperations.range("queue:event456", 0, -1)).thenReturn(users);

        QueueEntry recentEntry = QueueEntry.builder()
                .userId("user1")
                .eventId("event456")
                .queueToken("token1")
                .joinTimestamp(Instant.now())
                .lastHeartbeat(Instant.now().minus(Duration.ofSeconds(30)))
                .penaltyScore(0)
                .build();

        when(valueOperations.get("qentry:event456:user1"))
                .thenReturn(objectMapper.writeValueAsString(recentEntry));

        // When
        heartbeatMonitor.checkHeartbeats();

        // Then
        verify(queueManager, never()).applyPenalty(anyString(), anyString());
    }
}
