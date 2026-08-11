package com.min.edu.payment.dto.response;

import java.time.OffsetDateTime;

public record GuestOrderAccessTokenResponse(
        String orderNo,
        String orderAccessToken,
        OffsetDateTime expiresAt) {
}
