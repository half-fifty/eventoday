package com.min.edu.admission.service;

import com.min.edu.admission.domain.ExchangeCodeRequest;
import com.min.edu.admission.repository.ExchangeCodeRequestRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExchangeCodeRequestEmailRecorder {

    private final ExchangeCodeRequestRepository exchangeCodeRequestRepository;

    public ExchangeCodeRequestEmailRecorder(
            ExchangeCodeRequestRepository exchangeCodeRequestRepository) {
        this.exchangeCodeRequestRepository = exchangeCodeRequestRepository;
    }

    @Transactional
    public OffsetDateTime markEmailed(Long requestId) {
        ExchangeCodeRequest request = exchangeCodeRequestRepository.findById(requestId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_NOT_FOUND));
        OffsetDateTime now = OffsetDateTime.now();
        transition(() -> request.markEmailed(now));
        return now;
    }

    private void transition(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE);
        }
    }
}
