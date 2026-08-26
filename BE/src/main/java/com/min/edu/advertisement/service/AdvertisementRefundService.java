package com.min.edu.advertisement.service;

import com.min.edu.advertisement.domain.Advertisement;
import com.min.edu.advertisement.domain.AdvertisementStatus;
import com.min.edu.advertisement.dto.AdvertisementDtos;
import com.min.edu.advertisement.repository.AdvertisementRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentAuditActorType;
import com.min.edu.payment.domain.PaymentAuditEventType;
import com.min.edu.payment.domain.PaymentAuditSource;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.service.PaymentAuditLogWriter;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossCancelRequest;
import com.min.edu.payment.toss.dto.TossCancelResponse;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

@Service
public class AdvertisementRefundService {
    private static final String ALREADY_CANCELED_PAYMENT = "ALREADY_CANCELED_PAYMENT";
    private final AdvertisementRepository advertisementRepository;
    private final PaymentOrderRepository paymentOrderRepository;
    private final PaymentRepository paymentRepository;
    private final PaymentRefundRepository paymentRefundRepository;
    private final TossPaymentClient tossPaymentClient;
    private final PaymentAuditLogWriter auditLogWriter;
    private final TransactionTemplate transaction;

    public AdvertisementRefundService(AdvertisementRepository advertisementRepository,
            PaymentOrderRepository paymentOrderRepository, PaymentRepository paymentRepository,
            PaymentRefundRepository paymentRefundRepository, TossPaymentClient tossPaymentClient,
            PaymentAuditLogWriter auditLogWriter, PlatformTransactionManager transactionManager) {
        this.advertisementRepository = advertisementRepository;
        this.paymentOrderRepository = paymentOrderRepository;
        this.paymentRepository = paymentRepository;
        this.paymentRefundRepository = paymentRefundRepository;
        this.tossPaymentClient = tossPaymentClient;
        this.auditLogWriter = auditLogWriter;
        this.transaction = new TransactionTemplate(transactionManager);
        this.transaction.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
    }

