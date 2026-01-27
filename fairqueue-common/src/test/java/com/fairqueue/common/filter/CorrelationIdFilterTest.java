package com.fairqueue.common.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.MDC;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class CorrelationIdFilterTest {

    private CorrelationIdFilter filter;

    @Mock
    private HttpServletRequest request;

    @Mock
    private HttpServletResponse response;

    @Mock
    private FilterChain filterChain;

    @BeforeEach
    void setUp() {
        filter = new CorrelationIdFilter();
        MDC.clear();
    }

    @Test
    void doFilter_shouldUseExistingCorrelationId() throws ServletException, IOException {
        // Given
        String existingCorrelationId = "existing-correlation-id-123";
        when(request.getHeader("X-Correlation-ID")).thenReturn(existingCorrelationId);

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldGenerateCorrelationIdWhenMissing() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn(null);

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldSetMDC() throws ServletException, IOException {
        // Given
        String correlationId = "test-correlation-id";
        when(request.getHeader("X-Correlation-ID")).thenReturn(correlationId);

        // When
        doAnswer(invocation -> {
            // Assert MDC is set during filter chain execution
            assertEquals(correlationId, MDC.get("correlationId"));
            return null;
        }).when(filterChain).doFilter(request, response);

        filter.doFilter(request, response, filterChain);

        // Then
        verify(filterChain).doFilter(request, response);
    }

    @Test
    void doFilter_shouldClearMDCAfterProcessing() throws ServletException, IOException {
        // Given
        String correlationId = "test-correlation-id";
        when(request.getHeader("X-Correlation-ID")).thenReturn(correlationId);

        // When
        filter.doFilter(request, response, filterChain);

        // Then
        assertNull(MDC.get("correlationId"), "MDC should be cleared after filter");
    }

    @Test
    void doFilter_shouldClearMDCEvenOnException() throws ServletException, IOException {
        // Given
        when(request.getHeader("X-Correlation-ID")).thenReturn("test-id");
        doThrow(new ServletException("Test exception")).when(filterChain).doFilter(request, response);

        // When/Then
        assertThrows(ServletException.class, () -> {
            filter.doFilter(request, response, filterChain);
        });
        
        assertNull(MDC.get("correlationId"), "MDC should be cleared even on exception");
    }
}
