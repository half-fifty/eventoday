package com.min.edu.booth.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(
        name = "booth_map_positions",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_booth_map_positions",
                columnNames = {"venue_map_id", "booth_id"}
        )
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class BoothMapPosition {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "id")
    private Long id;

    @Column(name = "venue_map_id", nullable = false)
    private Long venueMapId;

    @Column(name = "booth_id", nullable = false)
    private Long boothId;

    @Column(name = "x_ratio", nullable = false, precision = 8, scale = 6)
    private BigDecimal xRatio;

    @Column(name = "y_ratio", nullable = false, precision = 8, scale = 6)
    private BigDecimal yRatio;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;
}
