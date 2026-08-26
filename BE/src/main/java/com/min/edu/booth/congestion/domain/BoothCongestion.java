package com.min.edu.booth.congestion.domain;

import com.min.edu.booth.domain.Booth;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDateTime;

@Entity
@Table(name = "booth_congestion")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoothCongestion {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booth_id", nullable = false)
    private Booth booth;

    @Column(nullable = false)
    @Enumerated(EnumType.STRING)
    private CongestionLevel congestionLevel;

    private Integer estimatedWaitTime;  // 분 단위

    private BigDecimal predictedCapacityRate;  // 0~100%

    @Column(nullable = false)
    private LocalDateTime recordedAt;
}