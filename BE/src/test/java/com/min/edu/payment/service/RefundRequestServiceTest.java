package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.times;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.dao.DataIntegrityViolationException;

import com.min.edu.admission.domain.ExchangeCodeStatus;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.policy.EventOperationDeadlinePolicy;
import com.min.edu.payment.domain.PaymentRefund;
import com.min.edu.payment.dto.request.CreateRefundRequest;
import com.min.edu.payment.dto.response.CreateRefundResponse;
import com.min.edu.payment.repository.PaymentRefundRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.RefundPaymentProjection;
import com.min.edu.payment.support.OrderAccessTokenProvider;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossCancelRequest;
import com.min.edu.payment.toss.dto.TossCancelResponse;

@ExtendWith(OutputCaptureExtension.class)
class RefundRequestServiceTest {

    private PaymentRepository paymentRepository;
    private PaymentRefundRepository paymentRefundRepository;
    private ExchangeCodeRepository exchangeCodeRepository;
    private OrderAccessTokenProvider orderAccessTokenProvider;
    private TossPaymentClient tossPaymentClient;
    private RefundAttemptRecorder refundAttemptRecorder;
    private RefundFinalizer refundFinalizer;
    private RefundFinalizationExceptionTranslator exceptionTranslator;
    private RefundRequestService service;

    @BeforeEach
    void setUp() {
        paymentRepository = org.mockito.Mockito.mock(PaymentRepository.class);
        paymentRefundRepository = org.mockito.Mockito.mock(PaymentRefundRepository.class);
        exchangeCodeRepository = org.mockito.Mockito.mock(ExchangeCodeRepository.class);
        orderAccessTokenProvider = org.mockito.Mockito.mock(OrderAccessTokenProvider.class);
        tossPaymentClient = org.mockito.Mockito.mock(TossPaymentClient.class);
        refundAttemptRecorder = org.mockito.Mockito.mock(RefundAttemptRecorder.class);
        refundFinalizer = org.mockito.Mockito.mock(RefundFinalizer.class);
        exceptionTranslator = new RefundFinalizationExceptionTranslator();
        given(paymentRefundRepository.findByPaymentId(any())).willReturn(Optional.empty());
        service = new RefundRequestService(
            paymentRepository,
            paymentRefundRepository,
            exchangeCodeRepository,
            orderAccessTokenProvider,
            tossPaymentClient,
            refundAttemptRecorder,
            refundFinalizer,
            exceptionTranslator,
            new EventOperationDeadlinePolicy()
        );
    }

    @Test
    void refund_succeedsForMemberOwner() {
        RefundPaymentProjection payment = projection(10L);
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(paymentRefundRepository.findByPaymentId(1L)).willReturn(Optional.empty());
        given(exchangeCodeRepository.existsByTicketOrderIdAndStatus(
            2L,
            ExchangeCodeStatus.REDEEMED
        )).willReturn(false);
        PaymentRefund preparedRefund = requestedRefund();
        given(refundAttemptRecorder.prepare(any(), any(), any(), any()))
            .willReturn(preparedRefund);
        given(tossPaymentClient.cancel(any())).willReturn(tossResponse());
        given(refundFinalizer.finalizeRefund(any(), any(), any(), any(), any()))
            .willReturn(refundResponse());
        CreateRefundRequest request = new CreateRefundRequest("reason");

        CreateRefundResponse response = service.refund(
            10L,
            null,
            1L,
            request
        );

        assertThat(response.getRefundStatus()).isEqualTo("COMPLETED");
        InOrder inOrder = inOrder(refundAttemptRecorder, tossPaymentClient, refundFinalizer);
        inOrder.verify(refundAttemptRecorder).prepare(eq(payment), eq(10L), eq(request), any());
        inOrder.verify(tossPaymentClient).cancel(any());
        inOrder.verify(refundFinalizer)
            .finalizeRefund(eq(preparedRefund.getId()), eq(1L), eq(10L), eq(request), any());
    }

