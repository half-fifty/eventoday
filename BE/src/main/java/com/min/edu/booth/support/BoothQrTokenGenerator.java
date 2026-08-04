package com.min.edu.booth.support;

import java.security.SecureRandom;
import java.util.Base64;

import org.springframework.stereotype.Component;

import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class BoothQrTokenGenerator {

    private static final int MAX_RETRY_COUNT = 5;
    private static final int TOKEN_BYTE_LENGTH = 24;

    private final BoothRepository boothRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        for (int attempt = 0; attempt < MAX_RETRY_COUNT; attempt++) {
            String token = createCandidate();

            if (!boothRepository.existsByQrToken(token)) {
                return token;
            }
        }

        throw new BusinessException(GlobalErrorCode.BOOTH_QR_GENERATION_FAILED);
    }

    private String createCandidate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
