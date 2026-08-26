package com.min.edu.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class OrganizationSignupRejectionRequestDto {

    @NotBlank(message = "반려 사유를 입력해 주세요.")
    @Size(max = 1000, message = "반려 사유는 1000자 이하여야 합니다.")
    private String reason;
}
