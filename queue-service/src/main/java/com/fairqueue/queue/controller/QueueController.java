package com.fairqueue.queue.controller;

import com.fairqueue.common.dto.QueueStatusResponse;
import com.fairqueue.common.model.QueueEntry;
import com.fairqueue.common.util.TokenService;
import com.fairqueue.queue.service.QueueManager;
import io.jsonwebtoken.Claims;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.constraints.NotBlank;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/queue")
public class QueueController {
    
    private static final Logger logger = LoggerFactory.getLogger(QueueController.class);
    
    private final QueueManager queueManager;
    private final TokenService tokenService;
    private final Counter refreshCounter;
    private final Counter statusCounter;
    
    public QueueController(QueueManager queueManager,
                          TokenService tokenService,
                          MeterRegistry meterRegistry) {
        this.queueManager = queueManager;
        this.tokenService = tokenService;
        this.refreshCounter = Counter.builder("fairqueue.queue.refresh")
                .description("Number of queue refresh requests")
                .register(meterRegistry);
        this.statusCounter = Counter.builder("fairqueue.queue.status")
                .description("Number of queue status requests")
                .register(meterRegistry);
    }
    
    @PostMapping("/refresh")
    public ResponseEntity<?> refreshQueue(@RequestHeader("Authorization") String authHeader) {
        refreshCounter.increment();
        
        String token = extractToken(authHeader);
        
        if (!tokenService.isTokenValid(token)) {
            return ResponseEntity.badRequest().body("Invalid or expired token");
        }
        
        Claims claims = tokenService.validateAndParse(token);
        String eventId = claims.get("eventId", String.class);
        
        QueueEntry entry = queueManager.refreshHeartbeat(eventId, token);
        
        QueueStatusResponse response = QueueStatusResponse.builder()
                .position(entry.getPosition())
                .queueDepth(queueManager.getQueueDepth(eventId))
                .estimatedWaitTimeSeconds(entry.getPosition() * 60)
                .penaltyScore(entry.getPenaltyScore())
                .eligible(entry.getPosition() <= 100)
                .message("Queue position updated")
                .build();
        
        logger.info("Queue refreshed: userId={}, position={}", 
                   entry.getUserId(), entry.getPosition());
        
        return ResponseEntity.ok(response);
    }
    
    @GetMapping("/status")
    public ResponseEntity<?> getStatus(@RequestHeader("Authorization") String authHeader) {
        statusCounter.increment();
        
        String token = extractToken(authHeader);
        
        if (!tokenService.isTokenValid(token)) {
            return ResponseEntity.badRequest().body("Invalid or expired token");
        }
        
        Claims claims = tokenService.validateAndParse(token);
        String userId = claims.getSubject();
        String eventId = claims.get("eventId", String.class);
        
        QueueEntry entry = queueManager.getStatus(token);
        
        if (entry == null) {
            return ResponseEntity.notFound().build();
        }
        
        QueueStatusResponse response = QueueStatusResponse.builder()
                .position(entry.getPosition())
                .queueDepth(queueManager.getQueueDepth(eventId))
                .estimatedWaitTimeSeconds(entry.getPosition() * 60)
                .penaltyScore(entry.getPenaltyScore())
                .eligible(entry.getPosition() <= 100)
                .message("Current queue status")
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    private String extractToken(String authHeader) {
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return authHeader.substring(7);
        }
        throw new IllegalArgumentException("Invalid authorization header");
    }
}
