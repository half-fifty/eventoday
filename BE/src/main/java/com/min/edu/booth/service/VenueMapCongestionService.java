package com.min.edu.booth.service;

import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothMapPosition;
import com.min.edu.booth.domain.VenueMap;
import com.min.edu.booth.domain.VenueMapStatus;
import com.min.edu.booth.domain.VenueMapType;
import com.min.edu.booth.dto.VenueMapWithCongestionResponseDto;
import com.min.edu.booth.repository.BoothMapPositionRepository;
import com.min.edu.booth.repository.BoothQrScanRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.VenueMapRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class VenueMapCongestionService {

    private final VenueMapRepository venueMapRepository;
    private final BoothMapPositionRepository boothMapPositionRepository;
    private final BoothRepository boothRepository;
    private final BoothQrScanRepository qrScanRepository;

    /**
     * WBS-161: 혼잡도를 포함한 평면도 마커 조회
     * 색상으로 혼잡도를 시각적으로 표현
     */
    public List<VenueMapWithCongestionResponseDto> getPublishedWithCongestion(
            Long eventId, VenueMapType mapType) {

        // 1️⃣ 게시된 평면도들 조회
        List<VenueMap> venueMaps = venueMapRepository
                .findByEventIdAndMapTypeAndStatusOrderByFloorNameAsc(eventId, mapType, VenueMapStatus.PUBLISHED);

        // 2️⃣ 혼잡도 데이터 (최근 10분)
        OffsetDateTime since = OffsetDateTime.now().minusMinutes(10);
        Map<Long, Long> congestionMap = getCongestionMap(since);

        // 3️⃣ 각 평면도별로 부스 마커 생성
        return venueMaps.stream()
                .map(venueMap -> buildVenueMapWithCongestion(venueMap, congestionMap))
                .collect(Collectors.toList());
    }

    /**
     * 부스별 혼잡도 맵 생성
     */
    private Map<Long, Long> getCongestionMap(OffsetDateTime since) {
        var congestedBooths = qrScanRepository.findPopularBooths(since,
                org.springframework.data.domain.PageRequest.of(0, 1000));

        return congestedBooths.stream()
                .collect(Collectors.toMap(
                        row -> ((Number) row[0]).longValue(),
                        row -> ((Number) row[1]).longValue()
                ));
    }

    /**
     * VenueMap + 혼잡도 응답 객체 생성
     */
    private VenueMapWithCongestionResponseDto buildVenueMapWithCongestion(
            VenueMap venueMap, Map<Long, Long> congestionMap) {

        // 이 평면도에 속한 부스 위치들 조회
        List<BoothMapPosition> positions = boothMapPositionRepository.findByVenueMapId(venueMap.getId());

        // 부스 ID로 부스 정보 조회 (한 번에)
        List<Long> boothIds = positions.stream()
                .map(BoothMapPosition::getBoothId)
                .collect(Collectors.toList());

        Map<Long, Booth> boothMap = boothRepository.findByIdIn(boothIds).stream()
                .collect(Collectors.toMap(Booth::getId, booth -> booth));

        // 마커 생성
        List<VenueMapWithCongestionResponseDto.BoothMarker> markers = positions.stream()
                .map(position -> buildMarker(position, boothMap, congestionMap))
                .collect(Collectors.toList());

        return VenueMapWithCongestionResponseDto.builder()
                .venueMapId(venueMap.getId())
                .floorName(venueMap.getFloorName())
                .originalWidth(venueMap.getOriginalWidth())
                .originalHeight(venueMap.getOriginalHeight())
                .positions(markers)
                .build();
    }

    /**
     * 부스 마커 생성 (혼잡도 포함)
     */
    private VenueMapWithCongestionResponseDto.BoothMarker buildMarker(
            BoothMapPosition position,
            Map<Long, Booth> boothMap,
            Map<Long, Long> congestionMap) {

        Booth booth = boothMap.get(position.getBoothId());
        Long congestionCount = congestionMap.getOrDefault(position.getBoothId(), 0L);

        // 혼잡도 수준 판단
        String congestionLevel = determineCongestionLevel(congestionCount);
        String color = getColorByLevel(congestionLevel);

        return VenueMapWithCongestionResponseDto.BoothMarker.builder()
                .boothId(position.getBoothId())
                .boothName(booth != null ? booth.getDisplayName() : "")
                .xRatio(position.getXRatio().doubleValue())
                .yRatio(position.getYRatio().doubleValue())
                .congestionCount(congestionCount)
                .congestionLevel(congestionLevel)
                .color(color)
                .build();
    }

    /**
     * 혼잡도 수준 판단
     */
    private String determineCongestionLevel(Long congestionCount) {
        if (congestionCount >= 20) {
            return "HIGH";      // 혼잡
        } else if (congestionCount >= 10) {
            return "MEDIUM";    // 보통
        } else {
            return "LOW";       // 한산
        }
    }

    /**
     * 혼잡도 수준별 색상
     */
    private String getColorByLevel(String level) {
        return switch (level) {
            case "HIGH" -> "#FF0000";      // 빨강
            case "MEDIUM" -> "#FFA500";    // 주황
            case "LOW" -> "#00CC00";       // 초록
            default -> "#808080";           // 회색
        };
    }
}