package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.ConfirmPaymentRequest;
import com.min.edu.payment.dto.response.ConfirmPaymentResponse;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.PaymentRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.support.OrderAccessTokenProvider;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossConfirmRequest;
import com.min.edu.payment.toss.dto.TossConfirmResponse;

@ExtendWith(MockitoExtension.class)
class PaymentConfirmServiceTest {

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private OrderAccessTokenProvider orderAccessTokenProvider;

    @Mock
    private TossPaymentClient tossPaymentClient;

    @Mock
    private PaymentFinalizer paymentFinalizer;

    @Mock
    private VirtualAccountPaymentService virtualAccountPaymentService;

    @Mock
    private PaymentConfirmInflightDuplicateGate inflightDuplicateGate;

    @Spy
    private PaymentFinalizationExceptionTranslator exceptionTranslator =
        new PaymentFinalizationExceptionTranslator();

    @InjectMocks
    private PaymentConfirmService paymentConfirmService;

    @BeforeEach
    void setUp() {
        lenient().when(inflightDuplicateGate.tryClaim("ORDER-1"))
            .thenReturn(PaymentConfirmInflightClaim.acquired("token-1"));
    }

    @Test
    void confirm_succeedsForMemberPaidTicketOrder() {
        ConfirmPaymentRequest request = request();
        PaymentOrder paymentOrder = pendingMemberOrder(10L);
        TicketOrder ticketOrder = pendingTicketOrder();
        TossConfirmResponse tossResponse = tossResponse();
        ConfirmPaymentResponse response = response();

        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(tossPaymentClient.confirm(any())).willReturn(tossResponse);
        given(paymentFinalizer.finalizePayment(10L, request, tossResponse))
            .willReturn(response);

        paymentConfirmService.confirm(10L, null, request);

        verify(tossPaymentClient).confirm(any());
        verify(paymentFinalizer).finalizePayment(10L, request, tossResponse);
    }

