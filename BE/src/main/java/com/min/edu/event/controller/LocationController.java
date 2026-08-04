package com.min.edu.event.controller;

import com.min.edu.common.response.ApiResponse;
import com.min.edu.event.dto.LocationDtos;
import com.min.edu.event.service.LocationService;
import jakarta.validation.constraints.Size;
import java.util.List;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/v1/locations")
public class LocationController {
    private final LocationService locationService;

    public LocationController(LocationService locationService) {
        this.locationService = locationService;
    }

    @GetMapping("/search")
    public ApiResponse<List<LocationDtos.Place>> search(
            @RequestParam @Size(min = 2, max = 100) String query) {
        return ApiResponse.success(locationService.search(query));
    }

    @GetMapping("/postal-code")
    public ApiResponse<LocationDtos.PostalCode> postalCode(
            @RequestParam @Size(min = 2, max = 300) String address) {
        return ApiResponse.success(locationService.findPostalCode(address));
    }
}
