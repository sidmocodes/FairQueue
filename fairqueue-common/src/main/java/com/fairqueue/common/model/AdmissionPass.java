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
public class AdmissionPass {
    private String passId;
    private String userId;
    private String eventId;
    private Instant issuedAt;
    private Instant expiresAt;
    private boolean used;
    private String usedBy;
}