    @Test
    void confirm_succeedsForGuestWithOrderAccessToken() {
        ConfirmPaymentRequest request = request();
        PaymentOrder paymentOrder = pendingGuestOrder();

        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(orderAccessTokenProvider.getOrderNo("token")).willReturn("ORDER-1");
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any())).willReturn(tossResponse());
        given(paymentFinalizer.finalizePayment(any(), any(), any())).willReturn(response());

        paymentConfirmService.confirm(null, "token", request);

        verify(orderAccessTokenProvider, times(2)).getOrderNo("token");
    }

    @Test
    void confirm_succeedsForLoggedInUserAccessingGuestOrderWithToken() {
        ConfirmPaymentRequest request = request();
        PaymentOrder paymentOrder = pendingGuestOrder();

        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(orderAccessTokenProvider.getOrderNo("token")).willReturn("ORDER-1");
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any())).willReturn(tossResponse());
        given(paymentFinalizer.finalizePayment(any(), any(), any())).willReturn(response());

        paymentConfirmService.confirm(10L, "token", request);

        verify(orderAccessTokenProvider, times(2)).getOrderNo("token");
    }

    @Test
    void confirm_failsWhenGuestTokenIsMissing() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingGuestOrder()));

        assertBusinessException(
            () -> paymentConfirmService.confirm(null, null, request()),
            GlobalErrorCode.ORDER_ACCESS_TOKEN_REQUIRED
        );
    }

    @Test
    void confirm_failsWhenDifferentMemberAccessesOrder() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));

        assertBusinessException(
            () -> paymentConfirmService.confirm(20L, null, request()),
            GlobalErrorCode.PAYMENT_ACCESS_DENIED
        );
    }

    @Test
    void confirm_failsWhenOrderDoesNotExist() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.empty());

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_ORDER_NOT_FOUND
        );
    }

    @Test
    void confirm_failsWhenAmountDoesNotMatch() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));

        assertBusinessException(
            () -> paymentConfirmService.confirm(
                10L,
                null,
                new ConfirmPaymentRequest("payment-key", "ORDER-1", BigDecimal.valueOf(20000))
            ),
            GlobalErrorCode.PAYMENT_AMOUNT_MISMATCH
        );
    }

    @Test
    void confirm_failsWhenOrderIsFree() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(order(PaymentOrderStatus.PENDING, BigDecimal.ZERO, 10L)));

        assertBusinessException(
            () -> paymentConfirmService.confirm(
                10L,
                null,
                new ConfirmPaymentRequest("payment-key", "ORDER-1", BigDecimal.ZERO)
            ),
            GlobalErrorCode.PAYMENT_NOT_REQUIRED
        );
    }

    @Test
    void confirm_normalizesScaleZeroAmountBeforeCallingToss() {
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
            "payment-key",
            "ORDER-1",
            new BigDecimal("10000.00")
        );
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any())).willReturn(tossResponse());
        given(paymentFinalizer.finalizePayment(any(), any(), any())).willReturn(response());

        paymentConfirmService.confirm(10L, null, request);

        ArgumentCaptor<TossConfirmRequest> captor =
            ArgumentCaptor.forClass(TossConfirmRequest.class);
        verify(tossPaymentClient).confirm(captor.capture());
        assertThat(captor.getValue().amount()).isEqualTo(10000L);
    }

    @Test
    void confirm_failsBeforeCallingTossWhenAmountHasFraction() {
        ConfirmPaymentRequest request = new ConfirmPaymentRequest(
            "payment-key",
            "ORDER-1",
            new BigDecimal("10000.50")
        );
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(order(
                PaymentOrderStatus.PENDING,
                new BigDecimal("10000.50"),
                10L
            )));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request),
            GlobalErrorCode.INVALID_INPUT_VALUE
        );

        verify(tossPaymentClient, never()).confirm(any());
    }

    @Test
    void confirm_failsWhenOrderIsExpired() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(expiredOrder()));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_INVALID_STATE
        );
        verify(tossPaymentClient, never()).confirm(any());
    }

    @Test
    void confirm_failsWhenTicketOrderStateIsInvalid() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(confirmedTicketOrder()));

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_INVALID_STATE
        );
    }

    @Test
    void confirm_failsWithPaymentKeyAlreadyUsedBeforeCallingToss() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(paymentRepository.existsByPaymentKeyAndPaymentOrderIdNot("payment-key", 1L))
            .willReturn(true);

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_KEY_ALREADY_USED
        );

        verify(tossPaymentClient, never()).confirm(any());
    }


    @Test
    void confirm_failsWhenTossRejectsPayment() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any()))
            .willThrow(new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT));
        given(tossPaymentClient.getPayment("payment-key"))
            .willThrow(new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT));

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT
        );
    }

    @Test
    void confirm_recoversAlreadyProcessedPaymentFromTossLookup() {
        ConfirmPaymentRequest request = request();
        PaymentOrder paymentOrder = pendingMemberOrder(10L);
        TossConfirmResponse tossResponse = tossResponse();

        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any()))
            .willThrow(new TossPaymentClientException(
                GlobalErrorCode.PAYMENT_CONFIRM_REJECTED,
                "ALREADY_PROCESSED_PAYMENT"
            ));
        given(tossPaymentClient.getPayment("payment-key")).willReturn(tossResponse);
        given(paymentFinalizer.finalizePayment(10L, request, tossResponse)).willReturn(response());

        paymentConfirmService.confirm(10L, null, request);

        verify(tossPaymentClient).getPayment("payment-key");
        verify(paymentFinalizer).finalizePayment(10L, request, tossResponse);
    }

    @Test
    void confirm_failsWhenTossResponsePaymentKeyDoesNotMatch() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any()))
            .willReturn(new TossConfirmResponse(
                "other-key",
                "ORDER-1",
                BigDecimal.valueOf(10000),
                "DONE",
                "CARD",
                OffsetDateTime.now(),
                OffsetDateTime.now()
            ));

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
        );
    }

    @Test
    void confirm_failsWhenFinalizerLockTimesOut() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any())).willReturn(tossResponse());
        given(paymentFinalizer.finalizePayment(any(), any(), any()))
            .willThrow(new CannotAcquireLockException("lock timeout"));

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_PROCESSING_CONFLICT
        );
    }

    @Test
    void confirm_doesNotConvertUnrelatedDataIntegrityViolation() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any())).willReturn(tossResponse());
        given(paymentFinalizer.finalizePayment(any(), any(), any()))
            .willThrow(new DataIntegrityViolationException("other constraint"));

        assertThatThrownBy(() -> paymentConfirmService.confirm(10L, null, request()))
            .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void confirm_reusesCompletedPaymentWithoutCallingToss() {
        ConfirmPaymentRequest request = request();
        PaymentOrder paymentOrder = order(
            PaymentOrderStatus.PAID,
            BigDecimal.valueOf(10000),
            10L
        );
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(confirmedTicketOrder()));
        given(paymentFinalizer.finalizePayment(any(), any(), any())).willReturn(response());

        paymentConfirmService.confirm(10L, null, request);

        verify(tossPaymentClient, never()).confirm(any());
    }

    @Test
    void confirm_acceptsCardOrderWithTossKoreanCardMethod() {
        ConfirmPaymentRequest request = request();
        PaymentOrder paymentOrder = cardOrder();
        TossConfirmResponse tossResponse = tossDoneResponse("\uCE74\uB4DC");

        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any())).willReturn(tossResponse);
        given(paymentFinalizer.finalizePayment(10L, request, tossResponse)).willReturn(response());

        paymentConfirmService.confirm(10L, null, request);

        verify(paymentFinalizer).finalizePayment(10L, request, tossResponse);
        verify(virtualAccountPaymentService, never()).saveWaitingForDeposit(any(), any(), any(), any());
    }

    @Test
    void confirm_acceptsCardOrderWithTossEasyPayMethod() {
        ConfirmPaymentRequest request = request();
        PaymentOrder paymentOrder = cardOrder();
        TossConfirmResponse tossResponse = tossDoneResponse("\uAC04\uD3B8\uACB0\uC81C");

        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any())).willReturn(tossResponse);
        given(paymentFinalizer.finalizePayment(10L, request, tossResponse)).willReturn(response());

        paymentConfirmService.confirm(10L, null, request);

        verify(paymentFinalizer).finalizePayment(10L, request, tossResponse);
        verify(virtualAccountPaymentService, never()).saveWaitingForDeposit(any(), any(), any(), any());
    }

    @Test
    void confirm_rejectsCardOrderWithTossVirtualAccountMethod() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(cardOrder()));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any()))
            .willReturn(tossDoneResponse("\uAC00\uC0C1\uACC4\uC88C"));

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_METHOD_MISMATCH
        );

        verify(paymentFinalizer, never()).finalizePayment(any(), any(), any());
        verify(virtualAccountPaymentService, never()).saveWaitingForDeposit(any(), any(), any(), any());
    }

    @Test
    void confirm_acceptsWaitingVirtualAccountTossResponseWithKoreanMethodAndNullApprovedAt() {
        ConfirmPaymentRequest request = request();
        PaymentOrder paymentOrder = virtualAccountOrder();
        TicketOrder ticketOrder = pendingTicketOrder();
        TossConfirmResponse tossResponse = new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "WAITING_FOR_DEPOSIT",
            "VIRTUAL_ACCOUNT",
            OffsetDateTime.now(),
            null
        );
        ConfirmPaymentResponse response = response();

        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(ticketOrder));
        given(tossPaymentClient.confirm(any())).willReturn(tossResponse);
        given(virtualAccountPaymentService.saveWaitingForDeposit(10L, paymentOrder, "payment-key",
            tossResponse
        )).willReturn(response);

        paymentConfirmService.confirm(10L, null, request);

        verify(virtualAccountPaymentService).saveWaitingForDeposit(10L, paymentOrder, "payment-key",
            tossResponse
        );
        verify(paymentFinalizer, never()).finalizePayment(any(), any(), any());
    }

    @Test
    void confirm_rejectsVirtualAccountTossResponseWithWrongMethod() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(virtualAccountOrder()));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any())).willReturn(new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "WAITING_FOR_DEPOSIT",
            "CARD",
            OffsetDateTime.now(),
            null
        ));

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_METHOD_MISMATCH
        );

        verify(virtualAccountPaymentService, never()).saveWaitingForDeposit(any(), any(), any(), any());
    }

    @Test
    void confirm_rejectsVirtualAccountTossResponseWithEasyPayMethod() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(virtualAccountOrder()));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any())).willReturn(new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "DONE",
            "\uAC04\uD3B8\uACB0\uC81C",
            OffsetDateTime.now(),
            OffsetDateTime.now()
        ));

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_METHOD_MISMATCH
        );

        verify(virtualAccountPaymentService, never()).saveWaitingForDeposit(any(), any(), any(), any());
        verify(paymentFinalizer, never()).finalizePayment(any(), any(), any());
    }

    @Test
    void confirm_rejectsVirtualAccountTossResponseWithWrongStatus() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(virtualAccountOrder()));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any())).willReturn(new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "DONE",
            "VIRTUAL_ACCOUNT",
            OffsetDateTime.now(),
            OffsetDateTime.now()
        ));

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_GATEWAY_RESPONSE_INVALID
        );

        verify(virtualAccountPaymentService, never()).saveWaitingForDeposit(any(), any(), any(), any());
    }

    @Test
    void confirm_rejectsAlreadyInFlightWhenOrderIsStillPending() {
        ConfirmPaymentRequest request = request();
        PaymentOrder paymentOrder = pendingMemberOrder(10L);
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(paymentOrder), Optional.of(paymentOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(inflightDuplicateGate.tryClaim("ORDER-1"))
            .willReturn(PaymentConfirmInflightClaim.alreadyInFlight());

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request),
            GlobalErrorCode.PAYMENT_CONFIRM_IN_PROGRESS
        );

        verify(tossPaymentClient, never()).confirm(any());
    }

    @Test
    void confirm_failsOpenWhenRedisGateFailsOpen() {
        ConfirmPaymentRequest request = request();
        TossConfirmResponse tossResponse = tossResponse();
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(inflightDuplicateGate.tryClaim("ORDER-1")).willReturn(PaymentConfirmInflightClaim.failOpen());
        given(tossPaymentClient.confirm(any())).willReturn(tossResponse);
        given(paymentFinalizer.finalizePayment(10L, request, tossResponse)).willReturn(response());

        paymentConfirmService.confirm(10L, null, request);

        verify(tossPaymentClient).confirm(any());
        verify(inflightDuplicateGate, never()).release(any(), any());
    }

    @Test
    void confirm_reloadsPaidOrderAfterAcquiredAndSkipsTossConfirm() {
        ConfirmPaymentRequest request = request();
        PaymentOrder pendingOrder = pendingMemberOrder(10L);
        PaymentOrder paidOrder = order(
            PaymentOrderStatus.PAID,
            BigDecimal.valueOf(10000),
            10L
        );
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingOrder), Optional.of(paidOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()), Optional.of(confirmedTicketOrder()));
        given(paymentFinalizer.finalizePayment(any(), any(), any())).willReturn(response());

        paymentConfirmService.confirm(10L, null, request);

        verify(tossPaymentClient, never()).confirm(any());
        verify(paymentFinalizer).finalizePayment(any(), any(), any());
        verify(inflightDuplicateGate).release("ORDER-1", "token-1");
    }

    @Test
    void confirm_reloadsWaitingForDepositOrderAfterAcquiredAndSkipsTossConfirm() {
        ConfirmPaymentRequest request = request();
        PaymentOrder pendingOrder = virtualAccountOrder();
        PaymentOrder waitingOrder = PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT)
            .status(PaymentOrderStatus.WAITING_FOR_DEPOSIT.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();

        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingOrder), Optional.of(waitingOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(virtualAccountPaymentService.getWaitingForDeposit(waitingOrder, "payment-key"))
            .willReturn(response());

        paymentConfirmService.confirm(10L, null, request);

        verify(tossPaymentClient, never()).confirm(any());
        verify(virtualAccountPaymentService).getWaitingForDeposit(waitingOrder, "payment-key");
        verify(inflightDuplicateGate).release("ORDER-1", "token-1");
    }

    @Test
    void confirm_recoversTimeoutByProviderLookupDone() {
        ConfirmPaymentRequest request = request();
        TossConfirmResponse tossResponse = tossResponse();
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any()))
            .willThrow(new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT));
        given(tossPaymentClient.getPayment("payment-key")).willReturn(tossResponse);
        given(paymentFinalizer.finalizePayment(10L, request, tossResponse)).willReturn(response());

        paymentConfirmService.confirm(10L, null, request);

        verify(tossPaymentClient).getPayment("payment-key");
        verify(paymentFinalizer).finalizePayment(10L, request, tossResponse);
    }

    @Test
    void confirm_recoversTimeoutByProviderLookupWaitingForDeposit() {
        ConfirmPaymentRequest request = request();
        PaymentOrder paymentOrder = virtualAccountOrder();
        TossConfirmResponse tossResponse = virtualAccountWaitingResponse();
        ConfirmPaymentResponse response = response();

        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any()))
            .willThrow(new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT));
        given(tossPaymentClient.getPayment("payment-key")).willReturn(tossResponse);
        given(virtualAccountPaymentService.saveWaitingForDeposit(10L, paymentOrder, "payment-key",
            tossResponse
        )).willReturn(response);

        paymentConfirmService.confirm(10L, null, request);

        verify(virtualAccountPaymentService).saveWaitingForDeposit(10L, paymentOrder, "payment-key",
            tossResponse
        );
        verify(paymentFinalizer, never()).finalizePayment(any(), any(), any());
    }

    @Test
    void confirm_doesNotRecoverClearProviderRejection() {
        given(paymentOrderRepository.findByOrderNo("ORDER-1"))
            .willReturn(Optional.of(pendingMemberOrder(10L)));
        given(ticketOrderRepository.findByPaymentOrderId(1L))
            .willReturn(Optional.of(pendingTicketOrder()));
        given(tossPaymentClient.confirm(any()))
            .willThrow(new TossPaymentClientException(GlobalErrorCode.PAYMENT_CONFIRM_REJECTED));

        assertBusinessException(
            () -> paymentConfirmService.confirm(10L, null, request()),
            GlobalErrorCode.PAYMENT_CONFIRM_REJECTED
        );

        verify(tossPaymentClient, never()).getPayment(any());
    }

    private void assertBusinessException(
            Runnable runnable,
            GlobalErrorCode errorCode) {
        assertThatThrownBy(runnable::run)
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(errorCode);
    }

    private ConfirmPaymentRequest request() {
        return new ConfirmPaymentRequest(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000)
        );
    }

    private TossConfirmResponse tossResponse() {
        return new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "DONE",
            "CARD",
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );
    }

    private ConfirmPaymentResponse response() {
        return new ConfirmPaymentResponse(
            1L,
            "ORDER-1",
            2L,
            BigDecimal.valueOf(10000),
            PaymentOrderStatus.PAID.name(),
            TicketOrderStatus.CONFIRMED.name(),
            OffsetDateTime.now()
        );
    }

    private PaymentOrder pendingMemberOrder(Long memberId) {
        return order(PaymentOrderStatus.PENDING, BigDecimal.valueOf(10000), memberId);
    }

    private PaymentOrder pendingGuestOrder() {
        return order(PaymentOrderStatus.PENDING, BigDecimal.valueOf(10000), null);
    }

    private PaymentOrder cardOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.CARD)
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private PaymentOrder expiredOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(OffsetDateTime.now().minusMinutes(1))
            .createdAt(OffsetDateTime.now().minusMinutes(10))
            .updatedAt(OffsetDateTime.now().minusMinutes(10))
            .build();
    }

    private PaymentOrder virtualAccountOrder() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT)
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private PaymentOrder order(
            PaymentOrderStatus status,
            BigDecimal amount,
            Long memberId) {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(memberId)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(amount)
            .status(status.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }

    private TossConfirmResponse tossDoneResponse(String method) {
        return new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "DONE",
            method,
            OffsetDateTime.now(),
            OffsetDateTime.now()
        );
    }

    private TossConfirmResponse virtualAccountWaitingResponse() {
        return new TossConfirmResponse(
            "payment-key",
            "ORDER-1",
            BigDecimal.valueOf(10000),
            "WAITING_FOR_DEPOSIT",
            "VIRTUAL_ACCOUNT",
            "secret",
            new TossConfirmResponse.VirtualAccount(
                "1234567890",
                "088",
                "tester",
                OffsetDateTime.now().plusMinutes(30)
            ),
            OffsetDateTime.now(),
            null
        );
    }

    private TicketOrder pendingTicketOrder() {
        return ticketOrder(TicketOrderStatus.PENDING_PAYMENT);
    }

    private TicketOrder confirmedTicketOrder() {
        return ticketOrder(TicketOrderStatus.CONFIRMED);
    }

    private TicketOrder ticketOrder(TicketOrderStatus status) {
        return TicketOrder.builder()
            .id(2L)
            .paymentOrderId(1L)
            .eventId(3L)
            .unitPrice(BigDecimal.valueOf(10000))
            .totalQuantity(1)
            .status(status.name())
            .confirmedAt(status == TicketOrderStatus.CONFIRMED ? OffsetDateTime.now() : null)
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
    }
}
