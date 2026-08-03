package com.min.edu.recruitment.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoothRecruitmentUpdateRequestDto {

    @NotBlank
    private String title;

    @NotNull
    private OffsetDateTime recruitmentStartAt;

    @NotNull
    private OffsetDateTime recruitmentEndAt;

    @NotBlank
    private String participantTarget;

    private String qualification;

    private String selectionMethod;

    private OffsetDateTime expectedDecisionAt;

    @NotBlank
    private String contactName;

    @NotBlank
    @Email
    private String contactEmail;

    @NotBlank
    private String contactPhone;

    private String notice;
}
