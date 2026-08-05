package com.min.edu.application.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 부스 신청 제출 요청 DTO
 */
@Getter
@NoArgsConstructor
public class BoothApplicationSubmitRequestDto {

    @NotNull
    private Long boothId;

    @NotNull
    private Long applicantOrganizationId;

    @NotBlank
    @Size(max = 150)
    private String teamName;

    @NotBlank
    @Size(max = 50)
    private String contactName;

    @NotBlank
    @Email
    @Size(max = 255)
    private String contactEmail;

    @NotBlank
    @Size(max = 30)
    private String contactPhone;

    @NotBlank
    private String activityDescription;

    @NotBlank
    private String exhibitionContent;

    private Integer expectedVisitors;

    @NotNull
    private Boolean electricityRequired;

    @NotNull
    private Boolean waterRequired;

    @NotNull
    private Boolean drainageRequired;

    @NotNull
    private Boolean internetRequired;

    private String applicationReason;

    /** 견적서 파일 ID (필수) */
    @NotNull
    private Long estimateFileId;

    /** 기타 첨부파일 ID 목록 (선택) */
    @Size(max = 5)
    private List<Long> otherFileIds;
}