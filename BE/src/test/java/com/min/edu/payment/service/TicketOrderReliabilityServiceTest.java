package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.config.TicketOrderReliabilityProperties;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderIdempotencyRequest;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestBuyerRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;
import com.min.edu.payment.dto.response.GuestOrderAccessTokenResponse;
import com.min.edu.payment.exception.TicketOrderBusyException;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.TicketOrderIdempotencyRequestRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.admission.repository.ExchangeCodeRepository;

@ExtendWith(MockitoExtension.class)
class TicketOrderReliabilityServiceTest {

    @Mock
    private TicketOrderInflightDuplicateGate inflightDuplicateGate;

    @Mock
    private TicketOrderIdempotencyClaimService idempotencyClaimService;

    @Mock
    private TicketOrderAdmissionGate admissionGate;

    @Mock
    private TicketOrderCreationProcessor creationProcessor;

    @Mock
    private TicketOrderIdempotencyRequestRepository idempotencyRequestRepository;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private ExchangeCodeRepository exchangeCodeRepository;

    @Mock
    private GuestOrderAccessService guestOrderAccessService;

    private TicketOrderService service;

    @BeforeEach
    void setUp() {
        TicketOrderReliabilityProperties properties = new TicketOrderReliabilityProperties();
        service = new TicketOrderService(
            new TicketOrderRequestHasher(),
            inflightDuplicateGate,
            idempotencyClaimService,
            admissionGate,
            creationProcessor,
            idempotencyRequestRepository,
            paymentOrderRepository,
            ticketOrderRepository,
            exchangeCodeRepository,
            guestOrderAccessService,
            properties
        );
    }

