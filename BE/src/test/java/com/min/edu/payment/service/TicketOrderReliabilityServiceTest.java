package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
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
import org.springframework.transaction.support.TransactionSynchronizationManager;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.config.TicketOrderReliabilityProperties;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.TicketOrderIdempotencyRequest;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestBuyerRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;
import com.min.edu.payment.exception.TicketOrderBusyException;
import com.min.edu.payment.repository.TicketOrderIdempotencyRequestRepository;

@ExtendWith(MockitoExtension.class)
class TicketOrderReliabilityServiceTest {

    @Mock
    private TicketOrderInflightDuplicateGate inflightDuplicateGate;

    @Mock
    private TicketOrderIdempotencyClaimService idempotencyClaimService;

    @Mock
    private TicketOrderAdmissionGate admissionGate;

    @Mock
    private TicketOrderTransactionalCreator transactionalCreator;

    @Mock
    private TicketOrderCompletedResponseService completedResponseService;

    @Mock
    private TicketOrderIdempotencyRequestRepository idempotencyRequestRepository;

    private TicketOrderService service;

    @BeforeEach
    void setUp() {
        TicketOrderReliabilityProperties properties = new TicketOrderReliabilityProperties();
        service = new TicketOrderService(
            new TicketOrderRequestHasher(),
            inflightDuplicateGate,
            idempotencyClaimService,
            admissionGate,
            transactionalCreator,
            completedResponseService,
            idempotencyRequestRepository,
            properties
        );
    }

    @Test
    void create_returnsCompletedGuestOrderWithoutCreatingDuplicateOrder() {
        CreateTicketOrderRequest request = guestRequest(1);
        CreateTicketOrderResponse completed = response("ORDER-1", "TOKEN-B");

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.ACQUIRED);
        given(transactionalCreator.execute(eq("key-1"), any(), eq(1L), eq(null), eq(request)))
            .willReturn(completed);

        CreateTicketOrderResponse response = service.create("key-1", 1L, null, request);

        assertThat(response.getOrderNo()).isEqualTo("ORDER-1");
        assertThat(response.getOrderAccessToken()).isEqualTo("TOKEN-B");
        verify(idempotencyClaimService, never()).claim(any(), any(), any());
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

        verify(completedResponseService, never()).completedResponse(any(), any());
        verify(transactionalCreator, never()).execute(any(), any(), any(), any(), any());
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
        verify(transactionalCreator, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void create_returnsCompletedOrderWhenAdmissionRejectsCompletedRetry() {
        CreateTicketOrderRequest request = guestRequest(1);
        TicketOrderIdempotencyRequest completed = completedIdempotency();
        CreateTicketOrderResponse completedResponse = response("ORDER-1", "TOKEN-B");

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.ACQUIRED);
        given(admissionGate.tryAcquire(1L, "key-1")).willReturn(AdmissionResult.REJECTED);
        given(idempotencyRequestRepository.findByIdempotencyKey("key-1"))
            .willReturn(Optional.of(completed));
        given(completedResponseService.completedResponse(completed, request))
            .willReturn(completedResponse);

        CreateTicketOrderResponse response = service.create("key-1", 1L, null, request);

        assertThat(response.getOrderAccessToken()).isEqualTo("TOKEN-B");
        verify(idempotencyClaimService, never()).claim(any(), any(), any());
        verify(transactionalCreator, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void create_rejectsAdmissionOverflowSameKeyWithDifferentFingerprintAsConflict() {
        CreateTicketOrderRequest request = guestRequest(1);

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.ACQUIRED);
        given(admissionGate.tryAcquire(1L, "key-1")).willReturn(AdmissionResult.REJECTED);
        given(idempotencyRequestRepository.findByIdempotencyKey("key-1"))
            .willReturn(Optional.of(completedIdempotencyWithHash("different-hash")));

        assertThatThrownBy(() -> service.create("key-1", 1L, null, request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.IDEMPOTENCY_KEY_CONFLICT);

        verify(idempotencyClaimService, never()).claim(any(), any(), any());
        verify(transactionalCreator, never()).execute(any(), any(), any(), any(), any());
    }

    @Test
    void create_marksFailedAfterTransactionalCreationRollbackWhenCreationFails() {
        CreateTicketOrderRequest request = guestRequest(1);
        BusinessException failure = new BusinessException(GlobalErrorCode.TICKET_SOLD_OUT);

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.ACQUIRED);
        given(admissionGate.tryAcquire(1L, "key-1")).willReturn(AdmissionResult.ACQUIRED);
        given(transactionalCreator.execute(eq("key-1"), any(), eq(1L), eq(null), eq(request)))
            .willThrow(new TicketOrderBusinessCreationFailedException(failure));

        assertThatThrownBy(() -> service.create("key-1", 1L, null, request))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.TICKET_SOLD_OUT);

        verify(idempotencyClaimService).markFailed(eq("key-1"), any(), eq(1L));
    }

    @Test
    void create_continuesWhenRedisGatesFailOpen() {
        CreateTicketOrderRequest request = guestRequest(1);
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
        given(admissionGate.tryAcquire(1L, "key-1")).willReturn(AdmissionResult.FAIL_OPEN);
        given(transactionalCreator.execute(eq("key-1"), any(), eq(1L), eq(null), eq(request)))
            .willReturn(created);

        CreateTicketOrderResponse response = service.create("key-1", 1L, null, request);

        assertThat(response).isSameAs(created);
    }

    @Test
    void create_releasesRedisAfterTransactionalCreatorReturnsOutsideTransaction() {
        CreateTicketOrderRequest request = guestRequest(1);
        CreateTicketOrderResponse created = response("ORDER-1", "TOKEN-A");

        given(inflightDuplicateGate.tryClaim("key-1")).willReturn(InflightClaimResult.ACQUIRED);
        given(admissionGate.tryAcquire(1L, "key-1")).willReturn(AdmissionResult.ACQUIRED);
        given(transactionalCreator.execute(eq("key-1"), any(), eq(1L), eq(null), eq(request)))
            .willReturn(created);
        doAnswer(invocation -> {
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isFalse();
            return null;
        }).when(admissionGate).release(1L, "key-1");

        service.create("key-1", 1L, null, request);

        verify(admissionGate).release(1L, "key-1");
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

    private CreateTicketOrderResponse response(String orderNo, String accessToken) {
        return new CreateTicketOrderResponse(
            orderNo,
            20L,
            1,
            BigDecimal.valueOf(10000),
            BigDecimal.valueOf(10000),
            true,
            PaymentOrderStatus.PENDING.name(),
            null,
            OffsetDateTime.now().plusMinutes(10),
            null,
            accessToken
        );
    }
}
