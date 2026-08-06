package com.min.edu.recruitment.dto;

import java.time.OffsetDateTime;

import com.min.edu.booth.domain.BoothRecruitment;
import com.min.edu.booth.domain.BoothRecruitmentStatus;
import com.min.edu.event.domain.Event;

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
    private boolean businessNumberRequired;
    private BoothRecruitmentStatus status;
    private OffsetDateTime completedAt;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;

    // 모집 공고 상세 화면에서 별도 페이지 이동 없이 행사 정보를 바로 보여주기 위한 요약 정보 (getPublicDetail 등에서만 채움)
    private String eventName;
    private String eventType;
    private String eventShortDescription;
    private String eventDescription;
    private String eventVenueName;
    private String eventAddress;
    private OffsetDateTime eventStartAt;
    private OffsetDateTime eventEndAt;

    public static BoothRecruitmentResponseDto from(BoothRecruitment recruitment) {
        return from(recruitment, null);
    }

    public static BoothRecruitmentResponseDto from(BoothRecruitment recruitment, Event event) {
        BoothRecruitmentResponseDtoBuilder builder = BoothRecruitmentResponseDto.builder()
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
            .businessNumberRequired(recruitment.isBusinessNumberRequired())
            .status(recruitment.getStatus())
            .completedAt(recruitment.getCompletedAt())
            .createdAt(recruitment.getCreatedAt())
            .updatedAt(recruitment.getUpdatedAt());

        if (event != null) {
            builder
                .eventName(event.getName())
                .eventType(event.getEventType())
                .eventShortDescription(event.getShortDescription())
                .eventDescription(event.getDescription())
                .eventVenueName(event.getVenueName())
                .eventAddress(event.getAddress())
                .eventStartAt(event.getStartAt())
                .eventEndAt(event.getEndAt());
        }

        return builder.build();
    }
}
