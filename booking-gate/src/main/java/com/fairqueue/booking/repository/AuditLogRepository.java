package com.fairqueue.booking.repository;

import com.fairqueue.booking.entity.AuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface AuditLogRepository extends JpaRepository<AuditLog, Long> {
    
    List<AuditLog> findByUserId(String userId);
    
    List<AuditLog> findByEventId(String eventId);
    
    List<AuditLog> findByPassId(String passId);
}
