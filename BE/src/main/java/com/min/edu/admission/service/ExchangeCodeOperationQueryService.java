package com.min.edu.admission.service;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.service.EventOperationAccessService;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Transactional(readOnly = true)
@RequiredArgsConstructor
public class ExchangeCodeOperationQueryService {

    private final ExchangeCodeRepository exchangeCodeRepository;
    private final AdmissionTicketRepository admissionTicketRepository;
    private final EventOperationAccessService eventOperationAccessService;

    public ExchangeCodeOperationView getExchangeCodeStatus(
            Long eventId,
            Long memberId,
            Long exchangeCodeId) {
        eventOperationAccessService.requireOperationalAccess(eventId, memberId);
        if (exchangeCodeId == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        ExchangeCode exchangeCode = exchangeCodeRepository.findById(exchangeCodeId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_NOT_FOUND));
        if (!eventId.equals(exchangeCode.getEventId())) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
        boolean admissionTicketIssued =
            admissionTicketRepository.existsByExchangeCodeId(exchangeCode.getId());
        return ExchangeCodeOperationView.from(exchangeCode, admissionTicketIssued);
    }

    public record ExchangeCodeOperationView(
            Long exchangeCodeId,
            String maskedCode,
            ExchangeCodeStatus status,
            Source source,
            OffsetDateTime expiresAt,
            OffsetDateTime redeemedAt,
            boolean admissionTicketIssued) {

        static ExchangeCodeOperationView from(
                ExchangeCode exchangeCode,
                boolean admissionTicketIssued) {
            return new ExchangeCodeOperationView(
                exchangeCode.getId(),
                mask(exchangeCode.getCode()),
                exchangeCode.getStatus(),
                source(exchangeCode),
                exchangeCode.getExpiresAt(),
                exchangeCode.getRedeemedAt(),
                admissionTicketIssued
            );
        }

        private static Source source(ExchangeCode exchangeCode) {
            if (exchangeCode.getTicketOrderId() != null) {
                return Source.TICKET_ORDER;
            }
            return Source.EXTERNAL_REQUEST;
        }

        private static String mask(String code) {
            if (code == null || code.isBlank()) {
                return "";
            }
            if (code.length() <= 2) {
                return "*".repeat(code.length());
            }
            if (code.length() <= 8) {
                return code.charAt(0)
                    + "*".repeat(code.length() - 2)
                    + code.charAt(code.length() - 1);
            }
            return code.substring(0, 4)
                + "*".repeat(code.length() - 8)
                + code.substring(code.length() - 4);
        }

        public enum Source {
            TICKET_ORDER,
            EXTERNAL_REQUEST
        }
    }
}
