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

// 리뷰-파일 연결 테이블. 실제 파일 업로드/접근 제어는 공통 파일 도메인(FileAsset)이 담당하고,
// 여기서는 "이 리뷰에 어떤 파일이 몇 번째로 붙어있는지"만 관리한다.
@Entity
@Table(
        name = "booth_review_photos",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booth_review_photos",
                columnNames = {"booth_review_id", "file_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothReviewPhoto {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "booth_review_id", nullable = false)
    private Long boothReviewId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
