package com.fairqueue.admission.controller;

import com.fairqueue.admission.service.AdmissionService;
import com.fairqueue.common.dto.AdmissionClaimRequest;
import com.fairqueue.common.dto.AdmissionClaimResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(AdmissionController.class)
class AdmissionControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private AdmissionService admissionService;

    @Test
    void claimAdmissionPass_shouldReturnPassWhenSuccessful() throws Exception {
        // Given
        AdmissionClaimRequest request = new AdmissionClaimRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setQueueToken("valid.token");

        AdmissionClaimResponse response = new AdmissionClaimResponse();
        response.setAdmissionPass("admission.pass.token");
        response.setExpiresIn(300);

        when(admissionService.claimAdmissionPass(any(AdmissionClaimRequest.class))).thenReturn(response);

        // When/Then
        mockMvc.perform(post("/admission/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.admissionPass").value("admission.pass.token"))
                .andExpect(jsonPath("$.expiresIn").value(300));
    }

    @Test
    void claimAdmissionPass_shouldReturnBadRequestWhenUserIdMissing() throws Exception {
        // Given
        AdmissionClaimRequest request = new AdmissionClaimRequest();
        request.setEventId("event456");
        request.setQueueToken("valid.token");
        // userId is missing

        // When/Then
        mockMvc.perform(post("/admission/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void claimAdmissionPass_shouldReturnBadRequestWhenEventIdMissing() throws Exception {
        // Given
        AdmissionClaimRequest request = new AdmissionClaimRequest();
        request.setUserId("user123");
        request.setQueueToken("valid.token");
        // eventId is missing

        // When/Then
        mockMvc.perform(post("/admission/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void claimAdmissionPass_shouldReturnConflictWhenEventInactive() throws Exception {
        // Given
        AdmissionClaimRequest request = new AdmissionClaimRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setQueueToken("valid.token");

        when(admissionService.claimAdmissionPass(any(AdmissionClaimRequest.class)))
                .thenThrow(new IllegalStateException("Event is not active"));

        // When/Then
        mockMvc.perform(post("/admission/claim")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }
}
