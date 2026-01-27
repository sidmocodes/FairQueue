package com.fairqueue.gateway.service;

import com.fairqueue.common.util.TokenService;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.concurrent.TimeUnit;

@Service
public class RateLimiterService {
    
    private static final Logger logger = LoggerFactory.getLogger(RateLimiterService.class);
    private static final String RATE_LIMIT_KEY_PREFIX = "ratelimit:";
    private static final int MAX_REQUESTS_PER_MINUTE = 10;
    
    private final RedisTemplate<String, String> redisTemplate;
    private final Counter rateLimitExceededCounter;
    
    public RateLimiterService(RedisTemplate<String, String> redisTemplate, 
                             MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.rateLimitExceededCounter = Counter.builder("fairqueue.ratelimit.exceeded")
                .description("Number of rate limit exceeded events")
                .register(meterRegistry);
    }
    
    public boolean allowRequest(String userId, String eventId) {
        String key = RATE_LIMIT_KEY_PREFIX + userId + ":" + eventId;
        
        Long currentCount = redisTemplate.opsForValue().increment(key);
        
        if (currentCount == 1) {
            redisTemplate.expire(key, Duration.ofMinutes(1));
        }
        
        if (currentCount > MAX_REQUESTS_PER_MINUTE) {
            logger.warn("Rate limit exceeded for userId={}, eventId={}, count={}", 
                       userId, eventId, currentCount);
            rateLimitExceededCounter.increment();
            return false;
        }
        
        logger.debug("Request allowed for userId={}, eventId={}, count={}", 
                    userId, eventId, currentCount);
        return true;
    }
}
