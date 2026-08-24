package com.min.edu.event.controller;

import com.min.edu.common.response.ApiResponse;
import com.min.edu.event.dto.VenueParkingStatusDto;
import com.min.edu.event.service.CoexParkingStatusService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/v1/venue-guides")
@RequiredArgsConstructor
public class VenueGuideController {
    private final CoexParkingStatusService coexParkingStatusService;

    @GetMapping("/coex/parking-status")
    public ApiResponse<VenueParkingStatusDto> getCoexParkingStatus() {
        return ApiResponse.success(coexParkingStatusService.getStatus());
    }
}
