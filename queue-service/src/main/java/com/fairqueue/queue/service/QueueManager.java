package com.fairqueue.queue.service;

import com.fairqueue.common.model.QueueEntry;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Service
public class QueueManager {
    
    private static final Logger logger = LoggerFactory.getLogger(QueueManager.class);
    private static final String QUEUE_KEY_PREFIX = "queue:";
    private static final String QUEUE_ENTRY_PREFIX = "qentry:";
    private static final Duration HEARTBEAT_TIMEOUT = Duration.ofMinutes(2);
    
    private final RedisTemplate<String, String> redisTemplate;
    private final ObjectMapper objectMapper;
    
    public QueueManager(RedisTemplate<String, String> redisTemplate,
                       ObjectMapper objectMapper,
                       MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.objectMapper = objectMapper;
        
        // Register queue depth gauge
        Gauge.builder("fairqueue.queue.depth", this, QueueManager::getTotalQueueDepth)
                .description("Total number of users in all queues")
                .register(meterRegistry);
    }
    
    public QueueEntry addToQueue(String userId, String eventId, String queueToken) {
        String queueKey = QUEUE_KEY_PREFIX + eventId;
        String entryKey = QUEUE_ENTRY_PREFIX + eventId + ":" + userId;
        
        // Check if user already in queue
        QueueEntry existingEntry = getQueueEntry(eventId, userId);
        if (existingEntry != null) {
            logger.info("User already in queue: userId={}, eventId={}, position={}", 
                       userId, eventId, existingEntry.getPosition());
            return existingEntry;
        }
        
        Instant now = Instant.now();
        QueueEntry entry = QueueEntry.builder()
                .userId(userId)
                .eventId(eventId)
                .queueToken(queueToken)
                .joinTimestamp(now)
                .lastHeartbeat(now)
                .penaltyScore(0)
                .build();
        
        try {
            // Store entry
            String entryJson = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(entryKey, entryJson);
            
            // Add to sorted set (score = timestamp + penalty)
            double score = calculateScore(entry);
            redisTemplate.opsForZSet().add(queueKey, userId, score);
            
            // Get position
            long position = getPosition(eventId, userId);
            entry.setPosition(position);
            
            logger.info("User added to queue: userId={}, eventId={}, position={}", 
                       userId, eventId, position);
            
            return entry;
            
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize queue entry", e);
            throw new IllegalStateException("Failed to add to queue", e);
        }
    }
    
    public QueueEntry refreshHeartbeat(String eventId, String queueToken) {
        // Extract userId from token or find by token
        QueueEntry entry = findEntryByToken(eventId, queueToken);
        
        if (entry == null) {
            throw new IllegalArgumentException("Queue entry not found");
        }
        
        entry.updateHeartbeat();
        saveQueueEntry(entry);
        
        long position = getPosition(eventId, entry.getUserId());
        entry.setPosition(position);
        
        logger.debug("Heartbeat refreshed: userId={}, eventId={}, position={}", 
                    entry.getUserId(), eventId, position);
        
        return entry;
    }
    
    public QueueEntry getStatus(String queueToken) {
        // Find entry by token across all events (for simplicity, iterate)
        // In production, maintain token->entry mapping
        String userId = extractUserIdFromToken(queueToken);
        String eventId = extractEventIdFromToken(queueToken);
        
        QueueEntry entry = getQueueEntry(eventId, userId);
        if (entry != null) {
            long position = getPosition(eventId, userId);
            entry.setPosition(position);
        }
        
        return entry;
    }
    
    public List<QueueEntry> getNextBatch(String eventId, int batchSize) {
        String queueKey = QUEUE_KEY_PREFIX + eventId;
        
        Set<String> userIds = redisTemplate.opsForZSet().range(queueKey, 0, batchSize - 1);
        
        if (userIds == null || userIds.isEmpty()) {
            return Collections.emptyList();
        }
        
        List<QueueEntry> entries = new ArrayList<>();
        long position = 1;
        
        for (String userId : userIds) {
            QueueEntry entry = getQueueEntry(eventId, userId);
            if (entry != null) {
                entry.setPosition(position++);
                entries.add(entry);
            }
        }
        
        return entries;
    }
    
