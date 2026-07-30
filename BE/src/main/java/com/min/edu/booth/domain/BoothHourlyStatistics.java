package com.min.edu.booth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "booth_hourly_statistics",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booth_hourly_statistics",
                columnNames = {"booth_id", "stat_date", "stat_hour"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothHourlyStatistics {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "booth_id", nullable = false)
    private Long boothId;

    @Column(name = "stat_date", nullable = false)
    private LocalDate statDate;

    @Column(name = "stat_hour", nullable = false)
    private Short statHour;

    @Column(name = "reservation_count", nullable = false)
    private Integer reservationCount;

    @Column(name = "no_show_count", nullable = false)
    private Integer noShowCount;

    @Column(name = "qr_scan_count", nullable = false)
    private Integer qrScanCount;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;
}
