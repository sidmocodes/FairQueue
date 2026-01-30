package com.fairqueue.booking.service;

import com.fairqueue.booking.dto.ValidatePassResponse;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingGateServiceTest {

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AuditService auditService;

    private MeterRegistry meterRegistry;
    private BookingGateService bookingGateService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);

        bookingGateService = new BookingGateService(
                redisTemplate,
                restTemplate,
                auditService,
                "http://localhost:8082",
                meterRegistry
        );
    }

    @Test
    void enterBooking_shouldGrantAccessForValidPass() {
        // Arrange
        String admissionPass = "validPass123";
        String userId = "user123";
        String eventId = "event456";

        ValidatePassResponse validResponse = new ValidatePassResponse(true);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(restTemplate.getForObject(anyString(), eq(ValidatePassResponse.class)))
                .thenReturn(validResponse);

        // Act
        boolean result = bookingGateService.enterBooking(admissionPass, userId, eventId);

        // Assert
        assertTrue(result);
        
        verify(valueOperations).setIfAbsent(
                eq("used:pass:validPass123"),
                eq(userId),
                any(Duration.class)
        );
        verify(auditService).log(eq(userId), eq(eventId), eq("BOOKING_APPROVED"), 
                                eq(admissionPass), anyString());
        
        Counter attemptCounter = meterRegistry.find("fairqueue.booking.attempt").counter();
        Counter successCounter = meterRegistry.find("fairqueue.booking.success").counter();
        assertNotNull(attemptCounter);
        assertNotNull(successCounter);
        assertEquals(1.0, attemptCounter.count());
        assertEquals(1.0, successCounter.count());
    }

    @Test
    void enterBooking_shouldRejectAlreadyUsedPass() {
        // Arrange
        String admissionPass = "usedPass123";
        String userId = "user123";
        String eventId = "event456";

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        // Act
        boolean result = bookingGateService.enterBooking(admissionPass, userId, eventId);

        // Assert
        assertFalse(result);
        
        verify(auditService).log(eq(userId), eq(eventId), eq("BOOKING_REJECTED"), 
                                eq(admissionPass), contains("already used"));
        verify(restTemplate, never()).getForObject(anyString(), any());
        
        Counter attemptCounter = meterRegistry.find("fairqueue.booking.attempt").counter();
        Counter successCounter = meterRegistry.find("fairqueue.booking.success").counter();
        assertEquals(1.0, attemptCounter.count());
        assertEquals(0.0, successCounter.count());
    }

    @Test
    void enterBooking_shouldRejectInvalidPass() {
        // Arrange
        String admissionPass = "invalidPass123";
        String userId = "user123";
        String eventId = "event456";

        ValidatePassResponse invalidResponse = new ValidatePassResponse(false);

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(restTemplate.getForObject(anyString(), eq(ValidatePassResponse.class)))
                .thenReturn(invalidResponse);

        // Act
        boolean result = bookingGateService.enterBooking(admissionPass, userId, eventId);

        // Assert
        assertFalse(result);
        
        verify(redisTemplate).delete("used:pass:invalidPass123");
        verify(auditService).log(eq(userId), eq(eventId), eq("BOOKING_REJECTED"), 
                                eq(admissionPass), contains("Invalid pass"));
    }

    @Test
    void enterBooking_shouldHandleNullResponseFromValidation() {
        // Arrange
        String admissionPass = "pass123";
        String userId = "user123";
        String eventId = "event456";

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(restTemplate.getForObject(anyString(), eq(ValidatePassResponse.class)))
                .thenReturn(null);

        // Act
        boolean result = bookingGateService.enterBooking(admissionPass, userId, eventId);

        // Assert
        assertFalse(result);
        
        verify(redisTemplate).delete("used:pass:pass123");
        verify(auditService).log(eq(userId), eq(eventId), eq("BOOKING_REJECTED"), 
                                eq(admissionPass), contains("Invalid pass"));
    }

    @Test
    void enterBooking_shouldRollbackOnValidationError() {
        // Arrange
        String admissionPass = "pass123";
        String userId = "user123";
        String eventId = "event456";

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);
        when(restTemplate.getForObject(anyString(), eq(ValidatePassResponse.class)))
                .thenThrow(new RestClientException("Service unavailable"));

        // Act
        boolean result = bookingGateService.enterBooking(admissionPass, userId, eventId);

        // Assert
        assertFalse(result);
        
        verify(redisTemplate).delete("used:pass:pass123");
        verify(auditService).log(eq(userId), eq(eventId), eq("BOOKING_ERROR"), 
                                eq(admissionPass), contains("Validation error"));
    }

    @Test
    void enterBooking_shouldIncrementAttemptCounterOnEveryCall() {
        // Arrange
        String admissionPass = "pass123";
        String userId = "user123";
        String eventId = "event456";

        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false);

        // Act
        bookingGateService.enterBooking(admissionPass, userId, eventId);
        bookingGateService.enterBooking(admissionPass, userId, eventId);
        bookingGateService.enterBooking(admissionPass, userId, eventId);

        // Assert
        Counter attemptCounter = meterRegistry.find("fairqueue.booking.attempt").counter();
        assertEquals(3.0, attemptCounter.count());
    }
}
