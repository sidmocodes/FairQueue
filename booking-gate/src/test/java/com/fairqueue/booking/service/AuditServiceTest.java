package com.fairqueue.booking.service;

import com.fairqueue.booking.entity.AuditLog;
import com.fairqueue.booking.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @Mock
    private AuditLogRepository auditLogRepository;

    private AuditService auditService;

    @BeforeEach
    void setUp() {
        auditService = new AuditService(auditLogRepository);
    }

    @Test
    void log_shouldSaveAuditLogEntry() {
        // Arrange
        String userId = "user123";
        String eventId = "event456";
        String action = "BOOKING_APPROVED";
        String passId = "pass789";
        String details = "Access granted";

        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        auditService.log(userId, eventId, action, passId, details);

        // Assert
        verify(auditLogRepository).save(argThat(auditLog ->
                auditLog.getUserId().equals(userId) &&
                auditLog.getEventId().equals(eventId) &&
                auditLog.getAction().equals(action) &&
                auditLog.getPassId().equals(passId) &&
                auditLog.getDetails().equals(details) &&
                auditLog.getTimestamp() != null
        ));
    }

    @Test
    void log_shouldHandleMultipleLogEntries() {
        // Arrange
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        auditService.log("user1", "event1", "ACTION_1", "pass1", "Details 1");
        auditService.log("user2", "event2", "ACTION_2", "pass2", "Details 2");
        auditService.log("user3", "event3", "ACTION_3", "pass3", "Details 3");

        // Assert
        verify(auditLogRepository, times(3)).save(any(AuditLog.class));
    }

    @Test
    void log_shouldExecuteWithoutErrors() {
        // Arrange
        when(auditLogRepository.save(any(AuditLog.class))).thenAnswer(i -> i.getArgument(0));

        // Act & Assert
        assertDoesNotThrow(() -> {
            auditService.log("user", "event", "action", "pass", "details");
        });
    }
}
