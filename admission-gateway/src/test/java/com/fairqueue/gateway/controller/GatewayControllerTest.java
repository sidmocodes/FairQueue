package com.fairqueue.gateway.controller;

import com.fairqueue.common.dto.JoinQueueRequest;
import com.fairqueue.common.dto.JoinQueueResponse;
import com.fairqueue.gateway.service.GatewayService;
import com.fairqueue.gateway.service.RateLimiterService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(GatewayController.class)
class GatewayControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private GatewayService gatewayService;

    @MockBean
    private RateLimiterService rateLimiterService;

    @Test
    void joinQueue_shouldReturnSuccessWhenValidRequest() throws Exception {
        // Given
        JoinQueueRequest request = new JoinQueueRequest();
        request.setUserId("user123");
        request.setEventId("event456");

        JoinQueueResponse response = new JoinQueueResponse();
        response.setQueueToken("token123");
        response.setPosition(42);

        when(rateLimiterService.allowRequest(anyString(), anyString())).thenReturn(true);
        when(gatewayService.processJoinRequest(any(JoinQueueRequest.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/queue/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.queueToken").value("token123"))
                .andExpect(jsonPath("$.position").value(42));
    }

    @Test
    void joinQueue_shouldReturnTooManyRequestsWhenRateLimitExceeded() throws Exception {
        // Given
        JoinQueueRequest request = new JoinQueueRequest();
        request.setUserId("user123");
        request.setEventId("event456");

        when(rateLimiterService.allowRequest(anyString(), anyString())).thenReturn(false);

        // When/Then
        mockMvc.perform(post("/queue/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isTooManyRequests())
                .andExpect(jsonPath("$.message").value("Rate limit exceeded. Please try again later."));
    }

    @Test
    void joinQueue_shouldReturnBadRequestWhenMissingUserId() throws Exception {
        // Given
        JoinQueueRequest request = new JoinQueueRequest();
        request.setEventId("event456");
        // userId is missing

        // When/Then
        mockMvc.perform(post("/queue/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void joinQueue_shouldReturnBadRequestWhenMissingEventId() throws Exception {
        // Given
        JoinQueueRequest request = new JoinQueueRequest();
        request.setUserId("user123");
        // eventId is missing

        // When/Then
        mockMvc.perform(post("/queue/join")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }
}
