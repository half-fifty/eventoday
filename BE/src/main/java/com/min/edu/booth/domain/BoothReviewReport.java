package com.min.edu.booth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// 회원 한 명이 같은 리뷰를 두 번 신고할 수 없도록 (booth_review_id, reporter_member_id) 유니크 제약을 건다.
@Entity
@Table(
        name = "booth_review_reports",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booth_review_reports",
                columnNames = {"booth_review_id", "reporter_member_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothReviewReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "booth_review_id", nullable = false)
    private Long boothReviewId;

    @Column(name = "reporter_member_id", nullable = false)
    private Long reporterMemberId;

    @Column(name = "reason", length = 200)
    private String reason;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
