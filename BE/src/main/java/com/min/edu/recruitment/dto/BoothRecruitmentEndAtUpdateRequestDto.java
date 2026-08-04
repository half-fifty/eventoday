package com.min.edu.recruitment.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoothRecruitmentEndAtUpdateRequestDto {

    @NotNull
    private OffsetDateTime recruitmentEndAt;
}
