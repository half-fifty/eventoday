package com.min.edu.admission.support;

import java.security.SecureRandom;

import org.springframework.stereotype.Component;

import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class ExchangeCodeGenerator {

    private static final int MAX_RETRY_COUNT = 5;
    private static final int CODE_GROUP_COUNT = 3;
    private static final int CODE_GROUP_LENGTH = 4;

    private final ExchangeCodeRepository exchangeCodeRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        for (int attempt = 0; attempt < MAX_RETRY_COUNT; attempt++) {
            String code = createCandidate();

            if (!exchangeCodeRepository.existsByCode(code)) {
                return code;
            }
        }

        throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_GENERATION_FAILED);
    }

    private String createCandidate() {
        StringBuilder builder = new StringBuilder();

        for (int group = 0; group < CODE_GROUP_COUNT; group++) {
            if (group > 0) {
                builder.append("-");
            }

            for (int i = 0; i < CODE_GROUP_LENGTH; i++) {
                builder.append(Integer.toHexString(secureRandom.nextInt(16)).toUpperCase());
            }
        }

        return builder.toString();
    }
}
