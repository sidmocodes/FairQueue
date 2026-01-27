package com.fairqueue.booking.controller;

import com.fairqueue.booking.service.BookingGateService;
import com.fairqueue.common.dto.BookingEnterRequest;
import com.fairqueue.common.util.TokenService;
import io.jsonwebtoken.Claims;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/booking")
public class BookingController {
    
    private static final Logger logger = LoggerFactory.getLogger(BookingController.class);
    
    private final BookingGateService bookingGateService;
    private final TokenService tokenService;
    
    public BookingController(BookingGateService bookingGateService,
                           TokenService tokenService) {
        this.bookingGateService = bookingGateService;
        this.tokenService = tokenService;
    }
    
    @PostMapping("/enter")
    public ResponseEntity<?> enterBooking(@Valid @RequestBody BookingEnterRequest request) {
        logger.info("Booking entry request received: passId={}", request.getAdmissionPass());
        
        // Extract user and event info from admission pass
        // In a real implementation, this would decode the pass
        // For now, we'll use a simplified approach
        String userId = "user"; // Placeholder
        String eventId = "event"; // Placeholder
        
        boolean allowed = bookingGateService.enterBooking(
            request.getAdmissionPass(),
            userId,
            eventId
        );
        
        if (allowed) {
            logger.info("Booking access granted: passId={}", request.getAdmissionPass());
            return ResponseEntity.ok().body("{\"message\": \"Booking access granted\"}");
        } else {
            logger.warn("Booking access denied: passId={}", request.getAdmissionPass());
            return ResponseEntity.status(403)
                    .body("{\"message\": \"Booking access denied\"}");
        }
    }
}
