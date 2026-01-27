package com.fairqueue.admission.service;

import com.fairqueue.admission.entity.EventEntity;
import com.fairqueue.admission.repository.EventRepository;
import com.fairqueue.common.dto.CreateEventRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class EventService {
    
    private static final Logger logger = LoggerFactory.getLogger(EventService.class);
    
    private final EventRepository eventRepository;
    
    public EventService(EventRepository eventRepository) {
        this.eventRepository = eventRepository;
    }
    
    @Transactional
    public EventEntity createEvent(CreateEventRequest request) {
        String eventId = UUID.randomUUID().toString();
        
        EventEntity event = EventEntity.builder()
                .eventId(eventId)
                .name(request.getName())
                .totalCapacity(request.getTotalCapacity())
                .admissionRatePerMinute(request.getAdmissionRatePerMinute())
                .eventStartTime(request.getEventStartTime())
                .queueOpenTime(request.getQueueOpenTime())
                .active(true)
                .createdAt(Instant.now())
                .build();
        
        eventRepository.save(event);
        
        logger.info("Event created: eventId={}, name={}", eventId, request.getName());
        
        return event;
    }
    
    public EventEntity getEvent(String eventId) {
        return eventRepository.findByEventId(eventId)
                .orElseThrow(() -> new IllegalArgumentException("Event not found: " + eventId));
    }
}
