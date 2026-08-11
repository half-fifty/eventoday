package com.min.edu.admission.service;

import com.min.edu.admission.domain.AdmissionTicket;
import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.dto.ExchangeCodeDtos;
import com.min.edu.admission.dto.ExchangeCodeRedemptionDtos;
import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.admission.support.AdmissionQrTokenGenerator;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.payment.service.GuestOrderAccessService;
import com.min.edu.payment.service.GuestTicketOrderAccess;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class ExchangeCodeRedemptionService {

    private final ExchangeCodeRepository exchangeCodeRepository;
    private final EventRepository eventRepository;
    private final AdmissionTicketRepository admissionTicketRepository;
    private final AdmissionQrTokenGenerator admissionQrTokenGenerator;
    private final GuestOrderAccessService guestOrderAccessService;

    public ExchangeCodeRedemptionService(
            ExchangeCodeRepository exchangeCodeRepository,
            EventRepository eventRepository,
            AdmissionTicketRepository admissionTicketRepository,
            AdmissionQrTokenGenerator admissionQrTokenGenerator,
            GuestOrderAccessService guestOrderAccessService) {
        this.exchangeCodeRepository = exchangeCodeRepository;
        this.eventRepository = eventRepository;
        this.admissionTicketRepository = admissionTicketRepository;
        this.admissionQrTokenGenerator = admissionQrTokenGenerator;
        this.guestOrderAccessService = guestOrderAccessService;
    }

    @Transactional(readOnly = true)
    public ExchangeCodeRedemptionDtos.ValidationResponse validate(
            ExchangeCodeRedemptionDtos.Request request,
            AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        validateRequest(request);

        OffsetDateTime now = OffsetDateTime.now();
        ExchangeCode exchangeCode = findExchangeCode(request.code());
        Event event = findEvent(exchangeCode.getEventId());
        validateRedeemable(exchangeCode, event, actor.getMemberId(), now);

        return new ExchangeCodeRedemptionDtos.ValidationResponse(
            true,
            event.getId(),
            event.getName(),
            source(exchangeCode),
            exchangeCode.getStatus(),
            exchangeCode.getExpiresAt()
        );
    }

    @Transactional
    public ExchangeCodeRedemptionDtos.RedemptionResponse redeem(
            ExchangeCodeRedemptionDtos.Request request,
            AuthenticatedMemberDto actor) {
        requireAuthenticated(actor);
        validateRequest(request);

        OffsetDateTime now = OffsetDateTime.now();
        ExchangeCode exchangeCode = exchangeCodeRepository.findByCodeForUpdate(request.code())
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_NOT_FOUND));
        Event event = findEvent(exchangeCode.getEventId());
        validateRedeemable(exchangeCode, event, actor.getMemberId(), now);
        validateNoAdmissionTicket(exchangeCode.getId());

        if (isExternalRequest(exchangeCode) && exchangeCode.getHolderMemberId() == null) {
            exchangeCode.assignHolder(actor.getMemberId(), now);
        }

        return issueAdmissionTicket(exchangeCode, event, actor.getMemberId(), now);
    }

    @Transactional
    public ExchangeCodeRedemptionDtos.RedemptionResponse redeemGuestOrderExchangeCode(
            String orderNo,
            String orderAccessToken,
            Long exchangeCodeId) {
        GuestTicketOrderAccess access =
            guestOrderAccessService.validateGuestTicketOrderAccess(orderNo, orderAccessToken);
        OffsetDateTime now = OffsetDateTime.now();
        ExchangeCode exchangeCode = exchangeCodeRepository.findByIdForUpdate(exchangeCodeId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_NOT_FOUND));
        Event event = findEvent(exchangeCode.getEventId());
        validateGuestRedeemable(exchangeCode, access, event, now);
        validateNoAdmissionTicket(exchangeCode.getId());

        return issueAdmissionTicket(exchangeCode, event, null, now);
    }

    private void requireAuthenticated(AuthenticatedMemberDto actor) {
        if (actor == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
    }

    private void validateRequest(ExchangeCodeRedemptionDtos.Request request) {
        if (request == null || request.code() == null || request.code().isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private ExchangeCode findExchangeCode(String code) {
        return exchangeCodeRepository.findByCode(code)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EXCHANGE_CODE_NOT_FOUND));
    }

    private Event findEvent(Long eventId) {
        return eventRepository.findById(eventId)
            .orElseThrow(() -> new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));
    }

    private void validateNoAdmissionTicket(Long exchangeCodeId) {
        if (admissionTicketRepository.existsByExchangeCodeId(exchangeCodeId)) {
            throw new BusinessException(GlobalErrorCode.ADMISSION_TICKET_ALREADY_EXISTS);
        }
    }

    private void validateRedeemable(
            ExchangeCode exchangeCode,
            Event event,
            Long actorMemberId,
            OffsetDateTime now) {
        if (exchangeCode.getStatus() != ExchangeCodeStatus.ISSUED) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_INVALID_STATE);
        }
        if (exchangeCode.getExpiresAt() != null && !exchangeCode.getExpiresAt().isAfter(now)) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_EXPIRED);
        }
        if (event.getStatus() != EventStatus.PUBLISHED || !event.getEndAt().isAfter(now)) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_EVENT_NOT_REDEEMABLE);
        }

        validateHolder(exchangeCode, actorMemberId);
    }

    private void validateGuestRedeemable(
            ExchangeCode exchangeCode,
            GuestTicketOrderAccess access,
            Event event,
            OffsetDateTime now) {
        if (!isTicketOrder(exchangeCode)
                || !access.ticketOrderId().equals(exchangeCode.getTicketOrderId())
                || exchangeCode.getHolderMemberId() != null) {
            throw new BusinessException(GlobalErrorCode.ORDER_ACCESS_DENIED);
        }
        if (exchangeCode.getStatus() != ExchangeCodeStatus.ISSUED) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_INVALID_STATE);
        }
        if (exchangeCode.getExpiresAt() != null && !exchangeCode.getExpiresAt().isAfter(now)) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_EXPIRED);
        }
        if (event.getStatus() != EventStatus.PUBLISHED || !event.getEndAt().isAfter(now)) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_EVENT_NOT_REDEEMABLE);
        }
    }

    private ExchangeCodeRedemptionDtos.RedemptionResponse issueAdmissionTicket(
            ExchangeCode exchangeCode,
            Event event,
            Long memberId,
            OffsetDateTime now) {
        transition(() -> exchangeCode.redeem(now));

        AdmissionTicket admissionTicket = admissionTicketRepository.saveAndFlush(
            AdmissionTicket.issue(
                exchangeCode.getId(),
                memberId,
                admissionQrTokenGenerator.generate(),
                now
            )
        );

        return new ExchangeCodeRedemptionDtos.RedemptionResponse(
            exchangeCode.getId(),
            exchangeCode.getStatus(),
            admissionTicket.getId(),
            event.getId(),
            event.getName(),
            admissionTicket.getStatus(),
            admissionTicket.getIssuedAt(),
            admissionTicket.getQrToken() != null
        );
    }

    private void validateHolder(ExchangeCode exchangeCode, Long actorMemberId) {
        if (isTicketOrder(exchangeCode)) {
            if (exchangeCode.getHolderMemberId() == null) {
                throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_GUEST_NOT_REDEEMABLE);
            }
            if (!exchangeCode.getHolderMemberId().equals(actorMemberId)) {
                throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_HOLDER_MISMATCH);
            }
            return;
        }

        if (!isExternalRequest(exchangeCode)) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_INVALID_STATE);
        }

        if (exchangeCode.getHolderMemberId() != null
                && !exchangeCode.getHolderMemberId().equals(actorMemberId)) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_HOLDER_MISMATCH);
        }
    }

    private ExchangeCodeDtos.Source source(ExchangeCode exchangeCode) {
        if (isTicketOrder(exchangeCode)) {
            return ExchangeCodeDtos.Source.TICKET_ORDER;
        }
        if (isExternalRequest(exchangeCode)) {
            return ExchangeCodeDtos.Source.EXTERNAL_REQUEST;
        }
        throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_INVALID_STATE);
    }

    private boolean isTicketOrder(ExchangeCode exchangeCode) {
        return exchangeCode.getTicketOrderId() != null;
    }

    private boolean isExternalRequest(ExchangeCode exchangeCode) {
        return exchangeCode.getExchangeCodeRequestId() != null;
    }

    private void transition(Runnable action) {
        try {
            action.run();
        } catch (IllegalStateException exception) {
            throw new BusinessException(GlobalErrorCode.EXCHANGE_CODE_INVALID_STATE);
        }
    }
}
