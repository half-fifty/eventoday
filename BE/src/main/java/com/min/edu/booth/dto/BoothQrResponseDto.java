package com.min.edu.booth.dto;

import java.time.OffsetDateTime;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class BoothQrResponseDto {

    private Long boothId;
    private String qrToken;
    private OffsetDateTime qrIssuedAt;
}
