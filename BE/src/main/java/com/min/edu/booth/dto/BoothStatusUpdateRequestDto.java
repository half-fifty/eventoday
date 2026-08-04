package com.min.edu.booth.dto;

import jakarta.validation.constraints.NotNull;

import com.min.edu.booth.domain.BoothStatus;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoothStatusUpdateRequestDto {

    @NotNull
    private BoothStatus status;
}
