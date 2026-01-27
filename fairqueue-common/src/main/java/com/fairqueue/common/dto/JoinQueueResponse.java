package com.fairqueue.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JoinQueueResponse {
    private String queueToken;
    private long position;
    private long estimatedWaitTimeSeconds;
    private String message;
}
