package com.fairqueue.admission.service;

import com.fairqueue.admission.repository.AdmissionPassRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class PassCleanupService {
    
    private static final Logger logger = LoggerFactory.getLogger(PassCleanupService.class);
    
    private final AdmissionPassRepository passRepository;
    
    public PassCleanupService(AdmissionPassRepository passRepository) {
        this.passRepository = passRepository;
    }
    
    @Scheduled(fixedRate = 300000) // Every 5 minutes
    @Transactional
    public void cleanupExpiredPasses() {
        Instant cutoff = Instant.now().minusSeconds(3600); // Keep for 1 hour after expiry
        
        try {
            passRepository.deleteByExpiresAtBefore(cutoff);
            logger.debug("Cleaned up expired admission passes");
        } catch (Exception e) {
            logger.error("Failed to cleanup expired passes", e);
        }
    }
}
