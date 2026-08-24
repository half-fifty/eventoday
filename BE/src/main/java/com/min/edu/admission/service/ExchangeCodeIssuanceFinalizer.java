package com.min.edu.admission.service;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeRequest;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.admission.repository.ExchangeCodeRequestRepository;
import com.min.edu.admission.support.ExchangeCodeGenerator;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.Member;
import com.min.edu.member.repository.MemberRepository;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class ExchangeCodeIssuanceFinalizer {

    private final ExchangeCodeRequestRepository exchangeCodeRequestRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final EventRepository eventRepository;
    private final MemberRepository memberRepository;
    private final ExchangeCodeGenerator exchangeCodeGenerator;

    public ExchangeCodeIssuanceFinalizer(
            ExchangeCodeRequestRepository exchangeCodeRequestRepository,
            ExchangeCodeRepository exchangeCodeRepository,
            EventRepository eventRepository,
            MemberRepository memberRepository,
            ExchangeCodeGenerator exchangeCodeGenerator) {
        this.exchangeCodeRequestRepository = exchangeCodeRequestRepository;
        this.exchangeCodeRepository = exchangeCodeRepository;
        this.eventRepository = eventRepository;
        this.memberRepository = memberRepository;
        this.exchangeCodeGenerator = exchangeCodeGenerator;
    }

    @Transactional
    public ExchangeCodeIssuanceResult issue(Long requestId) {
        ExchangeCodeRequest request = exchangeCodeRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_NOT_FOUND));

        long existingCount = exchangeCodeRepository.countByExchangeCodeRequestId(requestId);
        validateIssuable(request, existingCount);

        return issueLocked(request, existingCount);
    }

    @Transactional
    public ExchangeCodeIssuanceResult issueOrPrepareEmail(Long requestId) {
        ExchangeCodeRequest request = exchangeCodeRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_NOT_FOUND));

        long existingCount = exchangeCodeRepository.countByExchangeCodeRequestId(requestId);
        if (request.isIssued()) {
            validateResendable(request);
            return prepareEmailResendLocked(request, existingCount);
        }
        validateIssuable(request, existingCount);
        return issueLocked(request, existingCount);
    }

    private ExchangeCodeIssuanceResult issueLocked(
            ExchangeCodeRequest request,
            long existingCount) {
        Long requestId = request.getId();

        Event event = eventRepository.findById(request.getEventId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
        OffsetDateTime now = OffsetDateTime.now();
        validateEventNotEnded(event, now);

        Member recipient = memberRepository.findById(request.getRequestedBy())
            .orElseThrow(() -> new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_RECIPIENT_NOT_FOUND
            ));
        validateRecipientEmail(recipient.getEmail());

        List<ExchangeCode> exchangeCodes = createExchangeCodes(request, event, now);
        exchangeCodeRepository.saveAllAndFlush(exchangeCodes);

        long savedCount = exchangeCodeRepository.countByExchangeCodeRequestId(requestId);
        if (savedCount != request.getRequestedQuantity()) {
            throw new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_ISSUANCE_INCONSISTENT
            );
        }

        transition(() -> request.issue());

        return new ExchangeCodeIssuanceResult(
            request.getId(),
            request.getEventId(),
            event.getName(),
            request.getRequestedQuantity(),
            exchangeCodes.size(),
            recipient.getEmail(),
            exchangeCodes.stream().map(ExchangeCode::getCode).toList(),
            event.getEndAt(),
            request.getStatus(),
            request.getEmailedAt()
        );
    }

    @Transactional
    public ExchangeCodeIssuanceResult prepareEmailResend(Long requestId) {
        ExchangeCodeRequest request = exchangeCodeRequestRepository.findByIdForUpdate(requestId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_NOT_FOUND));

        validateResendable(request);

        long existingCount = exchangeCodeRepository.countByExchangeCodeRequestId(requestId);
        return prepareEmailResendLocked(request, existingCount);
    }

    private ExchangeCodeIssuanceResult prepareEmailResendLocked(
            ExchangeCodeRequest request,
            long existingCount) {
        Long requestId = request.getId();

        Event event = eventRepository.findById(request.getEventId())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
        Member recipient = memberRepository.findById(request.getRequestedBy())
            .orElseThrow(() -> new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_RECIPIENT_NOT_FOUND
            ));
        validateRecipientEmail(recipient.getEmail());

        if (existingCount != request.getRequestedQuantity()) {
            throw new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_ISSUANCE_INCONSISTENT
            );
        }

        List<ExchangeCode> exchangeCodes =
            exchangeCodeRepository.findAllByExchangeCodeRequestIdOrderByIdAsc(requestId);
        if (exchangeCodes.size() != request.getRequestedQuantity()) {
            throw new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_ISSUANCE_INCONSISTENT
            );
        }

        return new ExchangeCodeIssuanceResult(
            request.getId(),
            request.getEventId(),
            event.getName(),
            request.getRequestedQuantity(),
            exchangeCodes.size(),
            recipient.getEmail(),
            exchangeCodes.stream().map(ExchangeCode::getCode).toList(),
            event.getEndAt(),
            request.getStatus(),
            request.getEmailedAt()
        );
    }

    private void validateIssuable(ExchangeCodeRequest request, long existingCount) {
        if (request.isIssued()) {
            if (existingCount != request.getRequestedQuantity()) {
                throw new BusinessException(
                    GlobalErrorCode.EXCHANGE_CODE_REQUEST_ISSUANCE_INCONSISTENT
                );
            }
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_ALREADY_ISSUED);
        }
        if (!request.isApproved()) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE);
        }
        if (existingCount > 0) {
            throw new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_ISSUANCE_INCONSISTENT
            );
        }
    }

    private void validateResendable(ExchangeCodeRequest request) {
        if (!request.isIssued()) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE);
        }
        if (request.getEmailedAt() != null) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EMAIL_ALREADY_SENT);
        }
    }

    private void validateEventNotEnded(Event event, OffsetDateTime now) {
        if (!event.getEndAt().isAfter(now)) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_EVENT_ENDED);
        }
    }

    private void validateRecipientEmail(String email) {
        if (email == null || email.isBlank()) {
            throw new BusinessException(
                GlobalErrorCode.EXCHANGE_CODE_REQUEST_RECIPIENT_EMAIL_MISSING
            );
        }
    }

    private List<ExchangeCode> createExchangeCodes(
            ExchangeCodeRequest request,
            Event event,
            OffsetDateTime now) {
        List<ExchangeCode> exchangeCodes = new ArrayList<>();
        for (int i = 0; i < request.getRequestedQuantity(); i++) {
            exchangeCodes.add(ExchangeCode.createForExchangeCodeRequest(
                request.getEventId(),
                request.getId(),
                exchangeCodeGenerator.generate(),
                event.getEndAt(),
                now
            ));
        }
        return exchangeCodes;
    }

    private void transition(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_REQUEST_INVALID_STATE);
        }
    }
}
