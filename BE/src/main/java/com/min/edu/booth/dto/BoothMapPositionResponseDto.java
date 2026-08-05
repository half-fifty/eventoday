package com.min.edu.booth.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@AllArgsConstructor
public class BoothMapPositionResponseDto {

    private Long boothId;
    private String boothCode;
    private String displayName;
    private BigDecimal xRatio;
    private BigDecimal yRatio;
}
