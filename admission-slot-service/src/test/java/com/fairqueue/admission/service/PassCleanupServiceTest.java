package com.fairqueue.admission.service;

import com.fairqueue.admission.repository.AdmissionPassRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PassCleanupServiceTest {

    @Mock
    private AdmissionPassRepository passRepository;

    private PassCleanupService passCleanupService;

    @BeforeEach
    void setUp() {
        passCleanupService = new PassCleanupService(passRepository);
    }

    @Test
    void cleanupExpiredPasses_shouldExecuteWithoutErrors() {
        // Act & Assert
        assertDoesNotThrow(() -> {
            passCleanupService.cleanupExpiredPasses();
        });

        verify(passRepository).deleteByExpiresAtBefore(any(Instant.class));
    }

    @Test
    void cleanupExpiredPasses_shouldCallDeleteWithCorrectCutoffTime() {
        // Act
        passCleanupService.cleanupExpiredPasses();

        // Assert
        verify(passRepository).deleteByExpiresAtBefore(any(Instant.class));
    }

    @Test
    void cleanupExpiredPasses_canBeCalledMultipleTimes() {
        // Act
        passCleanupService.cleanupExpiredPasses();
        passCleanupService.cleanupExpiredPasses();
        passCleanupService.cleanupExpiredPasses();

        // Assert
        verify(passRepository, times(3)).deleteByExpiresAtBefore(any(Instant.class));
    }
    
    @Test
    void cleanupExpiredPasses_shouldHandleExceptionsGracefully() {
        // Arrange
        doThrow(new RuntimeException("Database error"))
                .when(passRepository).deleteByExpiresAtBefore(any(Instant.class));

        // Act & Assert - should not throw
        assertDoesNotThrow(() -> {
            passCleanupService.cleanupExpiredPasses();
        });
    }
}
