package com.fairqueue.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class JoinQueueRequest {
    @NotBlank(message = "User ID is required")
    private String userId;
    
    @NotBlank(message = "Event ID is required")
    private String eventId;
}
