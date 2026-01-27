package com.fairqueue.common.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionClaimRequest {
    @NotBlank(message = "Queue token is required")
    private String queueToken;
}
