package com.min.edu.admin.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

public final class PlatformAdminDtos {
    private PlatformAdminDtos() {}

    public record Account(
            Long id, String email, String nickname, String platformRole,
            String status, String organizationName, String organizationType,
            OffsetDateTime createdAt, OffsetDateTime lastLoginAt) {}

    public record AccountStatusRequest(String status) {}

    public record Statistics(
            long activeEventCount, long totalTicketQuantity,
            long exhibitorOrganizationCount, BigDecimal advertisementRevenue) {}

    public record AuditEntry(
            String id, String category, String action, String target,
            String detail, OffsetDateTime occurredAt) {}

    public record Dashboard(
            long pendingEventCount, long activeAccountCount,
            long pendingAdvertisementCount, long activeEventCount,
            List<AuditEntry> recentActivity) {}
}
