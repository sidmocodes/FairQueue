package com.fairqueue.admission.service;

import com.fairqueue.admission.entity.AdmissionPassEntity;
import com.fairqueue.admission.entity.EventEntity;
import com.fairqueue.admission.repository.AdmissionPassRepository;
import com.fairqueue.common.dto.AdmissionClaimResponse;
import com.fairqueue.common.util.TokenService;
import io.jsonwebtoken.Claims;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

@Service
public class AdmissionService {
    
    private static final Logger logger = LoggerFactory.getLogger(AdmissionService.class);
    private static final Duration PASS_VALIDITY = Duration.ofMinutes(5);
    
    private final AdmissionPassRepository passRepository;
    private final EventService eventService;
    private final TokenService tokenService;
    private final RestTemplate restTemplate;
    private final Counter passIssuedCounter;
    
    public AdmissionService(AdmissionPassRepository passRepository,
                           EventService eventService,
                           TokenService tokenService,
                           RestTemplate restTemplate,
                           MeterRegistry meterRegistry) {
        this.passRepository = passRepository;
        this.eventService = eventService;
        this.tokenService = tokenService;
        this.restTemplate = restTemplate;
        this.passIssuedCounter = Counter.builder("fairqueue.admission.pass.issued")
                .description("Number of admission passes issued")
                .register(meterRegistry);
    }
    
    @Transactional
    public AdmissionClaimResponse claimAdmission(String queueToken) {
        // Validate queue token
        if (!tokenService.isTokenValid(queueToken)) {
            throw new IllegalArgumentException("Invalid or expired queue token");
        }
        
        Claims claims = tokenService.validateAndParse(queueToken);
        String userId = claims.getSubject();
        String eventId = claims.get("eventId", String.class);
        
        // Check if user already has a valid pass
        var existingPasses = passRepository.findByUserIdAndEventId(userId, eventId);
        for (AdmissionPassEntity pass : existingPasses) {
            if (!pass.isUsed() && pass.getExpiresAt().isAfter(Instant.now())) {
                logger.info("Returning existing admission pass: userId={}, passId={}", 
                           userId, pass.getPassId());
                return buildResponse(pass);
            }
        }
        
        // Verify event exists and is active
        EventEntity event = eventService.getEvent(eventId);
        if (!event.isActive()) {
            throw new IllegalStateException("Event is not active");
        }
        
        // Check if user is eligible (at front of queue)
        // This would call Queue Service to verify position
        // For now, simplified
        
        // Issue admission pass
        Instant now = Instant.now();
        Instant expiry = now.plus(PASS_VALIDITY);
        
        String passId = UUID.randomUUID().toString();
        
        AdmissionPassEntity pass = AdmissionPassEntity.builder()
                .passId(passId)
                .userId(userId)
                .eventId(eventId)
                .issuedAt(now)
                .expiresAt(expiry)
                .used(false)
                .build();
        
        passRepository.save(pass);
        passIssuedCounter.increment();
        
        logger.info("Admission pass issued: userId={}, eventId={}, passId={}", 
                   userId, eventId, passId);
        
        return buildResponse(pass);
    }
    
    @Transactional
    public boolean validatePass(String passId) {
        var passOpt = passRepository.findByPassId(passId);
        
        if (passOpt.isEmpty()) {
            logger.warn("Admission pass not found: passId={}", passId);
            return false;
        }
        
        AdmissionPassEntity pass = passOpt.get();
        
        if (pass.isUsed()) {
            logger.warn("Admission pass already used: passId={}, usedAt={}", 
                       passId, pass.getUsedAt());
            return false;
        }
        
        if (pass.getExpiresAt().isBefore(Instant.now())) {
            logger.warn("Admission pass expired: passId={}, expiresAt={}", 
                       passId, pass.getExpiresAt());
            return false;
        }
        
        return true;
    }
    
    @Transactional
    public void markPassAsUsed(String passId, String usedBy) {
        var passOpt = passRepository.findByPassId(passId);
        
        if (passOpt.isEmpty()) {
            throw new IllegalArgumentException("Pass not found");
        }
        
        AdmissionPassEntity pass = passOpt.get();
        pass.setUsed(true);
        pass.setUsedBy(usedBy);
        pass.setUsedAt(Instant.now());
        
        passRepository.save(pass);
        
        logger.info("Admission pass marked as used: passId={}, userId={}", 
                   passId, pass.getUserId());
    }
    
    private AdmissionClaimResponse buildResponse(AdmissionPassEntity pass) {
        long expiresInSeconds = Duration.between(Instant.now(), pass.getExpiresAt()).getSeconds();
        
        return AdmissionClaimResponse.builder()
                .admissionPass(pass.getPassId())
                .expiresInSeconds(Math.max(0, expiresInSeconds))
                .message("Admission pass issued successfully")
                .build();
    }
}
