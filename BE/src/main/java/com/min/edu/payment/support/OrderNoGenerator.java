package com.min.edu.payment.support;

import java.security.SecureRandom;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

import org.springframework.stereotype.Component;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.repository.PaymentOrderRepository;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class OrderNoGenerator {

    private static final int MAX_RETRY_COUNT = 5;
    private static final int RANDOM_HEX_LENGTH = 16;
    private static final DateTimeFormatter DATE_FORMATTER =
        DateTimeFormatter.BASIC_ISO_DATE;

    private final PaymentOrderRepository paymentOrderRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public String generate() {
        for (int attempt = 0; attempt < MAX_RETRY_COUNT; attempt++) {
            String orderNo = createCandidate();

            if (!paymentOrderRepository.existsByOrderNo(orderNo)) {
                return orderNo;
            }
        }

        throw new BusinessException(GlobalErrorCode.ORDER_NUMBER_GENERATION_FAILED);
    }

    private String createCandidate() {
        return "EVT-"
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
