package com.fairqueue.booking.service;

import com.fairqueue.booking.dto.ValidatePassResponse;
import com.fairqueue.common.dto.BookingEnterRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.client.RestTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class BookingGateServiceTest {

    private BookingGateService bookingGateService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @Mock
    private RestTemplate restTemplate;

    @Mock
    private AuditService auditService;

    private static final String ADMISSION_SERVICE_URL = "http://localhost:8082";

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        bookingGateService = new BookingGateService(redisTemplate, restTemplate, auditService, ADMISSION_SERVICE_URL);
    }

    @Test
    void processBooking_shouldAllowValidPass() {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setAdmissionPass("valid.pass.token");

        ValidatePassResponse validationResponse = new ValidatePassResponse();
        validationResponse.setValid(true);
        validationResponse.setPassId("pass-123");

        when(restTemplate.getForEntity(anyString(), eq(ValidatePassResponse.class)))
                .thenReturn(ResponseEntity.ok(validationResponse));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true); // Atomic lock acquired

        // When
        boolean result = bookingGateService.processBooking(request);

        // Then
        assertTrue(result);
        verify(auditService).logBooking(eq("user123"), eq("event456"), eq("BOOKING_APPROVED"), anyString(), anyString());
    }

    @Test
    void processBooking_shouldRejectInvalidPass() {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setAdmissionPass("invalid.pass.token");

        ValidatePassResponse validationResponse = new ValidatePassResponse();
        validationResponse.setValid(false);

        when(restTemplate.getForEntity(anyString(), eq(ValidatePassResponse.class)))
                .thenReturn(ResponseEntity.ok(validationResponse));

        // When
        boolean result = bookingGateService.processBooking(request);

        // Then
        assertFalse(result);
        verify(auditService).logBooking(eq("user123"), eq("event456"), eq("INVALID_PASS"), anyString(), anyString());
        verify(valueOperations, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void processBooking_shouldRejectDuplicateUse() {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setAdmissionPass("valid.pass.token");

        ValidatePassResponse validationResponse = new ValidatePassResponse();
        validationResponse.setValid(true);
        validationResponse.setPassId("pass-123");

        when(restTemplate.getForEntity(anyString(), eq(ValidatePassResponse.class)))
                .thenReturn(ResponseEntity.ok(validationResponse));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(false); // Lock already held (pass already used)

        // When
        boolean result = bookingGateService.processBooking(request);

        // Then
        assertFalse(result);
        verify(auditService).logBooking(eq("user123"), eq("event456"), eq("ALREADY_USED"), anyString(), anyString());
    }

    @Test
    void processBooking_shouldHandleValidationServiceError() {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setAdmissionPass("valid.pass.token");

        when(restTemplate.getForEntity(anyString(), eq(ValidatePassResponse.class)))
                .thenThrow(new RuntimeException("Service unavailable"));

        // When
        boolean result = bookingGateService.processBooking(request);

        // Then
        assertFalse(result);
        verify(auditService).logBooking(eq("user123"), eq("event456"), eq("VALIDATION_ERROR"), anyString(), anyString());
    }

    @Test
    void processBooking_shouldUseCorrectRedisKey() {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setAdmissionPass("valid.pass.token");

        ValidatePassResponse validationResponse = new ValidatePassResponse();
        validationResponse.setValid(true);
        validationResponse.setPassId("pass-123");

        when(restTemplate.getForEntity(anyString(), eq(ValidatePassResponse.class)))
                .thenReturn(ResponseEntity.ok(validationResponse));
        when(valueOperations.setIfAbsent(anyString(), anyString(), any(Duration.class)))
                .thenReturn(true);

        // When
        bookingGateService.processBooking(request);

        // Then
        verify(valueOperations).setIfAbsent(eq("pass:used:pass-123"), anyString(), any(Duration.class));
    }

    @Test
    void processBooking_shouldHandleNullValidationResponse() {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setAdmissionPass("valid.pass.token");

        when(restTemplate.getForEntity(anyString(), eq(ValidatePassResponse.class)))
                .thenReturn(ResponseEntity.ok(null));

        // When
        boolean result = bookingGateService.processBooking(request);

        // Then
        assertFalse(result);
    }
}
