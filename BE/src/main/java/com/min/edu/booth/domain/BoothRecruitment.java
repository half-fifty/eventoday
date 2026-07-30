package com.min.edu.booth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "booth_recruitments")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothRecruitment {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false, unique = true)
    private Long eventId;

    @Column(name = "title", nullable = false, length = 200)
    private String title;

    @Column(name = "recruitment_start_at", nullable = false)
    private OffsetDateTime recruitmentStartAt;

    @Column(name = "recruitment_end_at", nullable = false)
    private OffsetDateTime recruitmentEndAt;

    @Column(name = "participant_target", nullable = false, columnDefinition = "TEXT")
    private String participantTarget;

    @Column(name = "qualification", columnDefinition = "TEXT")
    private String qualification;

    @Column(name = "selection_method", columnDefinition = "TEXT")
    private String selectionMethod;

    @Column(name = "expected_decision_at")
    private OffsetDateTime expectedDecisionAt;

    @Column(name = "contact_name", nullable = false, length = 50)
    private String contactName;

    @Column(name = "contact_email", nullable = false, length = 255)
    private String contactEmail;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Column(name = "notice", columnDefinition = "TEXT")
    private String notice;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 30)
    private BoothRecruitmentStatus status;

    @Column(name = "completed_at")
    private OffsetDateTime completedAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
