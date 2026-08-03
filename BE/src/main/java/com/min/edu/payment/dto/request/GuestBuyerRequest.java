package com.min.edu.payment.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class GuestBuyerRequest {

    @NotBlank(message = "구매자 이름은 필수입니다.")
    private String name;

    @NotBlank(message = "구매자 이메일은 필수입니다.")
    @Email(message = "이메일 형식이 올바르지 않습니다.")
    private String email;

    @NotBlank(message = "구매자 전화번호는 필수입니다.")
    @Pattern(regexp = "^[0-9-]+$", message = "전화번호 형식이 올바르지 않습니다.")
    private String phone;
}