    public void removeFromQueue(String eventId, String userId) {
        String queueKey = QUEUE_KEY_PREFIX + eventId;
        String entryKey = QUEUE_ENTRY_PREFIX + eventId + ":" + userId;
        
        redisTemplate.opsForZSet().remove(queueKey, userId);
        redisTemplate.delete(entryKey);
        
        logger.info("User removed from queue: userId={}, eventId={}", userId, eventId);
    }
    
    public void penalizeUser(String eventId, String userId) {
        QueueEntry entry = getQueueEntry(eventId, userId);
        if (entry != null) {
            entry.incrementPenalty();
            saveQueueEntry(entry);
            
            // Update score in sorted set
            String queueKey = QUEUE_KEY_PREFIX + eventId;
            double newScore = calculateScore(entry);
            redisTemplate.opsForZSet().add(queueKey, userId, newScore);
            
            logger.info("User penalized: userId={}, eventId={}, penaltyScore={}", 
                       userId, eventId, entry.getPenaltyScore());
        }
    }
    
    public long getQueueDepth(String eventId) {
        String queueKey = QUEUE_KEY_PREFIX + eventId;
        Long size = redisTemplate.opsForZSet().size(queueKey);
        return size != null ? size : 0;
    }
    
    private double getTotalQueueDepth() {
        // For metrics - scan all queue keys
        Set<String> keys = redisTemplate.keys(QUEUE_KEY_PREFIX + "*");
        if (keys == null) return 0;
        
        return keys.stream()
                .mapToLong(key -> {
                    Long size = redisTemplate.opsForZSet().size(key);
                    return size != null ? size : 0;
                })
                .sum();
    }
    
    private long getPosition(String eventId, String userId) {
        String queueKey = QUEUE_KEY_PREFIX + eventId;
        Long rank = redisTemplate.opsForZSet().rank(queueKey, userId);
        return rank != null ? rank + 1 : -1;
    }
    
    private QueueEntry getQueueEntry(String eventId, String userId) {
        String entryKey = QUEUE_ENTRY_PREFIX + eventId + ":" + userId;
        String entryJson = redisTemplate.opsForValue().get(entryKey);
        
        if (entryJson == null) {
            return null;
        }
        
        try {
            return objectMapper.readValue(entryJson, QueueEntry.class);
        } catch (JsonProcessingException e) {
            logger.error("Failed to deserialize queue entry", e);
            return null;
        }
    }
    
    private void saveQueueEntry(QueueEntry entry) {
        String entryKey = QUEUE_ENTRY_PREFIX + entry.getEventId() + ":" + entry.getUserId();
        try {
            String entryJson = objectMapper.writeValueAsString(entry);
            redisTemplate.opsForValue().set(entryKey, entryJson);
        } catch (JsonProcessingException e) {
            logger.error("Failed to serialize queue entry", e);
            throw new IllegalStateException("Failed to save queue entry", e);
        }
    }
    
    private QueueEntry findEntryByToken(String eventId, String queueToken) {
        String userId = extractUserIdFromToken(queueToken);
        return getQueueEntry(eventId, userId);
    }
    
    private double calculateScore(QueueEntry entry) {
        // Score = timestamp (seconds) + penalty weight
        long timestampScore = entry.getJoinTimestamp().getEpochSecond();
        int penaltyWeight = entry.getPenaltyScore() * 3600; // Each penalty adds 1 hour
        return timestampScore + penaltyWeight;
    }
    
    private String extractUserIdFromToken(String token) {
        // This should use TokenService to parse JWT
        // For now, simplified
        return "user"; // Placeholder
    }
    
    private String extractEventIdFromToken(String token) {
        // This should use TokenService to parse JWT
        return "event"; // Placeholder
    }
}
