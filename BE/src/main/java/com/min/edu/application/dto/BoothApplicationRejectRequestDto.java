package com.min.edu.application.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부스 신청 반려 요청 DTO
 */
@Getter
@NoArgsConstructor
public class BoothApplicationRejectRequestDto {

    /** 반려 사유 (필수) */
    @NotBlank
    private String rejectionReason;
}