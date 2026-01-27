package com.fairqueue.admission.repository;

import com.fairqueue.admission.entity.AdmissionPassEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

@Repository
public interface AdmissionPassRepository extends JpaRepository<AdmissionPassEntity, Long> {
    
    Optional<AdmissionPassEntity> findByPassId(String passId);
    
    List<AdmissionPassEntity> findByUserIdAndEventId(String userId, String eventId);
    
    long countByEventIdAndUsedFalseAndExpiresAtAfter(String eventId, Instant now);
    
    void deleteByExpiresAtBefore(Instant cutoff);
}
