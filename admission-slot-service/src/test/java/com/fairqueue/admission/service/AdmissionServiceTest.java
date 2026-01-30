package com.fairqueue.admission.service;

import com.fairqueue.admission.entity.AdmissionPassEntity;
import com.fairqueue.admission.entity.EventEntity;
import com.fairqueue.admission.repository.AdmissionPassRepository;
import com.fairqueue.common.dto.AdmissionClaimResponse;
import com.fairqueue.common.util.TokenService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.client.RestTemplate;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AdmissionServiceTest {

    @Mock
    private AdmissionPassRepository passRepository;

    @Mock
    private EventService eventService;

    @Mock
    private TokenService tokenService;

    @Mock
    private RestTemplate restTemplate;

    private MeterRegistry meterRegistry;
    private AdmissionService admissionService;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        admissionService = new AdmissionService(
                passRepository,
                eventService,
                tokenService,
                restTemplate,
                meterRegistry
        );
    }

    @Test
    void claimAdmission_shouldIssueNewPassForValidToken() {
        // Arrange
        String userId = "user123";
        String eventId = "event456";
        String queueToken = "validToken";

        Claims claims = Jwts.claims().subject(userId).add("eventId", eventId).build();

        EventEntity event = EventEntity.builder()
                .eventId(eventId)
                .name("Test Event")
                .totalCapacity(1000)
                .active(true)
                .build();

        when(tokenService.isTokenValid(queueToken)).thenReturn(true);
        when(tokenService.validateAndParse(queueToken)).thenReturn(claims);
        when(passRepository.findByUserIdAndEventId(userId, eventId)).thenReturn(List.of());
        when(eventService.getEvent(eventId)).thenReturn(event);
        when(passRepository.save(any(AdmissionPassEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        AdmissionClaimResponse response = admissionService.claimAdmission(queueToken);

        // Assert
        assertNotNull(response);
        assertNotNull(response.getAdmissionPass());
        assertTrue(response.getExpiresInSeconds() > 0);
        assertEquals("Admission pass issued successfully", response.getMessage());

        verify(passRepository).save(any(AdmissionPassEntity.class));
        
        Counter counter = meterRegistry.find("fairqueue.admission.pass.issued").counter();
        assertNotNull(counter);
        assertEquals(1.0, counter.count());
    }

    @Test
    void claimAdmission_shouldThrowExceptionForInvalidToken() {
        // Arrange
        String queueToken = "invalidToken";
        when(tokenService.isTokenValid(queueToken)).thenReturn(false);

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            admissionService.claimAdmission(queueToken);
        });

        verify(passRepository, never()).save(any());
    }

    @Test
    void claimAdmission_shouldReturnExistingValidPass() {
        // Arrange
        String userId = "user123";
        String eventId = "event456";
        String queueToken = "validToken";
        String existingPassId = UUID.randomUUID().toString();

        Claims claims = Jwts.claims().subject(userId).add("eventId", eventId).build();

        AdmissionPassEntity existingPass = AdmissionPassEntity.builder()
                .passId(existingPassId)
                .userId(userId)
                .eventId(eventId)
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(240))
                .used(false)
                .build();

        when(tokenService.isTokenValid(queueToken)).thenReturn(true);
        when(tokenService.validateAndParse(queueToken)).thenReturn(claims);
        when(passRepository.findByUserIdAndEventId(userId, eventId)).thenReturn(List.of(existingPass));

        // Act
        AdmissionClaimResponse response = admissionService.claimAdmission(queueToken);

        // Assert
        assertNotNull(response);
        assertEquals(existingPassId, response.getAdmissionPass());
        
        // Should not create new pass
        verify(passRepository, never()).save(any());
    }

    @Test
    void claimAdmission_shouldThrowExceptionWhenEventNotActive() {
        // Arrange
        String userId = "user123";
        String eventId = "event456";
        String queueToken = "validToken";

        Claims claims = Jwts.claims().subject(userId).add("eventId", eventId).build();

        EventEntity inactiveEvent = EventEntity.builder()
                .eventId(eventId)
                .name("Inactive Event")
                .totalCapacity(1000)
                .active(false)
                .build();

        when(tokenService.isTokenValid(queueToken)).thenReturn(true);
        when(tokenService.validateAndParse(queueToken)).thenReturn(claims);
        when(passRepository.findByUserIdAndEventId(userId, eventId)).thenReturn(List.of());
        when(eventService.getEvent(eventId)).thenReturn(inactiveEvent);

        // Act & Assert
        assertThrows(IllegalStateException.class, () -> {
            admissionService.claimAdmission(queueToken);
        });

        verify(passRepository, never()).save(any());
    }

    @Test
    void validatePass_shouldReturnTrueForValidPass() {
        // Arrange
        String passId = UUID.randomUUID().toString();
        AdmissionPassEntity pass = AdmissionPassEntity.builder()
                .passId(passId)
                .userId("user123")
                .eventId("event456")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(240))
                .used(false)
                .build();

        when(passRepository.findByPassId(passId)).thenReturn(Optional.of(pass));

        // Act
        boolean isValid = admissionService.validatePass(passId);

        // Assert
        assertTrue(isValid);
    }

    @Test
    void validatePass_shouldReturnFalseForNonExistentPass() {
        // Arrange
        String passId = "nonExistentPass";
        when(passRepository.findByPassId(passId)).thenReturn(Optional.empty());

        // Act
        boolean isValid = admissionService.validatePass(passId);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void validatePass_shouldReturnFalseForUsedPass() {
        // Arrange
        String passId = UUID.randomUUID().toString();
        AdmissionPassEntity usedPass = AdmissionPassEntity.builder()
                .passId(passId)
                .userId("user123")
                .eventId("event456")
                .issuedAt(Instant.now().minusSeconds(300))
                .expiresAt(Instant.now().plusSeconds(60))
                .used(true)
                .usedAt(Instant.now().minusSeconds(100))
                .build();

        when(passRepository.findByPassId(passId)).thenReturn(Optional.of(usedPass));

        // Act
        boolean isValid = admissionService.validatePass(passId);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void validatePass_shouldReturnFalseForExpiredPass() {
        // Arrange
        String passId = UUID.randomUUID().toString();
        AdmissionPassEntity expiredPass = AdmissionPassEntity.builder()
                .passId(passId)
                .userId("user123")
                .eventId("event456")
                .issuedAt(Instant.now().minusSeconds(400))
                .expiresAt(Instant.now().minusSeconds(100))
                .used(false)
                .build();

        when(passRepository.findByPassId(passId)).thenReturn(Optional.of(expiredPass));

        // Act
        boolean isValid = admissionService.validatePass(passId);

        // Assert
        assertFalse(isValid);
    }

    @Test
    void markPassAsUsed_shouldUpdatePassCorrectly() {
        // Arrange
        String passId = UUID.randomUUID().toString();
        String usedBy = "system";
        
        AdmissionPassEntity pass = AdmissionPassEntity.builder()
                .passId(passId)
                .userId("user123")
                .eventId("event456")
                .issuedAt(Instant.now().minusSeconds(60))
                .expiresAt(Instant.now().plusSeconds(240))
                .used(false)
                .build();

        when(passRepository.findByPassId(passId)).thenReturn(Optional.of(pass));
        when(passRepository.save(any(AdmissionPassEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        admissionService.markPassAsUsed(passId, usedBy);

        // Assert
        verify(passRepository).save(argThat(savedPass -> 
            savedPass.isUsed() && 
            savedPass.getUsedBy().equals(usedBy) &&
            savedPass.getUsedAt() != null
        ));
    }

    @Test
    void markPassAsUsed_shouldThrowExceptionForNonExistentPass() {
        // Arrange
        String passId = "nonExistentPass";
        when(passRepository.findByPassId(passId)).thenReturn(Optional.empty());

        // Act & Assert
        assertThrows(IllegalArgumentException.class, () -> {
            admissionService.markPassAsUsed(passId, "system");
        });

        verify(passRepository, never()).save(any());
    }
}
