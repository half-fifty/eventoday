package com.min.edu.booth.congestion.domain;

import com.min.edu.booth.domain.Booth;
import jakarta.persistence.*;
import lombok.*;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;

@Entity
@Table(name = "booth_congestion_forecast")
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoothCongestionForecast {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "booth_id", nullable = false)
    private Booth booth;

    @Column(nullable = false)
    private Integer forecastHour;  // 0~23

    @Column(nullable = false)
    private LocalDate forecastDate;

    @Enumerated(EnumType.STRING)
    private CongestionLevel predictedCongestionLevel;

    private Integer predictedWaitTime;

    private BigDecimal predictedCapacityRate;

    @Column(nullable = false)
    private LocalDateTime createdAt;
}