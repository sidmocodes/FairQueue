package com.fairqueue.booking.service;

import com.fairqueue.booking.dto.ValidatePassResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

@Service
public class BookingGateService {
    
    private static final Logger logger = LoggerFactory.getLogger(BookingGateService.class);
    private static final String USED_PASS_KEY_PREFIX = "used:pass:";
    
    private final RedisTemplate<String, String> redisTemplate;
    private final RestTemplate restTemplate;
    private final AuditService auditService;
    private final String admissionServiceUrl;
    private final Counter bookingAttemptCounter;
    private final Counter bookingSuccessCounter;
    
    public BookingGateService(RedisTemplate<String, String> redisTemplate,
                             RestTemplate restTemplate,
                             AuditService auditService,
                             @Value("${fairqueue.admission-service.url:http://localhost:8082}") String admissionServiceUrl,
                             MeterRegistry meterRegistry) {
        this.redisTemplate = redisTemplate;
        this.restTemplate = restTemplate;
        this.auditService = auditService;
        this.admissionServiceUrl = admissionServiceUrl;
        this.bookingAttemptCounter = Counter.builder("fairqueue.booking.attempt")
                .description("Number of booking attempts")
                .register(meterRegistry);
        this.bookingSuccessCounter = Counter.builder("fairqueue.booking.success")
                .description("Number of successful bookings")
                .register(meterRegistry);
    }
    
    public boolean enterBooking(String admissionPass, String userId, String eventId) {
        bookingAttemptCounter.increment();
        
        // Check if pass already used (Redis for quick lookup)
        String usedKey = USED_PASS_KEY_PREFIX + admissionPass;
        
        // Atomic operation: set if not exists
        Boolean wasSet = redisTemplate.opsForValue().setIfAbsent(
            usedKey, 
            userId, 
            Duration.ofHours(24)
        );
        
        if (Boolean.FALSE.equals(wasSet)) {
            logger.warn("Admission pass already used: passId={}, userId={}", 
                       admissionPass, userId);
            auditService.log(userId, eventId, "BOOKING_REJECTED", admissionPass, 
                           "Pass already used");
            return false;
        }
        
        // Validate with Admission Service
        try {
            String url = admissionServiceUrl + "/admission/validate/" + admissionPass;
            var response = restTemplate.getForObject(url, ValidatePassResponse.class);
            
            if (response == null || !response.isValid()) {
                logger.warn("Invalid admission pass: passId={}", admissionPass);
                
                // Rollback Redis entry
                redisTemplate.delete(usedKey);
                
                auditService.log(userId, eventId, "BOOKING_REJECTED", admissionPass, 
                               "Invalid pass");
                return false;
            }
            
            // Success - log and return
            bookingSuccessCounter.increment();
            
            logger.info("Booking access granted: userId={}, eventId={}, passId={}", 
                       userId, eventId, admissionPass);
            
            auditService.log(userId, eventId, "BOOKING_APPROVED", admissionPass, 
                           "Access granted");
            
            return true;
            
        } catch (Exception e) {
            logger.error("Failed to validate admission pass", e);
            
            // Rollback on error
            redisTemplate.delete(usedKey);
            
            auditService.log(userId, eventId, "BOOKING_ERROR", admissionPass, 
                           "Validation error: " + e.getMessage());
            
            return false;
        }
    }
}