    @Test
    void refund_succeedsForGuestWithToken() {
        RefundPaymentProjection payment = projection(null);
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(orderAccessTokenProvider.getOrderNo("token")).willReturn("ORDER-1");
        given(refundAttemptRecorder.prepare(any(), any(), any(), any()))
            .willReturn(requestedRefund());
        given(tossPaymentClient.cancel(any())).willReturn(tossResponse());
        given(refundFinalizer.finalizeRefund(any(), any(), any(), any(), any()))
            .willReturn(refundResponse());

        CreateRefundResponse response = service.refund(
            null,
            "token",
            1L,
            new CreateRefundRequest("reason")
        );

        assertThat(response.getPaymentId()).isEqualTo(1L);
    }

    @Test
    void refund_sendsVirtualAccountRefundBankCodeToToss() {
        RefundPaymentProjection payment = projection(10L, "VIRTUAL_ACCOUNT");
        PaymentRefund preparedRefund = requestedRefund();
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any()))
            .willReturn(preparedRefund);
        given(tossPaymentClient.cancel(any())).willReturn(tossResponse());
        given(refundFinalizer.finalizeRefund(any(), any(), any(), any(), any()))
            .willReturn(refundResponse());
        CreateRefundRequest request = refundRequestWithAccount("06");

        service.refund(10L, null, 1L, request);

        ArgumentCaptor<TossCancelRequest> captor =
            ArgumentCaptor.forClass(TossCancelRequest.class);
        verify(tossPaymentClient).cancel(captor.capture());
        assertThat(captor.getValue().refundReceiveAccount()).isNotNull();
        assertThat(captor.getValue().refundReceiveAccount().bank()).isEqualTo("06");
    }

    @Test
    void refund_allowsRepresentativeOfficialBankCodes() {
        assertThat(com.min.edu.payment.toss.TossBankCodes.isSupportedBankCode("11"))
            .isTrue();
        assertThat(com.min.edu.payment.toss.TossBankCodes.isSupportedBankCode("88"))
            .isTrue();
        assertThat(com.min.edu.payment.toss.TossBankCodes.isSupportedBankCode("90"))
            .isTrue();
        assertThat(com.min.edu.payment.toss.TossBankCodes.isSupportedBankCode("54"))
            .isTrue();
    }

    @Test
    void refund_failsWhenGuestTokenMissing() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(null)));

        assertThatThrownBy(() -> service.refund(
            null,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED);

        verify(tossPaymentClient, never()).cancel(any());
        verify(refundAttemptRecorder, never()).prepare(any(), any(), any(), any());
        verify(refundFinalizer, never()).finalizeRefund(any(), any(), any(), any(), any());
    }

    @Test
    void refund_failsWhenMemberIsNotOwnerBeforeRecordingRequested() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(10L)));

        assertThatThrownBy(() -> service.refund(
            20L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_ACCESS_DENIED);

        verify(refundAttemptRecorder, never()).prepare(any(), any(), any(), any());
        verify(tossPaymentClient, never()).cancel(any());
        verify(refundFinalizer, never()).finalizeRefund(any(), any(), any(), any(), any());
    }

    @Test
    void refund_failsWhenGuestTokenOrderNoDoesNotMatchBeforeRecordingRequested() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(null)));
        given(orderAccessTokenProvider.getOrderNo("token")).willReturn("OTHER-ORDER");

        assertThatThrownBy(() -> service.refund(
            null,
            "token",
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_ACCESS_DENIED);

        verify(refundAttemptRecorder, never()).prepare(any(), any(), any(), any());
        verify(tossPaymentClient, never()).cancel(any());
        verify(refundFinalizer, never()).finalizeRefund(any(), any(), any(), any(), any());
    }

    @Test
    void refund_succeedsAfterEventStartBeforeOperationCutoff() {
        OffsetDateTime now = OffsetDateTime.now();
        RefundPaymentProjection payment = projection(
            10L,
            "PAID",
            "PAID",
            "CONFIRMED",
            now.minusHours(1),
            now.plusHours(3)
        );
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        PaymentRefund preparedRefund = requestedRefund();
        given(refundAttemptRecorder.prepare(any(), any(), any(), any()))
            .willReturn(preparedRefund);
        given(tossPaymentClient.cancel(any())).willReturn(tossResponse());
        given(refundFinalizer.finalizeRefund(any(), any(), any(), any(), any()))
            .willReturn(refundResponse());

        CreateRefundResponse response = service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        );

        assertThat(response.getRefundStatus()).isEqualTo("COMPLETED");
        ArgumentCaptor<OffsetDateTime> attemptedAtCaptor =
            ArgumentCaptor.forClass(OffsetDateTime.class);
        verify(refundAttemptRecorder)
            .prepare(eq(payment), eq(10L), any(), attemptedAtCaptor.capture());
        assertThat(attemptedAtCaptor.getValue()).isBefore(payment.getEventEndAt().minusHours(1));
    }

    @Test
    void refund_failsAtOperationCutoff() {
        OffsetDateTime now = OffsetDateTime.now();
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(
                10L,
                "PAID",
                "PAID",
                "CONFIRMED",
                now.minusHours(2),
                now.plusHours(1)
            )));

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_NOT_ALLOWED);

        verify(tossPaymentClient, never()).cancel(any());
        verify(refundAttemptRecorder, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void refund_failsAfterOperationCutoff() {
        OffsetDateTime now = OffsetDateTime.now();
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(
                10L,
                "PAID",
                "PAID",
                "CONFIRMED",
                now.minusHours(2),
                now.plusMinutes(30)
            )));

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_NOT_ALLOWED);

        verify(tossPaymentClient, never()).cancel(any());
        verify(refundAttemptRecorder, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void refund_failsWhenExchangeCodeRedeemed() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(10L)));
        given(exchangeCodeRepository.existsByTicketOrderIdAndStatus(
            2L,
            ExchangeCodeStatus.REDEEMED
        )).willReturn(true);

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.USED_TICKET_CANNOT_BE_REFUNDED);

        verify(tossPaymentClient, never()).cancel(any());
        verify(refundAttemptRecorder, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void refund_rejectsBankNameBeforeCallingToss() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(10L, "VIRTUAL_ACCOUNT")));

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            refundRequestWithAccount("KB 국민")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_RECEIVE_ACCOUNT_INVALID);

        verify(tossPaymentClient, never()).cancel(any());
        verify(refundAttemptRecorder, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void refund_rejectsUnknownBankCodeBeforeCallingToss() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(10L, "VIRTUAL_ACCOUNT")));

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            refundRequestWithAccount("999")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_RECEIVE_ACCOUNT_INVALID);

        verify(tossPaymentClient, never()).cancel(any());
        verify(refundAttemptRecorder, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void refund_requiresReceiveAccountForVirtualAccountPayment() {
        given(paymentRepository.findRefundPaymentById(1L))
            .willReturn(Optional.of(projection(10L, "VIRTUAL_ACCOUNT")));

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_RECEIVE_ACCOUNT_REQUIRED);

        verify(tossPaymentClient, never()).cancel(any());
        verify(refundAttemptRecorder, never()).prepare(any(), any(), any(), any());
    }

    @Test
    void refund_allowsCardRefundWithoutReceiveAccount() {
        RefundPaymentProjection payment = projection(10L, "CARD");
        PaymentRefund preparedRefund = requestedRefund();
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any()))
            .willReturn(preparedRefund);
        given(tossPaymentClient.cancel(any())).willReturn(tossResponse());
        given(refundFinalizer.finalizeRefund(any(), any(), any(), any(), any()))
            .willReturn(refundResponse());

        service.refund(10L, null, 1L, new CreateRefundRequest("reason"));

        ArgumentCaptor<TossCancelRequest> captor =
            ArgumentCaptor.forClass(TossCancelRequest.class);
        verify(tossPaymentClient).cancel(captor.capture());
        assertThat(captor.getValue().refundReceiveAccount()).isNull();
    }

    @Test
    void refund_returnsExistingCompletedRefundWithoutToss() {
        RefundPaymentProjection payment = projection(
            10L,
            "REFUNDED",
            "REFUNDED",
            "REFUNDED",
            OffsetDateTime.now().plusDays(1),
            OffsetDateTime.now().plusDays(2)
        );
        PaymentRefund refund = PaymentRefund.requested(
            1L,
            10L,
            BigDecimal.valueOf(10000),
            "reason",
            OffsetDateTime.now()
        );
        refund.complete("cancel-key", OffsetDateTime.now());
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(paymentRefundRepository.findByPaymentId(1L)).willReturn(Optional.of(refund));

        CreateRefundResponse response = service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        );

        assertThat(response.getRefundStatus()).isEqualTo("COMPLETED");
        verify(refundAttemptRecorder, never()).prepare(any(), any(), any(), any());
        verify(tossPaymentClient, never()).cancel(any());
        verify(refundFinalizer, never()).finalizeRefund(any(), any(), any(), any(), any());
    }

    @Test
    void refund_marksFailedWhenTossCancelFails() {
        RefundPaymentProjection payment = projection(10L);
        PaymentRefund preparedRefund = requestedRefund();
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any())).willReturn(preparedRefund);
        given(tossPaymentClient.cancel(any()))
            .willThrow(new TossPaymentClientException(GlobalErrorCode.REFUND_REJECTED));

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_REJECTED);

        verify(refundAttemptRecorder).markFailed(preparedRefund.getId());
        verify(refundFinalizer, never()).finalizeRefund(any(), any(), any(), any(), any());
    }

    @Test
    void refund_logsOnlySafeTossCancelRejectionDiagnostics(CapturedOutput output) {
        RefundPaymentProjection payment = projection(10L, "VIRTUAL_ACCOUNT");
        PaymentRefund preparedRefund = requestedRefund();
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any())).willReturn(preparedRefund);
        given(tossPaymentClient.cancel(any())).willThrow(new TossPaymentClientException(
            GlobalErrorCode.REFUND_REJECTED,
            "INVALID_REFUND_ACCOUNT_NUMBER"
        ));

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest(
                "reason",
                new CreateRefundRequest.RefundReceiveAccountRequest(
                    "06",
                    "1234567890",
                    "holder"
                )
            )
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_REJECTED);

        assertThat(output)
            .contains("stage=TOSS_CANCEL")
            .contains("refundId=1")
            .contains("paymentId=1")
            .contains("tossErrorCode=INVALID_REFUND_ACCOUNT_NUMBER")
            .contains("exceptionType=TossPaymentClientException")
            .doesNotContain("1234567890")
            .doesNotContain("holder")
            .doesNotContain("payment-key");
    }

    @Test
    void refund_keepsRequestedWhenTossCancelTimeoutIsUncertain() {
        RefundPaymentProjection payment = projection(10L);
        PaymentRefund preparedRefund = requestedRefund();
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any())).willReturn(preparedRefund);
        given(tossPaymentClient.cancel(any()))
            .willThrow(new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT));

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT);

        verify(refundAttemptRecorder, never()).markFailed(preparedRefund.getId());
        verify(refundFinalizer, never()).finalizeRefund(any(), any(), any(), any(), any());
    }

    @Test
    void refund_keepsRequestedWhenTossGatewayErrorIsUncertain() {
        RefundPaymentProjection payment = projection(10L);
        PaymentRefund preparedRefund = requestedRefund();
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any())).willReturn(preparedRefund);
        given(tossPaymentClient.cancel(any()))
            .willThrow(new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_ERROR));

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.PAYMENT_GATEWAY_ERROR);

        verify(refundAttemptRecorder, never()).markFailed(preparedRefund.getId());
        verify(refundFinalizer, never()).finalizeRefund(any(), any(), any(), any(), any());
    }

    @Test
    void refund_keepsRequestedWhenFinalizerFailsAfterTossSuccess() {
        RefundPaymentProjection payment = projection(10L);
        PaymentRefund preparedRefund = requestedRefund();
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any())).willReturn(preparedRefund);
        given(tossPaymentClient.cancel(any())).willReturn(tossResponse());
        given(refundFinalizer.finalizeRefund(any(), any(), any(), any(), any()))
            .willThrow(new BusinessException(GlobalErrorCode.REFUND_DATA_INCONSISTENT));

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_DATA_INCONSISTENT);

        verify(tossPaymentClient).cancel(any());
        verify(refundAttemptRecorder, never()).markFailed(preparedRefund.getId());
    }

    @Test
    void refund_failsAsAlreadyProcessingWhenPrepareUniqueCollisionFindsRequestedRefund() {
        RefundPaymentProjection payment = projection(10L);
        PaymentRefund existingRefund = requestedRefund();
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(paymentRefundRepository.findByPaymentId(1L))
            .willReturn(Optional.empty(), Optional.of(existingRefund));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any()))
            .willThrow(paymentRefundUniqueViolation());

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.REFUND_ALREADY_PROCESSING);

        verify(tossPaymentClient, never()).cancel(any());
        verify(refundFinalizer, never()).finalizeRefund(any(), any(), any(), any(), any());
    }

    @Test
    void refund_returnsCompletedRefundWhenPrepareUniqueCollisionFindsCompletedRefund() {
        RefundPaymentProjection payment = projection(10L);
        PaymentRefund existingRefund = completedRefund();
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(paymentRefundRepository.findByPaymentId(1L))
            .willReturn(Optional.empty(), Optional.of(existingRefund));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any()))
            .willThrow(paymentRefundUniqueViolation());

        CreateRefundResponse response = service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        );

        assertThat(response.getRefundStatus()).isEqualTo("COMPLETED");
        verify(tossPaymentClient, never()).cancel(any());
        verify(refundFinalizer, never()).finalizeRefund(any(), any(), any(), any(), any());
    }

    @Test
    void refund_reusesFailedRefundWhenPrepareUniqueCollisionFindsFailedRefund() {
        RefundPaymentProjection payment = projection(10L);
        PaymentRefund failedRefund = failedRefund();
        PaymentRefund retriedRefund = requestedRefund();
        CreateRefundRequest request = new CreateRefundRequest("reason");
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(paymentRefundRepository.findByPaymentId(1L))
            .willReturn(Optional.empty(), Optional.of(failedRefund));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any()))
            .willThrow(paymentRefundUniqueViolation())
            .willReturn(retriedRefund);
        given(tossPaymentClient.cancel(any())).willReturn(tossResponse());
        given(refundFinalizer.finalizeRefund(any(), any(), any(), any(), any()))
            .willReturn(refundResponse());

        CreateRefundResponse response = service.refund(10L, null, 1L, request);

        assertThat(response.getRefundStatus()).isEqualTo("COMPLETED");
        verify(refundAttemptRecorder, times(2)).prepare(eq(payment), eq(10L), eq(request), any());
        verify(tossPaymentClient).cancel(any());
        verify(refundFinalizer)
            .finalizeRefund(eq(retriedRefund.getId()), eq(1L), eq(10L), eq(request), any());
    }

    @Test
    void refund_recoversAlreadyCanceledPaymentAfterTossLookupMatches() {
        RefundPaymentProjection payment = projection(10L);
        PaymentRefund preparedRefund = requestedRefund();
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any())).willReturn(preparedRefund);
        given(tossPaymentClient.cancel(any())).willThrow(new TossPaymentClientException(
            GlobalErrorCode.REFUND_REJECTED,
            "ALREADY_CANCELED_PAYMENT"
        ));
        given(tossPaymentClient.getPaymentForRefund("payment-key")).willReturn(tossResponse());
        given(refundFinalizer.finalizeRefund(any(), any(), any(), any(), any()))
            .willReturn(refundResponse());

        CreateRefundResponse response = service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        );

        assertThat(response.getRefundStatus()).isEqualTo("COMPLETED");
        verify(tossPaymentClient).getPaymentForRefund("payment-key");
        verify(refundAttemptRecorder, never()).markFailed(preparedRefund.getId());
    }

    @Test
    void refund_marksFailedWhenAlreadyCanceledLookupDoesNotMatch() {
        RefundPaymentProjection payment = projection(10L);
        PaymentRefund preparedRefund = requestedRefund();
        TossCancelResponse mismatched = new TossCancelResponse(
            "payment-key",
            "OTHER-ORDER",
            BigDecimal.valueOf(10000),
            "CANCELED",
            "CARD",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            java.util.List.of(new TossCancelResponse.Cancel(
                "cancel-key",
                BigDecimal.valueOf(10000),
                "reason",
                OffsetDateTime.now()
            ))
        );
        given(paymentRepository.findRefundPaymentById(1L)).willReturn(Optional.of(payment));
        given(refundAttemptRecorder.prepare(any(), any(), any(), any())).willReturn(preparedRefund);
        given(tossPaymentClient.cancel(any())).willThrow(new TossPaymentClientException(
            GlobalErrorCode.REFUND_REJECTED,
            "ALREADY_CANCELED_PAYMENT"
        ));
        given(tossPaymentClient.getPaymentForRefund("payment-key")).willReturn(mismatched);

        assertThatThrownBy(() -> service.refund(
            10L,
            null,
            1L,
            new CreateRefundRequest("reason")
        ))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID);

        verify(refundAttemptRecorder).markFailed(preparedRefund.getId());
        verify(refundFinalizer, never()).finalizeRefund(any(), any(), any(), any(), any());
    }

    private CreateRefundResponse refundResponse() {
        return new CreateRefundResponse(
            1L,
            1L,
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "COMPLETED",
            "reason",
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );
    }

    private TossCancelResponse tossResponse() {
        return new TossCancelResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "CANCELED",
            "CARD",
            OffsetDateTime.now(),
            OffsetDateTime.now(),
            java.util.List.of(new TossCancelResponse.Cancel(
                "cancel-key",
                BigDecimal.valueOf(10000),
                "reason",
                OffsetDateTime.now()
            ))
        );
    }

    private PaymentRefund requestedRefund() {
        return PaymentRefund.builder()
            .id(1L)
            .paymentId(1L)
            .requesterMemberId(10L)
            .refundAmount(BigDecimal.valueOf(10000))
            .reason("reason")
            .status(com.min.edu.payment.domain.PaymentRefundStatus.REQUESTED)
            .requestedAt(OffsetDateTime.now())
            .build();
    }

    private PaymentRefund completedRefund() {
        PaymentRefund refund = requestedRefund();
        refund.complete("cancel-key", OffsetDateTime.now());
        return refund;
    }

    private PaymentRefund failedRefund() {
        PaymentRefund refund = requestedRefund();
        refund.fail(OffsetDateTime.now());
        return refund;
    }

    private CreateRefundRequest refundRequestWithAccount(String bankCode) {
        return new CreateRefundRequest(
            "reason",
            new CreateRefundRequest.RefundReceiveAccountRequest(
                bankCode,
                "1234567890",
                "holder"
            )
        );
    }

    private DataIntegrityViolationException paymentRefundUniqueViolation() {
        ConstraintViolationException cause = new ConstraintViolationException(
            "duplicate",
            new SQLException("duplicate", "23505"),
            "uk_payment_refunds_payment"
        );
        return new DataIntegrityViolationException("duplicate", cause);
    }

    private RefundPaymentProjection projection(Long buyerMemberId) {
        return projection(buyerMemberId, "CARD");
    }

    private RefundPaymentProjection projection(Long buyerMemberId, String paymentMethod) {
        return projection(
            buyerMemberId,
            paymentMethod,
            "PAID",
            "PAID",
            "CONFIRMED",
            OffsetDateTime.now().plusDays(1),
            OffsetDateTime.now().plusDays(2)
        );
    }

    private RefundPaymentProjection projection(
            Long buyerMemberId,
            String paymentStatus,
            String paymentOrderStatus,
            String ticketOrderStatus,
            OffsetDateTime eventStartAt,
            OffsetDateTime eventEndAt) {
        return projection(
            buyerMemberId,
            "CARD",
            paymentStatus,
            paymentOrderStatus,
            ticketOrderStatus,
            eventStartAt,
            eventEndAt
        );
    }

    private RefundPaymentProjection projection(
            Long buyerMemberId,
            String paymentMethod,
            String paymentStatus,
            String paymentOrderStatus,
            String ticketOrderStatus,
            OffsetDateTime eventStartAt,
            OffsetDateTime eventEndAt) {
        return new RefundPaymentProjection() {
            @Override public Long getPaymentId() { return 1L; }
            @Override public Long getPaymentOrderId() { return 11L; }
            @Override public String getPaymentKey() { return "payment-key"; }
            @Override public BigDecimal getPaymentAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getPaymentStatus() { return paymentStatus; }
            @Override public String getPaymentMethod() { return paymentMethod; }
            @Override public String getOrderNo() { return "ORDER-1"; }
            @Override public Long getBuyerMemberId() { return buyerMemberId; }
            @Override public BigDecimal getTotalAmount() { return BigDecimal.valueOf(10000); }
            @Override public String getPaymentOrderStatus() { return paymentOrderStatus; }
            @Override public Long getTicketOrderId() { return 2L; }
            @Override public Long getEventId() { return 3L; }
            @Override public String getEventName() { return "event name"; }
            @Override public OffsetDateTime getEventStartAt() { return eventStartAt; }
            @Override public OffsetDateTime getEventEndAt() { return eventEndAt; }
            @Override public Integer getQuantity() { return 2; }
            @Override public String getTicketOrderStatus() { return ticketOrderStatus; }
        };
    }
}
