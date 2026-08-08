package com.min.edu.admission.support;

import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.security.SecureRandom;
import java.util.Base64;
import org.springframework.stereotype.Component;

@Component
public class AdmissionQrTokenGenerator {

    private static final int MAX_RETRY_COUNT = 5;
    private static final int TOKEN_BYTE_LENGTH = 24;

    private final AdmissionTicketRepository admissionTicketRepository;
    private final SecureRandom secureRandom = new SecureRandom();

    public AdmissionQrTokenGenerator(AdmissionTicketRepository admissionTicketRepository) {
        this.admissionTicketRepository = admissionTicketRepository;
    }

    public String generate() {
        for (int attempt = 0; attempt < MAX_RETRY_COUNT; attempt++) {
            String token = createCandidate();

            if (!admissionTicketRepository.existsByQrToken(token)) {
                return token;
            }
        }

        throw new BusinessException(GlobalErrorCode.ADMISSION_QR_TOKEN_GENERATION_FAILED);
    }

    private String createCandidate() {
        byte[] bytes = new byte[TOKEN_BYTE_LENGTH];
        secureRandom.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
