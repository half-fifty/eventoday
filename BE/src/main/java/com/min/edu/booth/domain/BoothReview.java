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

@Entity
@Table(
        name = "booth_reviews",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booth_reviews",
                columnNames = {"member_id", "booth_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothReview {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "booth_id", nullable = false)
    private Long boothId;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "rating", nullable = false)
    private Short rating;

    @Column(name = "comment", length = 300)
    private String comment;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "member_name")
    private String memberName;

    // 신고 누적 또는 운영자 조치로 공개 목록/평점/AI 요약에서 제외되었는지 여부
    @Column(name = "hidden", nullable = false)
    @Builder.Default
    private boolean hidden = false;

    // 숨김 사유: REPORTED(신고 누적), MANAGER_HIDDEN(운영자 강제 숨김)
    @Column(name = "hidden_reason", length = 30)
    private String hiddenReason;

    @Column(name = "hidden_at")
    private OffsetDateTime hiddenAt;

    // 1) 별점 수정
    public void updateRating(Short rating) {
        if (rating == null) {
            throw new IllegalArgumentException("평점은 null일 수 없습니다");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("평점은 1점 이상 5점 이하여야 합니다");
        }
        this.rating = rating;
    }

    // 2) 댓글 수정
    public void updateComment(String comment) {
        this.comment = comment;
    }

    // 3) 수정 시간 업데이트
    public void updateUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    // 4) 신고 누적/운영자 조치로 숨김 처리 (하드 삭제 대신 소프트 숨김 — 되돌리거나 근거를 남길 수 있게)
    public void hide(String reason, OffsetDateTime now) {
        this.hidden = true;
        this.hiddenReason = reason;
        this.hiddenAt = now;
    }

    // 5) 숨김 해제 (운영자가 오판이었다고 판단한 경우)
    public void unhide() {
        this.hidden = false;
        this.hiddenReason = null;
        this.hiddenAt = null;
    }
}