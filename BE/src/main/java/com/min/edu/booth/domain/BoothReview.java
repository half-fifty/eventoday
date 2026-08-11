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
}