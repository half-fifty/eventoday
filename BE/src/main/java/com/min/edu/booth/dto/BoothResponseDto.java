package com.min.edu.booth.dto;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.List;

import com.min.edu.booth.domain.BoothStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class BoothResponseDto {

    private Long id;
    private Long eventId;
    private Long assignedOrganizationId;
    private String boothCode;
    private String boothType;
    private String floorName;
    private String zoneName;
    private String locationDescription;
    private BigDecimal widthMeter;
    private BigDecimal depthMeter;
    private BigDecimal areaSqm;
    private List<String> basicEquipment;
    private boolean electricityAvailable;
    private boolean waterAvailable;
    private boolean drainageAvailable;
    private boolean internetAvailable;
    private BigDecimal price;
    private BoothStatus status;
    private String displayName;
    private String shortIntro;
    private String description;
    private String exhibitionContent;
    private Long representativeFileId;
    private String qrToken;
    private OffsetDateTime qrIssuedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}
