package com.fairqueue.gateway.controller;

import com.fairqueue.common.dto.JoinQueueRequest;
import com.fairqueue.common.dto.JoinQueueResponse;
import com.fairqueue.gateway.service.GatewayService;
import com.fairqueue.gateway.service.RateLimiterService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/queue")
public class GatewayController {
    
    private static final Logger logger = LoggerFactory.getLogger(GatewayController.class);
    
    private final GatewayService gatewayService;
    private final RateLimiterService rateLimiterService;
    private final Counter joinRequestCounter;
    
    public GatewayController(GatewayService gatewayService,
                           RateLimiterService rateLimiterService,
                           MeterRegistry meterRegistry) {
        this.gatewayService = gatewayService;
        this.rateLimiterService = rateLimiterService;
        this.joinRequestCounter = Counter.builder("fairqueue.gateway.join.requests")
                .description("Number of join queue requests")
                .register(meterRegistry);
    }
    
    @PostMapping("/join")
    public ResponseEntity<?> joinQueue(@Valid @RequestBody JoinQueueRequest request) {
        logger.info("Join queue request received: userId={}, eventId={}", 
                   request.getUserId(), request.getEventId());
        
        joinRequestCounter.increment();
        
        // Rate limiting
        if (!rateLimiterService.allowRequest(request.getUserId(), request.getEventId())) {
            logger.warn("Rate limit exceeded for userId={}, eventId={}", 
                       request.getUserId(), request.getEventId());
            return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                    .body("Rate limit exceeded. Please try again later.");
        }
        
        JoinQueueResponse response = gatewayService.processJoinRequest(request);
        logger.info("Join queue processed successfully: userId={}, position={}", 
                   request.getUserId(), response.getPosition());
        
        return ResponseEntity.ok(response);
    }
}
