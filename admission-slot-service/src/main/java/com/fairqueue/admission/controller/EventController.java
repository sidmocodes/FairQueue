package com.fairqueue.admission.controller;

import com.fairqueue.admission.entity.EventEntity;
import com.fairqueue.admission.service.EventService;
import com.fairqueue.common.dto.CreateEventRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/events")
public class EventController {
    
    private static final Logger logger = LoggerFactory.getLogger(EventController.class);
    
    private final EventService eventService;
    
    public EventController(EventService eventService) {
        this.eventService = eventService;
    }
    
    @PostMapping
    public ResponseEntity<?> createEvent(@Valid @RequestBody CreateEventRequest request) {
        logger.info("Creating event: name={}", request.getName());
        
        EventEntity event = eventService.createEvent(request);
        
        return ResponseEntity.ok(event);
    }
    
    @GetMapping("/{eventId}")
    public ResponseEntity<?> getEvent(@PathVariable String eventId) {
        EventEntity event = eventService.getEvent(eventId);
        return ResponseEntity.ok(event);
    }
}
