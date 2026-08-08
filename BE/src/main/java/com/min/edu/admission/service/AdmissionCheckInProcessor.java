package com.min.edu.admission.service;

import com.min.edu.admission.domain.AdmissionAction;
import com.min.edu.admission.domain.AdmissionLog;
import com.min.edu.admission.domain.AdmissionResult;
import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.AdmissionTicketStatus;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.AdmissionLogRepository;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.payment.config.PaymentFinalizationProperties;
import jakarta.persistence.EntityManager;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class AdmissionCheckInProcessor {

    private final AdmissionTicketRepository admissionTicketRepository;
    private final ExchangeCodeRepository exchangeCodeRepository;
    private final AdmissionLogRepository admissionLogRepository;
    private final EntityManager entityManager;
    private final PaymentFinalizationProperties paymentFinalizationProperties;

    public AdmissionCheckInProcessor(
            AdmissionTicketRepository admissionTicketRepository,
            ExchangeCodeRepository exchangeCodeRepository,
            AdmissionLogRepository admissionLogRepository,
            EntityManager entityManager,
            PaymentFinalizationProperties paymentFinalizationProperties) {
        this.admissionTicketRepository = admissionTicketRepository;
        this.exchangeCodeRepository = exchangeCodeRepository;
        this.admissionLogRepository = admissionLogRepository;
        this.entityManager = entityManager;
        this.paymentFinalizationProperties = paymentFinalizationProperties;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessResult checkIn(
            Long eventId,
            Event event,
            String qrToken,
            String gateName,
            Long staffMemberId,
            OffsetDateTime now) {
        setLocalLockTimeout();
        AdmissionTicket ticket = admissionTicketRepository.findByQrTokenForUpdate(qrToken)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));
        ExchangeCode exchangeCode = findExchangeCode(ticket.getExchangeCodeId());
        validateEventMatch(eventId, exchangeCode);

        if (event.getStatus() != EventStatus.PUBLISHED
                || !event.getEndAt().isAfter(now)) {
            AdmissionLog log = saveLog(
                ticket.getId(),
                staffMemberId,
                AdmissionAction.CHECK_IN,
                AdmissionResult.INVALID,
                gateName,
                now
            );
            return ProcessResult.invalid(ticket, exchangeCode, event, log);
        }
        if (ticket.getStatus() == AdmissionTicketStatus.USED) {
            AdmissionLog log = saveLog(
                ticket.getId(),
                staffMemberId,
                AdmissionAction.CHECK_IN,
                AdmissionResult.DUPLICATE,
                gateName,
                now
            );
            return ProcessResult.duplicate(ticket, exchangeCode, event, log);
        }
        if (ticket.getStatus() != AdmissionTicketStatus.ISSUED) {
            AdmissionLog log = saveLog(
                ticket.getId(),
                staffMemberId,
                AdmissionAction.CHECK_IN,
                AdmissionResult.INVALID,
                gateName,
                now
            );
            return ProcessResult.invalid(ticket, exchangeCode, event, log);
        }

        transition(ticket::checkIn, now);
        AdmissionLog log = saveLog(
            ticket.getId(),
            staffMemberId,
            AdmissionAction.CHECK_IN,
            AdmissionResult.SUCCESS,
            gateName,
            now
        );
        return ProcessResult.success(ticket, exchangeCode, event, log);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public ProcessResult cancelCheckIn(
            Long eventId,
            Event event,
            Long admissionTicketId,
            Long staffMemberId,
            OffsetDateTime now) {
        setLocalLockTimeout();
        AdmissionTicket ticket = admissionTicketRepository.findByIdForUpdate(admissionTicketId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.ADMISSION_TICKET_NOT_FOUND));
        ExchangeCode exchangeCode = findExchangeCode(ticket.getExchangeCodeId());
        validateEventMatch(eventId, exchangeCode);

        if (ticket.getStatus() != AdmissionTicketStatus.USED) {
            AdmissionLog log = saveLog(
                ticket.getId(),
                staffMemberId,
                AdmissionAction.CHECK_IN_CANCEL,
                AdmissionResult.INVALID,
                null,
                now
            );
            return ProcessResult.cancelInvalid(ticket, exchangeCode, event, log);
        }

        transition(ticket::cancelCheckIn);
        AdmissionLog log = saveLog(
            ticket.getId(),
            staffMemberId,
            AdmissionAction.CHECK_IN_CANCEL,
            AdmissionResult.SUCCESS,
            null,
            now
        );
        return ProcessResult.success(ticket, exchangeCode, event, log);
    }

    private ExchangeCode findExchangeCode(Long exchangeCodeId) {
        return exchangeCodeRepository.findById(exchangeCodeId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_NOT_FOUND));
    }

    private void validateEventMatch(Long eventId, ExchangeCode exchangeCode) {
        if (!eventId.equals(exchangeCode.getEventId())) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_CHECK_IN_EVENT_MISMATCH);
        }
    }

    private AdmissionLog saveLog(
            Long admissionTicketId,
            Long staffMemberId,
            AdmissionAction action,
            AdmissionResult result,
            String gateName,
            OffsetDateTime processedAt) {
        return admissionLogRepository.save(AdmissionLog.builder()
            .admissionTicketId(admissionTicketId)
            .staffMemberId(staffMemberId)
            .action(action)
            .result(result)
            .gateName(gateName)
            .processedAt(processedAt)
            .build());
    }

    private void setLocalLockTimeout() {
        entityManager
            .createNativeQuery("select set_config('lock_timeout', :timeout, true)")
            .setParameter("timeout", paymentFinalizationProperties.getFinalizationLockTimeoutMs() + "ms")
            .getSingleResult();
    }

    private void transition(CheckInTransition transition, OffsetDateTime now) {
        try {
            transition.apply(now);
        } catch (IllegalStateException exception) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_CHECK_IN_INVALID_STATE);
        }
    }

    private void transition(CancelTransition transition) {
        try {
            transition.apply();
        } catch (IllegalStateException exception) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_CHECK_IN_CANCEL_INVALID_STATE);
        }
    }

    @FunctionalInterface
    private interface CheckInTransition {
        void apply(OffsetDateTime now);
    }

    @FunctionalInterface
    private interface CancelTransition {
        void apply();
    }

    public record ProcessResult(
            Outcome outcome,
            Long admissionTicketId,
            Long eventId,
            String eventName,
            AdmissionTicketStatus status,
            OffsetDateTime usedAt,
            Long admissionLogId,
            AdmissionAction action,
            AdmissionResult result,
            OffsetDateTime processedAt) {

        static ProcessResult success(
                AdmissionTicket ticket,
                ExchangeCode exchangeCode,
                Event event,
                AdmissionLog log) {
            return of(Outcome.SUCCESS, ticket, exchangeCode, event, log);
        }

        static ProcessResult duplicate(
                AdmissionTicket ticket,
                ExchangeCode exchangeCode,
                Event event,
                AdmissionLog log) {
            return of(Outcome.DUPLICATE, ticket, exchangeCode, event, log);
        }

        static ProcessResult invalid(
                AdmissionTicket ticket,
                ExchangeCode exchangeCode,
                Event event,
                AdmissionLog log) {
            return of(Outcome.INVALID, ticket, exchangeCode, event, log);
        }

        static ProcessResult cancelInvalid(
                AdmissionTicket ticket,
                ExchangeCode exchangeCode,
                Event event,
                AdmissionLog log) {
            return of(Outcome.CANCEL_INVALID, ticket, exchangeCode, event, log);
        }

        private static ProcessResult of(
                Outcome outcome,
                AdmissionTicket ticket,
                ExchangeCode exchangeCode,
                Event event,
                AdmissionLog log) {
            return new ProcessResult(
                outcome,
                ticket.getId(),
                exchangeCode.getEventId(),
                event.getName(),
                ticket.getStatus(),
                ticket.getUsedAt(),
                log.getId(),
                log.getAction(),
                log.getResult(),
                log.getProcessedAt()
            );
        }
    }

    public enum Outcome {
        SUCCESS,
        DUPLICATE,
        INVALID,
        CANCEL_INVALID
    }
}
