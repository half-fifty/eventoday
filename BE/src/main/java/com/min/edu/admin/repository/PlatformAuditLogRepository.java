package com.min.edu.admin.repository;

import com.min.edu.admin.domain.PlatformAuditLog;
import java.util.List;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAuditLogRepository extends JpaRepository<PlatformAuditLog, Long> {
    List<PlatformAuditLog> findAllByOrderByOccurredAtDescIdDesc(Pageable pageable);
}
