package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.min.edu.payment.domain.Payment;
import com.min.edu.payment.domain.PaymentAuditActorType;
import com.min.edu.payment.domain.PaymentAuditEventType;
import com.min.edu.payment.domain.PaymentAuditSource;
import com.min.edu.payment.domain.PaymentProvider;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.domain.PaymentStatus;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.dto.request.CreateRefundRequest;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;

class RefundAttemptRecorderTest {

    private PaymentRefundRepository paymentRefundRepository;
    private PaymentRepository paymentRepository;
    private PaymentAuditLogWriter auditLogWriter;
    private RefundAttemptRecorder recorder;

    @BeforeEach
    void setUp() {
        paymentRefundRepository = org.mockito.Mockito.mock(PaymentRefundRepository.class);
        paymentRepository = org.mockito.Mockito.mock(PaymentRepository.class);
        auditLogWriter = org.mockito.Mockito.mock(PaymentAuditLogWriter.class);
        recorder = new RefundAttemptRecorder(
            paymentRefundRepository,
            paymentRepository,
            auditLogWriter
        );
    }

    @Test
    void prepare_savesRequestedAtFromRefundAttemptedAt() {
        OffsetDateTime refundAttemptedAt = OffsetDateTime.parse("2026-08-12T16:59:59.999+09:00");
        RefundPaymentProjection payment = projection();
        given(paymentRefundRepository.findByPaymentId(1L)).willReturn(Optional.empty());
        given(paymentRefundRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(PaymentRefund.class)))
            .willReturn(refund(7L, refundAttemptedAt));

        PaymentRefund refund = recorder.prepare(
            payment,
            10L,
            new CreateRefundRequest("reason"),
            refundAttemptedAt
        );

