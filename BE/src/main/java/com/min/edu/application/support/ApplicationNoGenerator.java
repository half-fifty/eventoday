package com.min.edu.application.support;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.min.edu.booth.repository.BoothApplicationRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;

import lombok.RequiredArgsConstructor;

/**
 * 신청번호 생성기 - OrderNoGenerator 패턴과 동일
 * 형식: "APP-yyyyMMdd-랜덤16진수(16자)"
 */
@Component
@RequiredArgsConstructor
public class ApplicationNoGenerator {

    private static final int MAX_RETRY_COUNT = 5;
    private static final int RANDOM_HEX_LENGTH = 16;
    private static final DateTimeFormatter DATE_FORMATTER = DateTimeFormatter.BASIC_ISO_DATE;

    private final BoothApplicationRepository boothApplicationRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        for (int attempt = 0; attempt < MAX_RETRY_COUNT; attempt++) {
            String applicationNo = createCandidate();

            // 중복 체크: 동일 번호가 이미 있으면 재시도
            if (!boothApplicationRepository.existsByApplicationNo(applicationNo)) {
                return applicationNo;
            }
        }

        throw new BusinessException(GlobalErrorCode.APPLICATION_NUMBER_GENERATION_FAILED);
    }

    private String createCandidate() {
        return "APP-"
                + LocalDate.now().format(DATE_FORMATTER)
                + "-"
                + randomHex();
    }

    private String randomHex() {
        StringBuilder builder = new StringBuilder(RANDOM_HEX_LENGTH);

        while (builder.length() < RANDOM_HEX_LENGTH) {
            builder.append(Integer.toHexString(secureRandom.nextInt(16)).toUpperCase());
        }

        return builder.toString();
    }
}