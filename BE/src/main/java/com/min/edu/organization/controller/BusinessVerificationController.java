package com.min.edu.organization.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.common.response.ApiResponse;
import com.min.edu.organization.dto.BusinessVerificationRequestDto;
import com.min.edu.organization.dto.BusinessVerificationResponseDto;
import com.min.edu.organization.service.BusinessVerificationService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth/business")
@RequiredArgsConstructor
public class BusinessVerificationController {

    private final BusinessVerificationService businessVerificationService;

    @PostMapping("/verify")
    public ApiResponse<BusinessVerificationResponseDto> verify(
            @Valid @RequestBody BusinessVerificationRequestDto request) {
        BusinessVerificationResponseDto response =
            businessVerificationService.verify(request);

        return ApiResponse.success(response);
    }
}
