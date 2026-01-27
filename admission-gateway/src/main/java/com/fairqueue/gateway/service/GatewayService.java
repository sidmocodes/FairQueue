package com.fairqueue.gateway.service;

import com.fairqueue.common.dto.JoinQueueRequest;
import com.fairqueue.common.dto.JoinQueueResponse;
import com.fairqueue.common.util.TokenService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class GatewayService {
    
    private static final Logger logger = LoggerFactory.getLogger(GatewayService.class);
    
    private final TokenService tokenService;
    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;
    private final String queueServiceUrl;
    
    public GatewayService(TokenService tokenService,
                         RedisTemplate<String, String> redisTemplate,
                         RestTemplate restTemplate,
                         @Value("${fairqueue.queue-service.url:http://queue-service:8081}") String queueServiceUrl) {
        this.tokenService = tokenService;
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.queueServiceUrl = queueServiceUrl;
    }
    
    public JoinQueueResponse processJoinRequest(JoinQueueRequest request) {
        // Generate queue token
        String queueToken = tokenService.generateQueueToken(
            request.getUserId(), 
            request.getEventId()
        );
        
        logger.debug("Generated queue token for userId={}, eventId={}", 
                    request.getUserId(), request.getEventId());
        
        // Forward to Queue Service
        try {
            String url = queueServiceUrl + "/internal/queue/add";
            
            AddToQueueRequest queueRequest = new AddToQueueRequest(
                request.getUserId(),
                request.getEventId(),
                queueToken
            );
            
            ResponseEntity<JoinQueueResponse> response = restTemplate.postForEntity(
                url, 
                queueRequest, 
                JoinQueueResponse.class
            );
            
            if (response.getBody() != null) {
                return response.getBody();
            }
            
            throw new IllegalStateException("Empty response from queue service");
            
        } catch (Exception e) {
            logger.error("Failed to add user to queue: userId={}, eventId={}", 
                        request.getUserId(), request.getEventId(), e);
            throw new IllegalStateException("Failed to join queue: " + e.getMessage());
        }
    }
    
    // Internal DTO for queue service communication
    private static class AddToQueueRequest {
        public String userId;
        public String eventId;
        public String queueToken;
        
        public AddToQueueRequest(String userId, String eventId, String queueToken) {
            this.userId = userId;
            this.eventId = eventId;
            this.queueToken = queueToken;
        }
    }
}
