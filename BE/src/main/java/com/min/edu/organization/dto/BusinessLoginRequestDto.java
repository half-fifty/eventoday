package com.min.edu.organization.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BusinessLoginRequestDto {

    @NotBlank(message = "사업자등록번호를 입력해 주세요.")
    @Pattern(regexp = "^[0-9]{10}$", message = "사업자등록번호는 숫자 10자리여야 합니다.")
    private String businessNumber;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    private String password;
}
