package com.min.edu.funnel.domain;

import java.time.LocalDate;
import java.time.OffsetDateTime;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 행사(event) x 일자(report_date) 단위로 배치가 하루 한 건 생성하는 진단 결과.
 * 판정 기준값(session_window_minutes)을 함께 저장해, 나중에 기준이 바뀌어도
 * 과거 리포트의 해석이 흔들리지 않게 한다 (technical-design.md 참고).
 */
@Entity
@Table(
        name = "funnel_diagnosis_report",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_funnel_diagnosis_report_event_date",
                columnNames = {"event_id", "report_date"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class FunnelDiagnosisReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "report_date", nullable = false)
    private LocalDate reportDate;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "step_conversion_rates", nullable = false, columnDefinition = "jsonb")
    private String stepConversionRates;

    @Column(name = "anomaly_detected", nullable = false)
    private boolean anomalyDetected;

    @Column(name = "statistically_significant", nullable = false)
    private boolean statisticallySignificant;

    @Column(name = "ai_comment", columnDefinition = "TEXT")
    private String aiComment;

    @Column(name = "baseline_avg_rate")
    private Double baselineAvgRate;

    @Column(name = "sample_size", nullable = false)
    private int sampleSize;

    @Column(name = "session_window_minutes", nullable = false)
    private int sessionWindowMinutes;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static FunnelDiagnosisReport create(
            Long eventId,
            LocalDate reportDate,
            String stepConversionRates,
            boolean anomalyDetected,
            boolean statisticallySignificant,
            String aiComment,
            Double baselineAvgRate,
            int sampleSize,
            int sessionWindowMinutes,
            OffsetDateTime now) {
        return FunnelDiagnosisReport.builder()
                .eventId(eventId)
                .reportDate(reportDate)
                .stepConversionRates(stepConversionRates)
                .anomalyDetected(anomalyDetected)
                .statisticallySignificant(statisticallySignificant)
                .aiComment(aiComment)
                .baselineAvgRate(baselineAvgRate)
                .sampleSize(sampleSize)
                .sessionWindowMinutes(sessionWindowMinutes)
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
