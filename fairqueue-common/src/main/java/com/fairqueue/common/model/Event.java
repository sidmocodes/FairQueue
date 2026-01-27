package com.fairqueue.common.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Event {
    private String eventId;
    private String name;
    private int totalCapacity;
    private int admissionRatePerMinute;
    private Instant eventStartTime;
    private Instant queueOpenTime;
    private boolean active;
}
