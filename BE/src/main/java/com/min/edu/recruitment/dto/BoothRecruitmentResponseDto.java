package com.min.edu.recruitment.dto;

import java.time.OffsetDateTime;

import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class BoothRecruitmentResponseDto {

    private Long id;
    private Long eventId;
    private String title;
    private OffsetDateTime recruitmentStartAt;
    private OffsetDateTime recruitmentEndAt;
    private String participantTarget;
    private String qualification;
    private String selectionMethod;
    private OffsetDateTime expectedDecisionAt;
    private String contactName;
    private String contactEmail;
    private String contactPhone;
    private String notice;
    private BoothRecruitmentStatus status;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    public static BoothRecruitmentResponseDto from(BoothRecruitment recruitment) {
        return BoothRecruitmentResponseDto.builder()
            .id(recruitment.getId())
            .eventId(recruitment.getEventId())
            .title(recruitment.getTitle())
            .recruitmentStartAt(recruitment.getRecruitmentStartAt())
            .recruitmentEndAt(recruitment.getRecruitmentEndAt())
            .participantTarget(recruitment.getParticipantTarget())
            .qualification(recruitment.getQualification())
            .selectionMethod(recruitment.getSelectionMethod())
            .expectedDecisionAt(recruitment.getExpectedDecisionAt())
            .contactName(recruitment.getContactName())
            .contactEmail(recruitment.getContactEmail())
            .contactPhone(recruitment.getContactPhone())
            .notice(recruitment.getNotice())
            .status(recruitment.getStatus())
            .completedAt(recruitment.getCompletedAt())
            .createdAt(recruitment.getCreatedAt())
            .updatedAt(recruitment.getUpdatedAt())
            .build();
    }
}
