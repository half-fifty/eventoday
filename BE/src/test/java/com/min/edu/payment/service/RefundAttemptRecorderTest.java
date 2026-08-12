package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.dto.request.CreateRefundRequest;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;

class RefundAttemptRecorderTest {

    private PaymentRefundRepository paymentRefundRepository;
    private RefundAttemptRecorder recorder;

    @BeforeEach
    void setUp() {
        paymentRefundRepository = org.mockito.Mockito.mock(PaymentRefundRepository.class);
        recorder = new RefundAttemptRecorder(paymentRefundRepository);
    }

    @Test
    void prepare_savesRequestedAtFromRefundAttemptedAt() {
        OffsetDateTime refundAttemptedAt = OffsetDateTime.parse("2026-08-12T16:59:59.999+09:00");
        RefundPaymentProjection payment = projection();
        given(paymentRefundRepository.findByPaymentId(1L)).willReturn(Optional.empty());
        given(paymentRefundRepository.saveAndFlush(org.mockito.ArgumentMatchers.any(PaymentRefund.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        PaymentRefund refund = recorder.prepare(
            payment,
            10L,
            new CreateRefundRequest("reason"),
            refundAttemptedAt
        );

        assertThat(refund.getRequestedAt()).isEqualTo(refundAttemptedAt);
        verify(paymentRefundRepository).saveAndFlush(refund);
    }

    @Test
    void prepare_updatesFailedRefundRequestedAtFromNewRefundAttemptedAt() {
        OffsetDateTime oldRequestedAt = OffsetDateTime.parse("2026-08-12T15:00:00+09:00");
        OffsetDateTime retryAttemptedAt = OffsetDateTime.parse("2026-08-12T16:59:59.999+09:00");
        PaymentRefund failedRefund = PaymentRefund.requested(
            1L,
            10L,
            BigDecimal.valueOf(10000),
            "old reason",
            oldRequestedAt
        );
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
    }

    private RefundPaymentProjection projection() {
        return new RefundPaymentProjection() {
            @Override public Long getPaymentId() { return 1L; }
            @Override public Long getPaymentOrderId() { return 11L; }
            @Override public String getPaymentKey() { return "payment-key"; }
            @Override public BigDecimal getPaymentAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getPaymentStatus() { return "PAID"; }
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
}
