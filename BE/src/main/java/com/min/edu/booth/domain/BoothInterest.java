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
        name = "booth_interests",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booth_interests",
                columnNames = {"member_id", "booth_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothInterest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "member_id", nullable = false)
    private Long memberId;

    @Column(name = "booth_id", nullable = false)
    private Long boothId;

    @Column(name = "vacancy_notification_enabled", nullable = false)
    private boolean vacancyNotificationEnabled;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    public void updateVacancyNotificationEnabled(boolean enabled) {
        this.vacancyNotificationEnabled = enabled;
    }
}
