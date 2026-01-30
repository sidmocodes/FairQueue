package com.fairqueue.admission.service;

import com.fairqueue.admission.entity.EventEntity;
import com.fairqueue.admission.repository.EventRepository;
import com.fairqueue.common.dto.CreateEventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @Mock
    private EventRepository eventRepository;

    private EventService eventService;

    @BeforeEach
    void setUp() {
        eventService = new EventService(eventRepository);
    }

    @Test
    void createEvent_shouldCreateAndReturnEvent() {
        // Arrange
        CreateEventRequest request = new CreateEventRequest(
                "Concert 2026",
                10000,
                100,
                Instant.now().plusSeconds(7200),
                Instant.now().plusSeconds(3600)
        );

        when(eventRepository.save(any(EventEntity.class))).thenAnswer(i -> i.getArgument(0));

        // Act
        EventEntity result = eventService.createEvent(request);

        // Assert
        assertNotNull(result);
        assertNotNull(result.getEventId());
        assertEquals("Concert 2026", result.getName());
        assertEquals(10000, result.getTotalCapacity());
        assertEquals(100, result.getAdmissionRatePerMinute());
        assertTrue(result.isActive());
        assertNotNull(result.getCreatedAt());

        verify(eventRepository).save(any(EventEntity.class));
    }

    @Test
    void getEvent_shouldReturnEventWhenExists() {
        // Arrange
        String eventId = "event123";
        EventEntity event = EventEntity.builder()
                .eventId(eventId)
                .name("Test Event")
                .totalCapacity(5000)
                .admissionRatePerMinute(50)
                .active(true)
                .createdAt(Instant.now())
                .build();

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.of(event));

        // Act
        EventEntity result = eventService.getEvent(eventId);

        // Assert
        assertNotNull(result);
        assertEquals(eventId, result.getEventId());
        assertEquals("Test Event", result.getName());
    }

    @Test
    void getEvent_shouldThrowExceptionWhenEventNotFound() {
        // Arrange
        String eventId = "nonExistentEvent";
        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class, () -> {
            eventService.getEvent(eventId);
        });

        assertTrue(exception.getMessage().contains("Event not found"));
    }
}
