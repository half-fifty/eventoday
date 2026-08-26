package com.min.edu.organization.dto;

import java.nio.charset.StandardCharsets;

import com.min.edu.organization.domain.OrganizationType;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BusinessSignupRequestDto {

    @NotNull(message = "가입 유형을 선택해 주세요.")
    private OrganizationType organizationType;

    @NotBlank(message = "로그인 이메일을 입력해 주세요.")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
    private String email;

    @NotBlank(message = "비밀번호를 입력해 주세요.")
    @Size(min = 8, max = 72, message = "비밀번호는 8자 이상 72자 이하여야 합니다.")
    private String password;

    @NotBlank(message = "담당자 이름을 입력해 주세요.")
    @Size(max = 50, message = "담당자 이름은 50자 이하여야 합니다.")
    private String managerName;

    @NotBlank(message = "담당자 연락처를 입력해 주세요.")
    @Size(max = 30, message = "담당자 연락처는 30자 이하여야 합니다.")
    private String managerPhone;

    @NotBlank(message = "사업자등록번호를 입력해 주세요.")
    @Pattern(regexp = "^[0-9]{10}$", message = "사업자등록번호는 숫자 10자리여야 합니다.")
    private String businessNumber;

    @NotBlank(message = "개업일자를 입력해 주세요.")
    @Pattern(regexp = "^[0-9]{8}$", message = "개업일자는 YYYYMMDD 형식이어야 합니다.")
    private String startDate;

    @NotBlank(message = "대표자명을 입력해 주세요.")
    @Size(max = 50, message = "대표자명은 50자 이하여야 합니다.")
    private String representativeName;

    @NotBlank(message = "회사명을 입력해 주세요.")
    @Size(max = 50, message = "회사명은 50자 이하여야 합니다.")
    private String organizationName;

    @NotBlank(message = "회사 대표 이메일을 입력해 주세요.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    @Size(max = 255, message = "이메일은 255자 이하여야 합니다.")
    private String contactEmail;

    @NotBlank(message = "회사 대표 전화번호를 입력해 주세요.")
    @Size(max = 30, message = "전화번호는 30자 이하여야 합니다.")
    private String contactPhone;

    @Size(max = 10, message = "우편번호는 10자 이하여야 합니다.")
    private String postalCode;

    @Size(max = 300, message = "기본 주소는 300자 이하여야 합니다.")
    private String addressLine1;

    @Size(max = 300, message = "상세 주소는 300자 이하여야 합니다.")
    private String addressLine2;

    @Pattern(regexp = "^$|^https?://.+", message = "홈페이지 URL은 http:// 또는 https://로 시작해야 합니다.")
    @Size(max = 500, message = "홈페이지 URL은 500자 이하여야 합니다.")
    private String homepageUrl;

    @Size(max = 1000, message = "회사 소개는 1000자 이하여야 합니다.")
    private String introduction;

    @AssertTrue(message = "비밀번호는 UTF-8 기준 72바이트 이하여야 합니다.")
    public boolean isPasswordUtf8LengthValid() {
        if (password == null) {
            return true;
        }

        return password.getBytes(StandardCharsets.UTF_8).length <= 72;
    }
}
