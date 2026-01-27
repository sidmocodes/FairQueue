package com.fairqueue.admission.service;

import com.fairqueue.admission.entity.EventEntity;
import com.fairqueue.admission.repository.EventRepository;
import com.fairqueue.common.dto.CreateEventRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class EventServiceTest {

    @InjectMocks
    private EventService eventService;

    @Mock
    private EventRepository eventRepository;

    @Test
    void createEvent_shouldSaveAndReturnEvent() {
        // Given
        CreateEventRequest request = new CreateEventRequest();
        request.setName("Concert 2026");
        request.setTotalCapacity(1000);
        request.setAdmissionRatePerMinute(50);
        request.setEventStartTime(Instant.now().plusSeconds(3600));
        request.setQueueOpenTime(Instant.now().plusSeconds(1800));

        EventEntity savedEntity = new EventEntity();
        savedEntity.setId(1L);
        savedEntity.setEventId("event-123");
        savedEntity.setName("Concert 2026");
        savedEntity.setActive(true);

        when(eventRepository.save(any(EventEntity.class))).thenReturn(savedEntity);

        // When
        EventEntity result = eventService.createEvent(request);

        // Then
        assertNotNull(result);
        assertEquals("Concert 2026", result.getName());
        assertTrue(result.isActive());
        verify(eventRepository).save(any(EventEntity.class));
    }

    @Test
    void getEvent_shouldReturnEventWhenFound() {
        // Given
        String eventId = "event-123";
        EventEntity entity = new EventEntity();
        entity.setEventId(eventId);
        entity.setName("Test Event");

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.of(entity));

        // When
        EventEntity result = eventService.getEvent(eventId);

        // Then
        assertNotNull(result);
        assertEquals(eventId, result.getEventId());
        assertEquals("Test Event", result.getName());
    }

    @Test
    void getEvent_shouldThrowExceptionWhenNotFound() {
        // Given
        String eventId = "nonexistent";
        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.empty());

        // When/Then
        assertThrows(IllegalArgumentException.class, () -> {
            eventService.getEvent(eventId);
        });
    }

    @Test
    void deactivateEvent_shouldSetActiveToFalse() {
        // Given
        String eventId = "event-123";
        EventEntity entity = new EventEntity();
        entity.setEventId(eventId);
        entity.setActive(true);

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.of(entity));
        when(eventRepository.save(any(EventEntity.class))).thenReturn(entity);

        // When
        eventService.deactivateEvent(eventId);

        // Then
        assertFalse(entity.isActive());
        verify(eventRepository).save(entity);
    }

    @Test
    void isEventActive_shouldReturnTrueForActiveEvent() {
        // Given
        String eventId = "event-123";
        EventEntity entity = new EventEntity();
        entity.setEventId(eventId);
        entity.setActive(true);

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.of(entity));

        // When
        boolean result = eventService.isEventActive(eventId);

        // Then
        assertTrue(result);
    }

    @Test
    void isEventActive_shouldReturnFalseForInactiveEvent() {
        // Given
        String eventId = "event-123";
        EventEntity entity = new EventEntity();
        entity.setEventId(eventId);
        entity.setActive(false);

        when(eventRepository.findByEventId(eventId)).thenReturn(Optional.of(entity));

        // When
        boolean result = eventService.isEventActive(eventId);

        // Then
        assertFalse(result);
    }

    @Test
    void isEventActive_shouldReturnFalseWhenEventNotFound() {
        // Given
        when(eventRepository.findByEventId(anyString())).thenReturn(Optional.empty());

        // When
        boolean result = eventService.isEventActive("nonexistent");

        // Then
        assertFalse(result);
    }
}
