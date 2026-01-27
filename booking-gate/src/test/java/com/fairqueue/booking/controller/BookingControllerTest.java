package com.fairqueue.booking.controller;

import com.fairqueue.booking.service.BookingGateService;
import com.fairqueue.common.dto.BookingEnterRequest;
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

@WebMvcTest(BookingController.class)
class BookingControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private BookingGateService bookingGateService;

    @Test
    void enter_shouldReturnSuccessWhenBookingAllowed() throws Exception {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setAdmissionPass("valid.admission.pass");

        when(bookingGateService.processBooking(any(BookingEnterRequest.class))).thenReturn(true);

        // When/Then
        mockMvc.perform(post("/booking/enter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.allowed").value(true))
                .andExpect(jsonPath("$.message").value("Booking successful"));
    }

    @Test
    void enter_shouldReturnForbiddenWhenBookingRejected() throws Exception {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setAdmissionPass("invalid.admission.pass");

        when(bookingGateService.processBooking(any(BookingEnterRequest.class))).thenReturn(false);

        // When/Then
        mockMvc.perform(post("/booking/enter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.allowed").value(false))
                .andExpect(jsonPath("$.message").value("Booking rejected"));
    }

    @Test
    void enter_shouldReturnBadRequestWhenUserIdMissing() throws Exception {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setEventId("event456");
        request.setAdmissionPass("valid.admission.pass");
        // userId is missing

        // When/Then
        mockMvc.perform(post("/booking/enter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enter_shouldReturnBadRequestWhenEventIdMissing() throws Exception {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setUserId("user123");
        request.setAdmissionPass("valid.admission.pass");
        // eventId is missing

        // When/Then
        mockMvc.perform(post("/booking/enter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enter_shouldReturnBadRequestWhenAdmissionPassMissing() throws Exception {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        // admissionPass is missing

        // When/Then
        mockMvc.perform(post("/booking/enter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enter_shouldHandleServiceException() throws Exception {
        // Given
        BookingEnterRequest request = new BookingEnterRequest();
        request.setUserId("user123");
        request.setEventId("event456");
        request.setAdmissionPass("valid.admission.pass");

        when(bookingGateService.processBooking(any(BookingEnterRequest.class)))
                .thenThrow(new RuntimeException("Service error"));

        // When/Then
        mockMvc.perform(post("/booking/enter")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isInternalServerError());
    }
}
