package com.fairqueue.admission.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "admission_passes", indexes = {
    @Index(name = "idx_pass_id", columnList = "passId", unique = true),
    @Index(name = "idx_user_event", columnList = "userId,eventId"),
    @Index(name = "idx_expires_at", columnList = "expiresAt")
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AdmissionPassEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String passId;
    
    @Column(nullable = false)
    private String userId;
    
    @Column(nullable = false)
    private String eventId;
    
    @Column(nullable = false)
    private Instant issuedAt;
    
    @Column(nullable = false)
    private Instant expiresAt;
    
    @Column(nullable = false)
    private boolean used;
    
    @Column
    private String usedBy;
    
    @Column
    private Instant usedAt;
}
