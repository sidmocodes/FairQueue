package com.fairqueue.admission.service;

import com.fairqueue.admission.entity.AdmissionPassEntity;
import com.fairqueue.admission.entity.EventEntity;
import com.fairqueue.admission.repository.AdmissionPassRepository;
import com.fairqueue.common.dto.AdmissionClaimRequest;
import com.fairqueue.common.dto.AdmissionClaimResponse;
import com.fairqueue.common.util.TokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionServiceTest {

    @InjectMocks
    private AdmissionService admissionService;

    @Mock
    private AdmissionPassRepository passRepository;

    @Mock
    private EventService eventService;

    @Mock
    private TokenService tokenService;

    @Mock
    private RedisTemplate<String, String> redisTemplate;

    @Mock
    private ValueOperations<String, String> valueOperations;

    @BeforeEach
    void setUp() {
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
    }

    @Test
    void claimAdmissionPass_shouldCreatePassForEligibleUser() {
        // Given
        AdmissionClaimRequest request = new AdmissionClaimRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setQueueToken("valid.token");

        EventEntity event = new EventEntity();
        event.setEventId("event456");
        event.setActive(true);

        when(eventService.getEvent("event456")).thenReturn(event);
        when(eventService.isEventActive("event456")).thenReturn(true);
        when(passRepository.findByUserIdAndEventId("user123", "event456")).thenReturn(Optional.empty());
        when(tokenService.generateAdmissionPass(anyString(), anyString(), anyLong())).thenReturn("admission.pass.token");
        
        AdmissionPassEntity savedPass = new AdmissionPassEntity();
        savedPass.setPassId("pass-123");
        savedPass.setUserId("user123");
        savedPass.setEventId("event456");
        
        when(passRepository.save(any(AdmissionPassEntity.class))).thenReturn(savedPass);

        // When
        AdmissionClaimResponse response = admissionService.claimAdmissionPass(request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getAdmissionPass());
        verify(passRepository).save(any(AdmissionPassEntity.class));
    }

    @Test
    void claimAdmissionPass_shouldThrowExceptionWhenEventInactive() {
        // Given
        AdmissionClaimRequest request = new AdmissionClaimRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setQueueToken("valid.token");

        when(eventService.isEventActive("event456")).thenReturn(false);

        // When/Then
        assertThrows(IllegalStateException.class, () -> {
            admissionService.claimAdmissionPass(request);
        });
    }

    @Test
    void claimAdmissionPass_shouldReturnExistingPassIfAlreadyClaimed() {
        // Given
        AdmissionClaimRequest request = new AdmissionClaimRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setQueueToken("valid.token");

        EventEntity event = new EventEntity();
        event.setEventId("event456");
        event.setActive(true);

        AdmissionPassEntity existingPass = new AdmissionPassEntity();
        existingPass.setPassId("existing-pass");
        existingPass.setUserId("user123");
        existingPass.setEventId("event456");
        existingPass.setExpiresAt(Instant.now().plusSeconds(300));

        when(eventService.getEvent("event456")).thenReturn(event);
        when(eventService.isEventActive("event456")).thenReturn(true);
        when(passRepository.findByUserIdAndEventId("user123", "event456")).thenReturn(Optional.of(existingPass));

        // When
        AdmissionClaimResponse response = admissionService.claimAdmissionPass(request);

        // Then
        assertNotNull(response);
        assertNotNull(response.getAdmissionPass());
        verify(passRepository, never()).save(any(AdmissionPassEntity.class));
    }

    @Test
    void validatePass_shouldReturnTrueForValidPass() {
        // Given
        String passId = "pass-123";
        AdmissionPassEntity pass = new AdmissionPassEntity();
        pass.setPassId(passId);
        pass.setUsed(false);
        pass.setExpiresAt(Instant.now().plusSeconds(300));

        when(passRepository.findByPassId(passId)).thenReturn(Optional.of(pass));

        // When
        boolean result = admissionService.validatePass(passId);

        // Then
        assertTrue(result);
    }

    @Test
    void validatePass_shouldReturnFalseForExpiredPass() {
        // Given
        String passId = "pass-123";
        AdmissionPassEntity pass = new AdmissionPassEntity();
        pass.setPassId(passId);
        pass.setUsed(false);
        pass.setExpiresAt(Instant.now().minusSeconds(10)); // Expired

        when(passRepository.findByPassId(passId)).thenReturn(Optional.of(pass));

        // When
        boolean result = admissionService.validatePass(passId);

        // Then
        assertFalse(result);
    }

    @Test
    void validatePass_shouldReturnFalseForUsedPass() {
        // Given
        String passId = "pass-123";
        AdmissionPassEntity pass = new AdmissionPassEntity();
        pass.setPassId(passId);
        pass.setUsed(true);
        pass.setExpiresAt(Instant.now().plusSeconds(300));

        when(passRepository.findByPassId(passId)).thenReturn(Optional.of(pass));

        // When
        boolean result = admissionService.validatePass(passId);

        // Then
        assertFalse(result);
    }

    @Test
    void validatePass_shouldReturnFalseForNonexistentPass() {
        // Given
        when(passRepository.findByPassId(anyString())).thenReturn(Optional.empty());

        // When
        boolean result = admissionService.validatePass("nonexistent");

        // Then
        assertFalse(result);
    }

    @Test
    void markPassAsUsed_shouldUpdatePassStatus() {
        // Given
        String passId = "pass-123";
        String usedBy = "booking-service";
        
        AdmissionPassEntity pass = new AdmissionPassEntity();
        pass.setPassId(passId);
        pass.setUsed(false);

        when(passRepository.findByPassId(passId)).thenReturn(Optional.of(pass));

        // When
        admissionService.markPassAsUsed(passId, usedBy);

        // Then
        assertTrue(pass.isUsed());
        assertEquals(usedBy, pass.getUsedBy());
        assertNotNull(pass.getUsedAt());
        verify(passRepository).save(pass);
    }
}
