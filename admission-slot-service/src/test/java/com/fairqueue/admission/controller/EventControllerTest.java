package com.fairqueue.admission.controller;

import com.fairqueue.admission.entity.EventEntity;
import com.fairqueue.admission.service.EventService;
import com.fairqueue.common.dto.CreateEventRequest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(EventController.class)
class EventControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private EventService eventService;

    @Test
    void createEvent_shouldReturnCreatedEvent() throws Exception {
        // Given
        CreateEventRequest request = new CreateEventRequest();
        request.setName("Concert 2026");
        request.setTotalCapacity(1000);
        request.setAdmissionRatePerMinute(50);
        request.setEventStartTime(Instant.now().plusSeconds(3600));
        request.setQueueOpenTime(Instant.now().plusSeconds(1800));

        EventEntity createdEvent = new EventEntity();
        createdEvent.setId(1L);
        createdEvent.setEventId("event-123");
        createdEvent.setName("Concert 2026");
        createdEvent.setTotalCapacity(1000);
        createdEvent.setActive(true);

        when(eventService.createEvent(any(CreateEventRequest.class))).thenReturn(createdEvent);

        // When/Then
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.eventId").value("event-123"))
                .andExpect(jsonPath("$.name").value("Concert 2026"))
                .andExpect(jsonPath("$.totalCapacity").value(1000))
                .andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void createEvent_shouldReturnBadRequestWhenNameMissing() throws Exception {
        // Given
        CreateEventRequest request = new CreateEventRequest();
        request.setTotalCapacity(1000);
        request.setAdmissionRatePerMinute(50);
        // name is missing

        // When/Then
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createEvent_shouldReturnBadRequestWhenCapacityInvalid() throws Exception {
        // Given
        CreateEventRequest request = new CreateEventRequest();
        request.setName("Concert 2026");
        request.setTotalCapacity(-100); // Invalid
        request.setAdmissionRatePerMinute(50);

        // When/Then
        mockMvc.perform(post("/events")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