    public AdvertisementDtos.CancellationResponse cancel(
            Long advertisementId, Long requesterMemberId) {
        AdvertisementStatus status = transaction.execute(ignored -> {
            Advertisement ad = getAdvertisement(advertisementId);
            if (ad.getStatus() == AdvertisementStatus.PAYMENT_PENDING) {
                OffsetDateTime now = OffsetDateTime.now();
                PaymentOrder order = paymentOrderRepository.findByIdForUpdate(ad.getPaymentOrderId())
                        .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT));
                if (order.isPending() || order.isWaitingForDeposit()) {
                    String fromStatus = order.getStatus();
                    order.expire(now);
                    auditLogWriter.append(order.getId(), null, null,
                            PaymentAuditEventType.PAYMENT_EXPIRED, fromStatus, order.getStatus(),
                            PaymentAuditSource.REFUND, "ADVERTISEMENT_CANCELLED_BEFORE_PAYMENT",
                            PaymentAuditActorType.fromRequester(requesterMemberId), requesterMemberId,
                            null, now);
                }
                ad.cancel(now);
                return AdvertisementStatus.CANCELLED;
            }
            if (ad.getStatus() == AdvertisementStatus.ACTIVE) {
                ad.stop(OffsetDateTime.now());
                return AdvertisementStatus.STOPPED;
            }
            if (ad.getStatus() == AdvertisementStatus.REVISION_PENDING
                    && !ad.getStartAt().isAfter(OffsetDateTime.now())) {
                ad.stopRevision(OffsetDateTime.now());
                return AdvertisementStatus.STOPPED;
            }
            if (!isRefundable(ad.getStatus())) {
                throw new BusinessException(GlobalErrorCode.REFUND_NOT_ALLOWED);
            }
            return ad.getStatus();
        });

        if (status == AdvertisementStatus.CANCELLED) {
            return response("CANCELLED", status, false, "결제 전 광고 신청을 취소했습니다.");
        }
        if (status == AdvertisementStatus.STOPPED) {
            return response("STOPPED", status, false,
                    "광고 노출을 중단했습니다. 이미 노출이 시작되어 환불되지 않습니다.");
        }
        return refund(advertisementId, requesterMemberId, "광고주 요청에 따른 광고 취소");
    }

    public AdvertisementDtos.CancellationResponse rejectAndRefund(
            Long advertisementId, Long reviewerId, String reason) {
        RejectionDecision decision = transaction.execute(ignored -> {
            Advertisement ad = getAdvertisement(advertisementId);
            OffsetDateTime now = OffsetDateTime.now();
            if (ad.getStatus() == AdvertisementStatus.REVISION_PENDING
                    && !ad.getStartAt().isAfter(now)) {
                ad.rejectRevisionWithoutRefund(reviewerId, reason, now);
                return new RejectionDecision(ad.getPaymentOrderId() != null, false);
            }
            try {
                ad.reject(reviewerId, reason, now);
            } catch (IllegalStateException exception) {
                throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
            }
            return new RejectionDecision(ad.getPaymentOrderId() != null, true);
        });
        if (decision != null && !decision.refundable()) {
            return response("STOPPED", AdvertisementStatus.STOPPED, false,
                    "수정 광고를 반려하여 노출을 중단했습니다. 이미 노출이 시작된 광고이므로 환불되지 않습니다.");
        }
        if (decision == null || !decision.hasPayment()) {
            return response("REJECTED", AdvertisementStatus.REJECTED, false,
                    "광고를 반려했습니다. 결제되지 않은 광고입니다.");
        }
        return refund(advertisementId, reviewerId, "광고 심사 반려: " + reason);
    }

    private AdvertisementDtos.CancellationResponse refund(
            Long advertisementId, Long requesterMemberId, String reason) {
        RefundContext context = transaction.execute(ignored -> prepare(
                advertisementId, requesterMemberId, reason));
        if (context == null) throw new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT);
        if (context.alreadyCompleted()) {
            finalizeLocal(context);
            return refundedResponse();
        }

        TossCancelResponse tossResponse;
        try {
            tossResponse = cancelWithToss(context, reason);
            validateTossResponse(context, tossResponse);
        } catch (RuntimeException exception) {
            transaction.executeWithoutResult(ignored -> paymentRefundRepository.findById(context.refundId())
                    .filter(refund -> !refund.isCompleted()).ifPresent(refund -> refund.fail(OffsetDateTime.now())));
            throw exception;
        }

        String cancelKey = tossResponse.cancels().stream()
                .filter(cancel -> cancel.cancelAmount() != null
                        && cancel.cancelAmount().compareTo(context.amount()) == 0)
                .map(TossCancelResponse.Cancel::transactionKey)
                .filter(key -> key != null && !key.isBlank())
                .reduce((first, second) -> second)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID));
        RefundContext completedContext = new RefundContext(context.advertisementId(), context.paymentOrderId(),
                context.paymentId(), context.refundId(), context.paymentKey(), context.orderNo(),
                context.amount(), cancelKey, false);
        try {
            finalizeLocal(completedContext);
        } catch (RuntimeException exception) {
            transaction.executeWithoutResult(ignored -> paymentRefundRepository.findById(context.refundId())
                    .filter(refund -> !refund.isCompleted())
                    .ifPresent(refund -> refund.failAfterGatewayCancellation(cancelKey, OffsetDateTime.now())));
            throw exception;
        }
        return refundedResponse();
    }

    private RefundContext prepare(Long advertisementId, Long requesterMemberId, String reason) {
        Advertisement ad = getAdvertisement(advertisementId);
        if (!isRefundable(ad.getStatus())) {
            if (ad.getStatus() == AdvertisementStatus.REFUNDED) {
                return existingCompletedContext(ad);
            }
            throw new BusinessException(GlobalErrorCode.REFUND_NOT_ALLOWED);
        }
        PaymentOrder order = paymentOrderRepository.findById(ad.getPaymentOrderId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT));
        Payment payment = paymentRepository.findByPaymentOrderId(order.getId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT));
        if (!order.isPaid() || !payment.isPaid()) {
            throw new BusinessException(GlobalErrorCode.REFUND_NOT_ALLOWED);
        }
        PaymentRefund refund = paymentRefundRepository.findByPaymentId(payment.getId()).orElse(null);
        if (refund != null && refund.isCompleted()) {
            return context(ad, order, payment, refund, true);
        }
        if (refund != null && !refund.isFailed()) {
            throw new BusinessException(GlobalErrorCode.REFUND_ALREADY_PROCESSING);
        }
        if (refund == null) {
            refund = paymentRefundRepository.saveAndFlush(PaymentRefund.requested(payment.getId(),
                    requesterMemberId, payment.getAmount(), reason, OffsetDateTime.now()));
        } else {
            refund.retry(requesterMemberId, payment.getAmount(), reason, OffsetDateTime.now());
            refund = paymentRefundRepository.saveAndFlush(refund);
        }
        return context(ad, order, payment, refund, false);
    }

    private RefundContext existingCompletedContext(Advertisement ad) {
        PaymentOrder order = paymentOrderRepository.findById(ad.getPaymentOrderId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT));
        Payment payment = paymentRepository.findByPaymentOrderId(order.getId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT));
        PaymentRefund refund = paymentRefundRepository.findByPaymentId(payment.getId())
                .filter(PaymentRefund::isCompleted)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT));
        return context(ad, order, payment, refund, true);
    }

    private void finalizeLocal(RefundContext context) {
        transaction.executeWithoutResult(ignored -> {
            Advertisement ad = getAdvertisement(context.advertisementId());
            PaymentOrder order = paymentOrderRepository.findById(context.paymentOrderId())
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT));
            Payment payment = paymentRepository.findById(context.paymentId())
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT));
            PaymentRefund refund = paymentRefundRepository.findById(context.refundId())
                    .orElseThrow(() -> new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT));
            OffsetDateTime now = OffsetDateTime.now();
            if (payment.isPaid()) payment.markRefunded(now);
            if (order.isPaid()) order.markRefunded(now);
            if (ad.getStatus() != AdvertisementStatus.REFUNDED) ad.markRefunded(now);
            if (!refund.isCompleted()) refund.complete(context.cancelKey(), now);
        });
    }

    private TossCancelResponse cancelWithToss(RefundContext context, String reason) {
        try {
            return tossPaymentClient.cancel(new TossCancelRequest(
                    context.paymentKey(), reason, context.amount().longValueExact()));
        } catch (TossPaymentClientException exception) {
            if (ALREADY_CANCELED_PAYMENT.equals(exception.getTossErrorCode())) {
                return tossPaymentClient.getPaymentForRefund(context.paymentKey());
            }
            throw new BusinessException(exception.getErrorCode());
        }
    }

    private void validateTossResponse(RefundContext context, TossCancelResponse response) {
        BigDecimal cancelled = response == null || response.cancels() == null ? BigDecimal.ZERO
                : response.cancels().stream().map(TossCancelResponse.Cancel::cancelAmount)
                        .filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        if (response == null || !context.paymentKey().equals(response.paymentKey())
                || !context.orderNo().equals(response.orderId())
                || !"CANCELED".equals(response.status())
                || cancelled.compareTo(context.amount()) != 0) {
            throw new BusinessException(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);
        }
    }

    private boolean isRefundable(AdvertisementStatus status) {
        return status == AdvertisementStatus.PAID || status == AdvertisementStatus.REVIEW_PENDING
                || status == AdvertisementStatus.REVISION_PENDING
                || status == AdvertisementStatus.REJECTED || status == AdvertisementStatus.SCHEDULED;
    }

    private Advertisement getAdvertisement(Long id) {
        return advertisementRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
    }

    private RefundContext context(Advertisement ad, PaymentOrder order, Payment payment,
            PaymentRefund refund, boolean completed) {
        return new RefundContext(ad.getId(), order.getId(), payment.getId(), refund.getId(),
                payment.getPaymentKey(), order.getOrderNo(), payment.getAmount(),
                refund.getPgCancelKey(), completed);
    }

    private AdvertisementDtos.CancellationResponse refundedResponse() {
        return response("REFUNDED", AdvertisementStatus.REFUNDED, true,
                "광고를 취소하고 결제 금액 전액을 환불했습니다.");
    }

    private AdvertisementDtos.CancellationResponse response(String action,
            AdvertisementStatus status, boolean refunded, String message) {
        return new AdvertisementDtos.CancellationResponse(action, status, refunded, message);
    }

    private record RejectionDecision(boolean hasPayment, boolean refundable) {}

    private record RefundContext(Long advertisementId, Long paymentOrderId, Long paymentId,
            Long refundId, String paymentKey, String orderNo, BigDecimal amount,
            String cancelKey, boolean alreadyCompleted) {}
}
