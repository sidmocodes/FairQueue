package com.fairqueue.booking.service;

import com.fairqueue.booking.entity.AuditLog;
import com.fairqueue.booking.repository.AuditLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Service
public class AuditService {
    
    private static final Logger logger = LoggerFactory.getLogger(AuditService.class);
    
    private final AuditLogRepository auditLogRepository;
    
    public AuditService(AuditLogRepository auditLogRepository) {
        this.auditLogRepository = auditLogRepository;
    }
    
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void log(String userId, String eventId, String action, String passId, String details) {
        try {
            AuditLog log = AuditLog.builder()
                    .userId(userId)
                    .eventId(eventId)
                    .action(action)
                    .passId(passId)
                    .details(details)
                    .timestamp(Instant.now())
                    .correlationId(MDC.get("correlationId"))
                    .build();
            
            auditLogRepository.save(log);
            
            logger.debug("Audit log created: userId={}, action={}", userId, action);
        } catch (Exception e) {
            logger.error("Failed to create audit log", e);
        }
    }
}
