package com.min.edu.organization.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BusinessLoginRequestDto {

    @NotBlank(message = "이메일을 입력해 주세요.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    private String password;
}
