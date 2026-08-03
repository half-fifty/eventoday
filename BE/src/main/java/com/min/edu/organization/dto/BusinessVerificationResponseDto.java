package com.min.edu.organization.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BusinessVerificationResponseDto {

    private boolean valid;
    private boolean active;
}
