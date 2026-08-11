package com.min.edu.admin.service;

import com.min.edu.admin.domain.PlatformAuditLog;
import com.min.edu.admin.repository.PlatformAuditLogRepository;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;

@Service
public class PlatformAuditService {
    private final PlatformAuditLogRepository repository;
    public PlatformAuditService(PlatformAuditLogRepository repository) { this.repository = repository; }

    public void record(Long actorMemberId, String category, String action,
            Long targetId, String targetName, String detail) {
        repository.save(PlatformAuditLog.builder().actorMemberId(actorMemberId)
            .category(category).action(action).targetId(targetId).targetName(targetName)
            .detail(detail).occurredAt(OffsetDateTime.now()).build());
    }
}
