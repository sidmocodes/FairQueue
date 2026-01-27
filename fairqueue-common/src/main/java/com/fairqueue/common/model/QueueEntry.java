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
public class QueueEntry {
    private String userId;
    private String queueToken;
    private String eventId;
    private Instant joinTimestamp;
    private Instant lastHeartbeat;
    private int penaltyScore;
    private long position;
    
    public void incrementPenalty() {
        this.penaltyScore++;
    }
    
    public void updateHeartbeat() {
        this.lastHeartbeat = Instant.now();
    }
}
