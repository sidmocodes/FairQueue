package com.fairqueue.admission.repository;

import com.fairqueue.admission.entity.EventEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface EventRepository extends JpaRepository<EventEntity, Long> {
    
    Optional<EventEntity> findByEventId(String eventId);
    
    List<EventEntity> findByActiveTrue();
}
