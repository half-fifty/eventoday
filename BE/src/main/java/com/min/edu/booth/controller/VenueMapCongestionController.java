package com.min.edu.booth.controller;

import com.min.edu.booth.domain.VenueMapType;
import com.min.edu.booth.dto.VenueMapWithCongestionResponseDto;
import com.min.edu.booth.service.VenueMapCongestionService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/events/{eventId}")
public class VenueMapCongestionController {

    private final VenueMapCongestionService congestionService;

    /**
     * WBS-161: 혼잡도를 포함한 평면도 마커 조회
     * 색상으로 혼잡도를 시각적으로 표현
     *
     * HIGH(빨강): 혼잡도 20명 이상
     * MEDIUM(주황): 혼잡도 10~20명
     * LOW(초록): 혼잡도 10명 미만
     */
    @GetMapping("/guide/markers")
    public ResponseEntity<List<VenueMapWithCongestionResponseDto>> getMarkersWithCongestion(
            @PathVariable Long eventId,
            @RequestParam VenueMapType mapType) {

        List<VenueMapWithCongestionResponseDto> response =
                congestionService.getPublishedWithCongestion(eventId, mapType);
        return ResponseEntity.ok(response);
    }
}