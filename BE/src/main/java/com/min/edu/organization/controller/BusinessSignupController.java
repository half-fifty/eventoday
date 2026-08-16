package com.min.edu.organization.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

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

    @PostMapping(value = "/signup", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<BusinessSignupResponseDto> signup(
            @Valid @RequestPart("data") BusinessSignupRequestDto request,
            @RequestPart(value = "certificateFile", required = false)
                MultipartFile certificateFile) {
        return ApiResponse.success(
            businessSignupService.signup(request, certificateFile)
        );
    }
}