    @Test
    void create_returnsCompletedGuestOrderWithoutCreatingDuplicateOrder() {
        CreateTicketOrderRequest request = guestRequest(1);
        TicketOrderIdempotencyRequest completed = completedIdempotency();
        PaymentOrder paymentOrder = guestPaymentOrder(10L);
        TicketOrder ticketOrder = ticketOrder(20L, paymentOrder.getId());

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.ACQUIRED);
        given(idempotencyClaimService.claim(eq("key-1"), any(), eq(1L)))
            .willReturn(TicketOrderIdempotencyClaimResult.completed(completed));
        given(paymentOrderRepository.findById(10L)).willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findById(20L)).willReturn(Optional.of(ticketOrder));
        given(guestOrderAccessService.issueGuestAccessToken(
                eq("ORDER-1"),
                any()))
            .willReturn(new GuestOrderAccessTokenResponse(
                "ORDER-1",
                "TOKEN-B",
                OffsetDateTime.now().plusDays(1)
            ));

        CreateTicketOrderResponse response = service.create("key-1", 1L, null, request);

        assertThat(response.getOrderNo()).isEqualTo("ORDER-1");
        assertThat(response.getOrderAccessToken()).isEqualTo("TOKEN-B");
        verify(creationProcessor, never()).create(any(), any(), any(), any());
    }

    @Test
    void create_rejectsSameKeyWithDifferentFingerprintBeforeGuestTokenIssuance() {
        CreateTicketOrderRequest request = guestRequest(1);

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.ALREADY_IN_FLIGHT);
        given(idempotencyRequestRepository.findByIdempotencyKey("key-1"))
            .willReturn(Optional.of(completedIdempotencyWithHash("different-hash")));

        assertThatThrownBy(() -> service.create("key-1", 1L, null, request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.IDEMPOTENCY_KEY_CONFLICT);

        verify(guestOrderAccessService, never()).issueGuestAccessToken(any(), any());
        verify(creationProcessor, never()).create(any(), any(), any(), any());
    }

    @Test
    void create_rejectsProcessingDuplicate() {
        CreateTicketOrderRequest request = guestRequest(1);

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.ALREADY_IN_FLIGHT);
        given(idempotencyRequestRepository.findByIdempotencyKey("key-1"))
            .willReturn(Optional.of(processingIdempotency(
                new TicketOrderRequestHasher().hash(1L, null, request)
            )));

        assertThatThrownBy(() -> service.create("key-1", 1L, null, request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.IDEMPOTENCY_REQUEST_IN_PROGRESS);
    }

    @Test
    void create_rejectsAdmissionOverflowBeforeDbIdempotencyClaim() {
        CreateTicketOrderRequest request = guestRequest(1);

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.ACQUIRED);
        given(admissionGate.tryAcquire(1L, "key-1")).willReturn(AdmissionResult.REJECTED);
        given(idempotencyRequestRepository.findByIdempotencyKey("key-1"))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.create("key-1", 1L, null, request))
            .isInstanceOf(TicketOrderBusyException.class)
            .extracting("retryAfterSeconds")
            .isEqualTo(1L);

        verify(idempotencyClaimService, never()).claim(any(), any(), any());
        verify(idempotencyClaimService, never()).markFailed(any());
        verify(creationProcessor, never()).create(any(), any(), any(), any());
    }

    @Test
    void create_returnsCompletedOrderWhenAdmissionRejectsCompletedRetry() {
        CreateTicketOrderRequest request = guestRequest(1);
        TicketOrderIdempotencyRequest completed = completedIdempotency();
        PaymentOrder paymentOrder = guestPaymentOrder(10L);
        TicketOrder ticketOrder = ticketOrder(20L, paymentOrder.getId());

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.ACQUIRED);
        given(admissionGate.tryAcquire(1L, "key-1")).willReturn(AdmissionResult.REJECTED);
        given(idempotencyRequestRepository.findByIdempotencyKey("key-1"))
            .willReturn(Optional.of(completed));
        given(paymentOrderRepository.findById(10L)).willReturn(Optional.of(paymentOrder));
        given(ticketOrderRepository.findById(20L)).willReturn(Optional.of(ticketOrder));
        given(guestOrderAccessService.issueGuestAccessToken(eq("ORDER-1"), any()))
            .willReturn(new GuestOrderAccessTokenResponse(
                "ORDER-1",
                "TOKEN-B",
                OffsetDateTime.now().plusDays(1)
            ));

        CreateTicketOrderResponse response = service.create("key-1", 1L, null, request);

        assertThat(response.getOrderAccessToken()).isEqualTo("TOKEN-B");
        verify(idempotencyClaimService, never()).claim(any(), any(), any());
        verify(creationProcessor, never()).create(any(), any(), any(), any());
    }

    @Test
    void create_marksFailedWhenAdmittedBusinessExecutionFailsAfterClaim() {
        CreateTicketOrderRequest request = guestRequest(1);
        TicketOrderIdempotencyRequest claimed = processingIdempotency(
            new TicketOrderRequestHasher().hash(1L, null, request)
        );

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.ACQUIRED);
        given(admissionGate.tryAcquire(1L, "key-1")).willReturn(AdmissionResult.ACQUIRED);
        given(idempotencyClaimService.claim(eq("key-1"), any(), eq(1L)))
            .willReturn(TicketOrderIdempotencyClaimResult.claimed(claimed));
        given(creationProcessor.create("key-1", 1L, null, request))
            .willThrow(new BusinessException(GlobalErrorCode.TICKET_SOLD_OUT));

        assertThatThrownBy(() -> service.create("key-1", 1L, null, request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.TICKET_SOLD_OUT);

        verify(idempotencyClaimService).markFailed("key-1");
    }

    @Test
    void create_continuesWhenRedisGatesFailOpen() {
        CreateTicketOrderRequest request = guestRequest(1);
        TicketOrderIdempotencyRequest claimed = processingIdempotency(
            new TicketOrderRequestHasher().hash(1L, null, request)
        );
        CreateTicketOrderResponse created = new CreateTicketOrderResponse(
            "ORDER-1",
            20L,
            1,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(10000),
            true,
            PaymentOrderStatus.PENDING.name(),
            null,
            OffsetDateTime.now().plusMinutes(10),
            null,
            "TOKEN-A"
        );

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.FAIL_OPEN);
        given(idempotencyClaimService.claim(eq("key-1"), any(), eq(1L)))
            .willReturn(TicketOrderIdempotencyClaimResult.claimed(claimed));
        given(admissionGate.tryAcquire(1L, "key-1")).willReturn(AdmissionResult.FAIL_OPEN);
        given(creationProcessor.create("key-1", 1L, null, request)).willReturn(created);

        CreateTicketOrderResponse response = service.create("key-1", 1L, null, request);

        assertThat(response).isSameAs(created);
    }

    private CreateTicketOrderRequest guestRequest(int quantity) {
        return new CreateTicketOrderRequest(
            quantity,
            new GuestBuyerRequest("guest", "guest@example.com", "010-1234-5678")
        );
    }

    private TicketOrderIdempotencyRequest completedIdempotency() {
        return completedIdempotencyWithHash(
            new TicketOrderRequestHasher().hash(1L, null, guestRequest(1))
        );
    }

    private TicketOrderIdempotencyRequest completedIdempotencyWithHash(String requestHash) {
        TicketOrderIdempotencyRequest request = processingIdempotency(requestHash);
        request.complete(10L, 20L, OffsetDateTime.now());
        return request;
    }

    private TicketOrderIdempotencyRequest processingIdempotency(String requestHash) {
        OffsetDateTime now = OffsetDateTime.now();
        return TicketOrderIdempotencyRequest.processing(
            "key-1",
            requestHash,
            1L,
            now,
            now.plusMinutes(15)
        );
    }

    private PaymentOrder guestPaymentOrder(Long id) {
        OffsetDateTime now = OffsetDateTime.now();
        return PaymentOrder.builder()
            .id(id)
            .orderNo("ORDER-1")
            .buyerName("guest")
            .buyerEmail("guest@example.com")
            .buyerPhone("010-1234-5678")
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.CARD)
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(now.plusMinutes(10))
            .createdAt(now)
            .updatedAt(now)
            .build();
    }

    private TicketOrder ticketOrder(Long id, Long paymentOrderId) {
        OffsetDateTime now = OffsetDateTime.now();
        return TicketOrder.builder()
            .id(id)
            .paymentOrderId(paymentOrderId)
            .eventId(1L)
            .unitPrice(BigDecimal.valueOf(10000))
            .totalQuantity(1)
            .status(TicketOrderStatus.PENDING_PAYMENT.name())
            .createdAt(now)
            .updatedAt(now)
            .build();
    }
}
