package com.fairqueue.queue.controller;

import com.fairqueue.common.dto.JoinQueueResponse;
import com.fairqueue.common.dto.QueueStatusResponse;
import com.fairqueue.common.model.QueueEntry;
import com.fairqueue.queue.service.QueueManager;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(QueueController.class)
class QueueControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private QueueManager queueManager;

    @Test
    void refreshHeartbeat_shouldReturnUpdatedPosition() throws Exception {
        // Given
        String queueToken = "valid.queue.token";
        
        QueueEntry entry = QueueEntry.builder()
                .userId("user123")
                .eventId("event456")
                .queueToken(queueToken)
                .joinTimestamp(Instant.now())
                .lastHeartbeat(Instant.now())
                .penaltyScore(0)
                .position(42L)
                .build();

        when(queueManager.refreshHeartbeat(anyString(), anyString())).thenReturn(entry);

        // When/Then
        mockMvc.perform(post("/queue/refresh")
                .header("Authorization", "Bearer " + queueToken)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"eventId\":\"event456\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(42));
    }

    @Test
    void getStatus_shouldReturnQueuePosition() throws Exception {
        // Given
        String queueToken = "valid.queue.token";
        
        QueueEntry entry = QueueEntry.builder()
                .userId("user123")
                .eventId("event456")
                .queueToken(queueToken)
                .joinTimestamp(Instant.now())
                .lastHeartbeat(Instant.now())
                .penaltyScore(0)
                .position(10L)
                .build();

        when(queueManager.getStatus(queueToken)).thenReturn(entry);

        // When/Then
        mockMvc.perform(get("/queue/status")
                .header("Authorization", "Bearer " + queueToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.position").value(10))
                .andExpect(jsonPath("$.userId").value("user123"))
                .andExpect(jsonPath("$.eventId").value("event456"));
    }

    @Test
    void getStatus_shouldReturnNotFoundWhenTokenInvalid() throws Exception {
        // Given
        String queueToken = "invalid.token";
        when(queueManager.getStatus(queueToken)).thenReturn(null);

        // When/Then
        mockMvc.perform(get("/queue/status")
                .header("Authorization", "Bearer " + queueToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void refreshHeartbeat_shouldReturnBadRequestWhenEventIdMissing() throws Exception {
        // When/Then
        mockMvc.perform(post("/queue/refresh")
                .header("Authorization", "Bearer token")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"))
                .andExpect(status().isBadRequest());
    }
}
