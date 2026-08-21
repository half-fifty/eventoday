package com.min.edu.payment.service;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.config.VirtualAccountReconciliationProperties;
import com.min.edu.payment.domain.PaymentMethod;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.PaymentOrderType;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.toss.TossPaymentClient;
import com.min.edu.payment.toss.TossPaymentClientException;
import com.min.edu.payment.toss.dto.TossConfirmResponse;
import java.math.BigDecimal;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class VirtualAccountReconciliationServiceTest {

    @Mock private PaymentOrderRepository paymentOrderRepository;
    @Mock private TossPaymentClient tossPaymentClient;
    @Mock private VirtualAccountReconciliationRepairService repairService;

    private VirtualAccountReconciliationService service;

    @BeforeEach
    void setUp() {
        VirtualAccountReconciliationProperties properties =
            new VirtualAccountReconciliationProperties();
        properties.setEnabled(true);
        properties.setFixedDelay(Duration.ofSeconds(60));
        properties.setAgeThreshold(Duration.ofSeconds(60));
        properties.setBatchSize(50);
        service = new VirtualAccountReconciliationService(
            paymentOrderRepository,
            tossPaymentClient,
            repairService,
            properties
        );
    }

    @Test
    void reconcilePendingVirtualAccountOrders_usesOrderIdLookupThenRepair() {
        PaymentOrder order = candidate();
        TossConfirmResponse providerPayment = providerPayment();
        given(paymentOrderRepository.findSuspiciousPendingVirtualAccountOrders(any(), any(Pageable.class)))
            .willReturn(List.of(order));
        given(tossPaymentClient.getPaymentByOrderId("ORDER-1")).willReturn(providerPayment);

        service.reconcilePendingVirtualAccountOrders();

        verify(tossPaymentClient).getPaymentByOrderId("ORDER-1");
        verify(repairService).repairPendingOrder("ORDER-1", providerPayment);
    }

    @Test
    void reconcilePendingVirtualAccountOrders_doesNotRepairOnProviderTimeout() {
        given(paymentOrderRepository.findSuspiciousPendingVirtualAccountOrders(any(), any(Pageable.class)))
            .willReturn(List.of(candidate()));
        given(tossPaymentClient.getPaymentByOrderId("ORDER-1"))
            .willThrow(new TossPaymentClientException(GlobalErrorCode.PAYMENT_GATEWAY_TIMEOUT));

        service.reconcilePendingVirtualAccountOrders();

        verify(repairService, never()).repairPendingOrder(eq("ORDER-1"), any());
    }

    private PaymentOrder candidate() {
        return PaymentOrder.builder()
            .id(1L)
            .orderNo("ORDER-1")
            .buyerMemberId(10L)
            .orderType(PaymentOrderType.EVENT_TICKET)
            .totalAmount(BigDecimal.valueOf(10000))
            .requestedPaymentMethod(PaymentMethod.VIRTUAL_ACCOUNT)
            .status(PaymentOrderStatus.PENDING.name())
            .expiresAt(OffsetDateTime.now().plusMinutes(10))
            .createdAt(OffsetDateTime.now().minusMinutes(2))
            .updatedAt(OffsetDateTime.now().minusMinutes(2))
            .build();
    }

    private TossConfirmResponse providerPayment() {
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
            OffsetDateTime.now().minusMinutes(1),
            null
        );
    }
}
