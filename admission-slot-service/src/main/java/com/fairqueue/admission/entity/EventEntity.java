package com.fairqueue.admission.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Entity
@Table(name = "events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EventEntity {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String eventId;
    
    @Column(nullable = false)
    private String name;
    
    @Column(nullable = false)
    private int totalCapacity;
    
    @Column(nullable = false)
    private int admissionRatePerMinute;
    
    @Column(nullable = false)
    private Instant eventStartTime;
    
    @Column(nullable = false)
    private Instant queueOpenTime;
    
    @Column(nullable = false)
    private boolean active;
    
    @Column
    private Instant createdAt;
}
