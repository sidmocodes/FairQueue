package com.fairqueue.queue.controller;

import com.fairqueue.common.dto.JoinQueueResponse;
import com.fairqueue.common.dto.QueueStatusResponse;
import com.fairqueue.common.model.QueueEntry;
import com.fairqueue.queue.service.QueueManager;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/internal/queue")
public class InternalQueueController {
    
    private static final Logger logger = LoggerFactory.getLogger(InternalQueueController.class);
    
    private final QueueManager queueManager;
    
    public InternalQueueController(QueueManager queueManager) {
        this.queueManager = queueManager;
    }
    
    @PostMapping("/add")
    public ResponseEntity<JoinQueueResponse> addToQueue(@RequestBody AddToQueueRequest request) {
        logger.info("Adding user to queue: userId={}, eventId={}", 
                   request.userId, request.eventId);
        
        QueueEntry entry = queueManager.addToQueue(
            request.userId,
            request.eventId,
            request.queueToken
        );
        
        JoinQueueResponse response = JoinQueueResponse.builder()
                .queueToken(request.queueToken)
                .position(entry.getPosition())
                .estimatedWaitTimeSeconds(calculateWaitTime(entry.getPosition()))
                .message("Successfully joined queue")
                .build();
        
        return ResponseEntity.ok(response);
    }
    
    private long calculateWaitTime(long position) {
        // Simplified calculation: assume 1 user admitted per second
        return position * 60; // 1 minute per position
    }
    
    public static class AddToQueueRequest {
        public String userId;
        public String eventId;
        public String queueToken;
    }
}
