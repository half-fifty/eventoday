package com.min.edu.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BusinessVerificationRequestDto {

    @NotBlank
    @Pattern(regexp = "^[0-9]{10}$")
    private String businessNumber;

    @NotBlank
    @Pattern(regexp = "^[0-9]{8}$")
    private String startDate;

    @NotBlank
    private String representativeName;
}
