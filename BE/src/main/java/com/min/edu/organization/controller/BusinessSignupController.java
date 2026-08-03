package com.min.edu.organization.controller;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.min.edu.common.response.ApiResponse;
import com.min.edu.organization.dto.BusinessSignupRequestDto;
import com.min.edu.organization.dto.BusinessSignupResponseDto;
import com.min.edu.organization.service.BusinessSignupService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/auth/business")
@RequiredArgsConstructor
public class BusinessSignupController {

    private final BusinessSignupService businessSignupService;

    @PostMapping("/signup")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BusinessSignupResponseDto> signup(
            @Valid @RequestBody BusinessSignupRequestDto request) {
        return ApiResponse.success(businessSignupService.signup(request));
    }
}