        assertThat(refund.getRequestedAt()).isEqualTo(refundAttemptedAt);
        assertThat(refund.getId()).isEqualTo(7L);
        verify(paymentRefundRepository).saveAndFlush(any(PaymentRefund.class));
        verify(auditLogWriter).append(
            eq(11L),
            eq(1L),
            eq(7L),
            eq(PaymentAuditEventType.REFUND_REQUESTED),
            eq(null),
            eq("REQUESTED"),
            eq(PaymentAuditSource.REFUND),
            eq(null),
            eq(PaymentAuditActorType.MEMBER),
            eq(10L),
            eq(null),
            eq(refundAttemptedAt)
        );
    }

    @Test
    void prepare_recordsGuestActorWhenRequesterMemberIdIsNull() {
        OffsetDateTime refundAttemptedAt = OffsetDateTime.parse("2026-08-12T16:59:59.999+09:00");
        RefundPaymentProjection payment = projection();
        given(paymentRefundRepository.findByPaymentId(1L)).willReturn(Optional.empty());
        given(paymentRefundRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(PaymentRefund.class)))
            .willReturn(refund(9L, null, refundAttemptedAt));

        PaymentRefund refund = recorder.prepare(
            payment,
            null,
            new CreateRefundRequest("reason"),
            refundAttemptedAt
        );

        assertThat(refund.getId()).isEqualTo(9L);
        verify(auditLogWriter).append(
            eq(11L),
            eq(1L),
            eq(9L),
            eq(PaymentAuditEventType.REFUND_REQUESTED),
            eq(null),
            eq("REQUESTED"),
            eq(PaymentAuditSource.REFUND),
            eq(null),
            eq(PaymentAuditActorType.GUEST),
            eq(null),
            eq(null),
            eq(refundAttemptedAt)
        );
    }

    @Test
    void prepare_updatesFailedRefundRequestedAtFromNewRefundAttemptedAt() {
        OffsetDateTime oldRequestedAt = OffsetDateTime.parse("2026-08-12T15:00:00+09:00");
        OffsetDateTime retryAttemptedAt = OffsetDateTime.parse("2026-08-12T16:59:59.999+09:00");
        PaymentRefund failedRefund = refund(8L, oldRequestedAt);
        failedRefund.fail(oldRequestedAt.plusMinutes(1));
        given(paymentRefundRepository.findByPaymentId(1L)).willReturn(Optional.of(failedRefund));

        PaymentRefund refund = recorder.prepare(
            projection(),
            10L,
            new CreateRefundRequest("retry reason"),
            retryAttemptedAt
        );

        assertThat(refund.getRequestedAt()).isEqualTo(retryAttemptedAt);
        assertThat(refund.getReason()).isEqualTo("retry reason");
        assertThat(refund.isFailed()).isFalse();
        verify(auditLogWriter).append(
            eq(11L),
            eq(1L),
            eq(8L),
            eq(PaymentAuditEventType.REFUND_REQUESTED),
            eq("FAILED"),
            eq("REQUESTED"),
            eq(PaymentAuditSource.REFUND),
            eq("RETRY_AFTER_FAILED"),
            eq(PaymentAuditActorType.MEMBER),
            eq(10L),
            eq(null),
            eq(retryAttemptedAt)
        );
    }

    @Test
    void markFailed_recordsRefundFailedAudit() {
        OffsetDateTime requestedAt = OffsetDateTime.parse("2026-08-12T16:59:59.999+09:00");
        PaymentRefund refund = PaymentRefund.builder()
            .id(7L)
            .paymentId(1L)
            .requesterMemberId(10L)
            .refundAmount(BigDecimal.valueOf(10000))
            .reason("reason")
            .status(com.min.edu.payment.domain.PaymentRefundStatus.REQUESTED)
            .requestedAt(requestedAt)
            .build();
        given(paymentRefundRepository.findById(7L)).willReturn(Optional.of(refund));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment()));

        recorder.markFailed(7L);

        assertThat(refund.isFailed()).isTrue();
        verify(auditLogWriter).append(
            eq(11L),
            eq(1L),
            eq(7L),
            eq(PaymentAuditEventType.REFUND_FAILED),
            eq("REQUESTED"),
            eq("FAILED"),
            eq(PaymentAuditSource.REFUND),
            eq(null),
            eq(PaymentAuditActorType.MEMBER),
            eq(10L),
            eq(null),
            any()
        );
    }

    @Test
    void markAmbiguous_recordsSystemActor() {
        OffsetDateTime requestedAt = OffsetDateTime.parse("2026-08-12T16:59:59.999+09:00");
        PaymentRefund refund = refund(7L, 10L, requestedAt);
        given(paymentRefundRepository.findById(7L)).willReturn(Optional.of(refund));
        given(paymentRepository.findById(1L)).willReturn(Optional.of(payment()));

        recorder.markAmbiguous(7L, "PROVIDER_TIMEOUT");

        verify(auditLogWriter).append(
            eq(11L),
            eq(1L),
            eq(7L),
            eq(PaymentAuditEventType.REFUND_AMBIGUOUS),
            eq("REQUESTED"),
            eq("REQUESTED"),
            eq(PaymentAuditSource.REFUND),
            eq("PROVIDER_TIMEOUT"),
            eq(PaymentAuditActorType.SYSTEM),
            eq(null),
            eq(null),
            any()
        );
    }

    private RefundPaymentProjection projection() {
        return new RefundPaymentProjection() {
            @Override public Long getPaymentId() { return 1L; }
            @Override public Long getPaymentOrderId() { return 11L; }
            @Override public String getPaymentKey() { return "payment-key"; }
            @Override public BigDecimal getPaymentAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getPaymentStatus() { return "PAID"; }
            @Override public String getPaymentMethod() { return "CARD"; }
            @Override public String getOrderNo() { return "ORDER-1"; }
            @Override public Long getBuyerMemberId() { return 10L; }
            @Override public BigDecimal getTotalAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getPaymentOrderStatus() { return "PAID"; }
            @Override public Long getTicketOrderId() { return 2L; }
            @Override public Long getEventId() { return 3L; }
            @Override public String getEventName() { return "event"; }
            @Override public OffsetDateTime getEventStartAt() { return OffsetDateTime.parse("2026-08-12T10:00:00+09:00"); }
            @Override public OffsetDateTime getEventEndAt() { return OffsetDateTime.parse("2026-08-12T18:00:00+09:00"); }
            @Override public Integer getQuantity() { return 2; }
            @Override public String getTicketOrderStatus() { return "CONFIRMED"; }
        };
    }

    private PaymentRefund refund(Long id, OffsetDateTime requestedAt) {
        return refund(id, 10L, requestedAt);
    }

    private PaymentRefund refund(Long id, Long requesterMemberId, OffsetDateTime requestedAt) {
        return PaymentRefund.builder()
            .id(id)
            .paymentId(1L)
            .requesterMemberId(requesterMemberId)
            .refundAmount(BigDecimal.valueOf(10000))
            .reason("old reason")
            .status(com.min.edu.payment.domain.PaymentRefundStatus.REQUESTED)
            .requestedAt(requestedAt)
            .build();
    }

    private Payment payment() {
        return Payment.builder()
            .id(1L)
            .paymentOrderId(11L)
            .pgProvider(PaymentProvider.TOSS_PAYMENTS)
            .paymentKey("payment-key")
            .method("CARD")
            .amount(BigDecimal.valueOf(10000))
            .status(PaymentStatus.PAID.name())
            .requestedAt(OffsetDateTime.now())
            .approvedAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
