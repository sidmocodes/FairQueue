package com.fairqueue.common.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class CreateEventRequest {
    @NotBlank(message = "Event name is required")
    private String name;
    
    @Min(value = 1, message = "Total capacity must be at least 1")
    private int totalCapacity;
    
    @Min(value = 1, message = "Admission rate must be at least 1")
    private int admissionRatePerMinute;
    
    @NotNull(message = "Event start time is required")
    private Instant eventStartTime;
    
    @NotNull(message = "Queue open time is required")
    private Instant queueOpenTime;
}
