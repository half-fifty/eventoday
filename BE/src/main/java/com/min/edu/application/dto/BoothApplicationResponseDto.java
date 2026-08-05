package com.min.edu.application.dto;

import com.min.edu.booth.domain.BoothApplication;
import com.min.edu.booth.domain.BoothApplicationStatus;
import java.time.OffsetDateTime;
import lombok.Builder;
import lombok.Getter;

/**
 * 부스 신청 응답 DTO - BoothRecruitmentResponseDto.from() 패턴과 동일
 */
@Getter
@Builder
public class BoothApplicationResponseDto {

    private Long id;
    private String applicationNo;
    private Long recruitmentId;
    private Long boothId;
    private Long applicantOrganizationId;
    private Long applicantMemberId;
    private String teamName;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String activityDescription;
    private String exhibitionContent;
    private Integer expectedVisitors;
    private boolean electricityRequired;
    private boolean waterRequired;
    private boolean drainageRequired;
    private boolean internetRequired;
    private String applicationReason;
    private BoothApplicationStatus status;
    private OffsetDateTime submittedAt;

    public static BoothApplicationResponseDto from(BoothApplication application) {
        return BoothApplicationResponseDto.builder()
                .id(application.getId())
                .applicationNo(application.getApplicationNo())
                .recruitmentId(application.getRecruitmentId())
                .boothId(application.getBoothId())
                .applicantOrganizationId(application.getApplicantOrganizationId())
                .applicantMemberId(application.getApplicantMemberId())
                .teamName(application.getTeamName())
                .contactName(application.getContactName())
                .contactEmail(application.getContactEmail())
                .contactPhone(application.getContactPhone())
                .activityDescription(application.getActivityDescription())
                .exhibitionContent(application.getExhibitionContent())
                .expectedVisitors(application.getExpectedVisitors())
                .electricityRequired(application.isElectricityRequired())
                .waterRequired(application.isWaterRequired())
                .drainageRequired(application.isDrainageRequired())
                .internetRequired(application.isInternetRequired())
                .applicationReason(application.getApplicationReason())
                .status(application.getStatus())
                .submittedAt(application.getSubmittedAt())
                .build();
    }
}