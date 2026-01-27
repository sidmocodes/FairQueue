package com.fairqueue.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class QueueStatusResponse {
    private long position;
    private long queueDepth;
    private long estimatedWaitTimeSeconds;
    private int penaltyScore;
    private boolean eligible;
    private String message;
}
