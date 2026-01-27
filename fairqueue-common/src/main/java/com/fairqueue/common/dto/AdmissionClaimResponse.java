package com.fairqueue.common.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionClaimResponse {
    private String admissionPass;
    private long expiresInSeconds;
    private String message;
}
