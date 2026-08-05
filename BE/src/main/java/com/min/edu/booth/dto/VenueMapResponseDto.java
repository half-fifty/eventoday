package com.min.edu.booth.dto;

import java.time.OffsetDateTime;
import java.util.List;

import com.min.edu.booth.domain.VenueMapStatus;
import com.min.edu.booth.domain.VenueMapType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class VenueMapResponseDto {

    private Long id;
    private Long eventId;
    private VenueMapType mapType;
    private String floorName;
    private Long imageFileId;
    private Integer originalWidth;
    private Integer originalHeight;
    private Integer version;
    private VenueMapStatus status;
    private OffsetDateTime publishedAt;
    private List<BoothMapPositionResponseDto> positions;
}
