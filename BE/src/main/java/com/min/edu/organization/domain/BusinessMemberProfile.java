package com.min.edu.organization.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 기업 회원(members)의 담당자 정보.
 * 소셜 회원의 nickname과 의미가 섞이지 않도록 별도 테이블로 분리한다.
 */
@Entity
@Table(name = "business_member_profiles")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BusinessMemberProfile {

    @Id
    @Column(name = "member_id")
    private Long memberId;

    @Column(name = "contact_name", nullable = false, length = 50)
    private String contactName;

    @Column(name = "contact_phone", nullable = false, length = 30)
    private String contactPhone;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static BusinessMemberProfile create(
            Long memberId,
            String contactName,
            String contactPhone,
            OffsetDateTime now) {
        return BusinessMemberProfile.builder()
            .memberId(memberId)
            .contactName(contactName)
            .contactPhone(contactPhone)
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    public void update(String contactName, String contactPhone, OffsetDateTime now) {
        this.contactName = contactName;
        this.contactPhone = contactPhone;
        this.updatedAt = now;
    }
}
