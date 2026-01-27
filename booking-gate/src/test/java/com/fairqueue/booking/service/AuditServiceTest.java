package com.fairqueue.booking.service;

import com.fairqueue.booking.entity.AuditLog;
import com.fairqueue.booking.repository.AuditLogRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuditServiceTest {

    @InjectMocks
    private AuditService auditService;

    @Mock
    private AuditLogRepository auditLogRepository;

    @Test
    void logBooking_shouldSaveAuditLog() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        String action = "BOOKING_APPROVED";
        String passId = "pass-789";
        String details = "Successfully booked";

        // When
        auditService.logBooking(userId, eventId, action, passId, details);

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertEquals(userId, savedLog.getUserId());
        assertEquals(eventId, savedLog.getEventId());
        assertEquals(action, savedLog.getAction());
        assertEquals(passId, savedLog.getPassId());
        assertEquals(details, savedLog.getDetails());
        assertNotNull(savedLog.getTimestamp());
    }

    @Test
    void logBooking_shouldHandleNullPassId() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        String action = "INVALID_PASS";
        String details = "Pass validation failed";

        // When
        auditService.logBooking(userId, eventId, action, null, details);

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertNull(savedLog.getPassId());
        assertEquals(action, savedLog.getAction());
    }

    @Test
    void logBooking_shouldSetTimestamp() {
        // Given
        String userId = "user123";
        String eventId = "event456";
        String action = "BOOKING_APPROVED";
        String passId = "pass-789";
        String details = "Success";

        long beforeTimestamp = System.currentTimeMillis();

        // When
        auditService.logBooking(userId, eventId, action, passId, details);

        long afterTimestamp = System.currentTimeMillis();

        // Then
        ArgumentCaptor<AuditLog> captor = ArgumentCaptor.forClass(AuditLog.class);
        verify(auditLogRepository).save(captor.capture());

        AuditLog savedLog = captor.getValue();
        assertNotNull(savedLog.getTimestamp());
        assertTrue(savedLog.getTimestamp().toEpochMilli() >= beforeTimestamp);
        assertTrue(savedLog.getTimestamp().toEpochMilli() <= afterTimestamp);
    }

    @Test
    void logBooking_shouldHandleRepositoryException() {
        // Given
        when(auditLogRepository.save(any(AuditLog.class)))
                .thenThrow(new RuntimeException("Database error"));

        // When/Then - Should not throw exception
        assertDoesNotThrow(() -> {
            auditService.logBooking("user123", "event456", "BOOKING_APPROVED", "pass-789", "Success");
        });
    }

    @Test
    void logBooking_shouldSaveMultipleLogs() {
        // When
        auditService.logBooking("user1", "event1", "ACTION1", "pass1", "details1");
        auditService.logBooking("user2", "event2", "ACTION2", "pass2", "details2");
        auditService.logBooking("user3", "event3", "ACTION3", "pass3", "details3");

        // Then
        verify(auditLogRepository, times(3)).save(any(AuditLog.class));
    }
}
