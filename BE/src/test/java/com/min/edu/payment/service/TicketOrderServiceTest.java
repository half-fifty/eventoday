package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.admission.support.ExchangeCodeGenerator;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.payment.domain.PaymentOrder;
import com.min.edu.payment.domain.PaymentOrderStatus;
import com.min.edu.payment.domain.TicketOrder;
import com.min.edu.payment.domain.TicketOrderStatus;
import com.min.edu.payment.dto.request.CreateTicketOrderRequest;
import com.min.edu.payment.dto.request.GuestBuyerRequest;
import com.min.edu.payment.dto.response.CreateTicketOrderResponse;
import com.min.edu.payment.event.EventTicketReader;
import com.min.edu.payment.event.EventTicketSnapshot;
import com.min.edu.payment.event.TicketInventoryGateway;
import com.min.edu.payment.policy.TicketOrderPolicy;
import com.min.edu.payment.repository.PaymentOrderRepository;
import com.min.edu.payment.repository.TicketOrderRepository;
import com.min.edu.payment.support.OrderAccessTokenProvider;
import com.min.edu.payment.support.OrderNoGenerator;

@ExtendWith(MockitoExtension.class)
class TicketOrderServiceTest {

    @Mock
    private EventTicketReader eventTicketReader;

    @Mock
    private TicketInventoryGateway ticketInventoryGateway;

    @Mock
    private PaymentOrderRepository paymentOrderRepository;

    @Mock
    private TicketOrderRepository ticketOrderRepository;

    @Mock
    private ExchangeCodeRepository exchangeCodeRepository;

    @Mock
    private OrderNoGenerator orderNoGenerator;

    @Mock
    private ExchangeCodeGenerator exchangeCodeGenerator;

    @Mock
    private OrderAccessTokenProvider orderAccessTokenProvider;

    private TicketOrderPolicy ticketOrderPolicy;

    @InjectMocks
    private TicketOrderService ticketOrderService;

    @BeforeEach
    void setUp() {
        ticketOrderPolicy = new TicketOrderPolicy();
        ticketOrderService = new TicketOrderService(
            eventTicketReader,
            ticketInventoryGateway,
            paymentOrderRepository,
            ticketOrderRepository,
            exchangeCodeRepository,
            orderNoGenerator,
            exchangeCodeGenerator,
            ticketOrderPolicy,
            orderAccessTokenProvider
        );
    }

    @Test
    void create_createsPaidOrderForMember() {
        givenDefaultOrderDependencies(paidEvent());

        CreateTicketOrderResponse response = ticketOrderService.create(
            1L,
            10L,
            new CreateTicketOrderRequest(2, null)
        );

        ArgumentCaptor<PaymentOrder> paymentCaptor =
            ArgumentCaptor.forClass(PaymentOrder.class);
        ArgumentCaptor<TicketOrder> ticketCaptor =
            ArgumentCaptor.forClass(TicketOrder.class);

        verify(paymentOrderRepository).save(paymentCaptor.capture());
        verify(ticketOrderRepository).save(ticketCaptor.capture());
        verify(exchangeCodeRepository, never()).save(any());

        PaymentOrder paymentOrder = paymentCaptor.getValue();
        TicketOrder ticketOrder = ticketCaptor.getValue();

        assertThat(paymentOrder.getBuyerMemberId()).isEqualTo(10L);
        assertThat(paymentOrder.getTotalAmount()).isEqualByComparingTo("20000");
        assertThat(paymentOrder.getStatus()).isEqualTo(PaymentOrderStatus.PENDING.name());
        assertThat(paymentOrder.getExpiresAt()).isNotNull();
        assertThat(paymentOrder.getExpiresAt())
            .isEqualTo(paymentOrder.getCreatedAt().plusMinutes(10));
        assertThat(ticketOrder.getStatus()).isEqualTo(TicketOrderStatus.PENDING_PAYMENT.name());
        assertThat(ticketOrder.getConfirmedAt()).isNull();
        assertThat(response.getPaymentRequired()).isTrue();
        assertThat(response.getOrderAccessToken()).isNull();
    }

    @Test
    void create_ignoresBuyerSnapshotFieldsForMemberOrder() {
        givenDefaultOrderDependencies(paidEvent());

        ticketOrderService.create(
            1L,
            10L,
            guestRequest(2)
        );

        ArgumentCaptor<PaymentOrder> paymentCaptor =
            ArgumentCaptor.forClass(PaymentOrder.class);

        verify(paymentOrderRepository).save(paymentCaptor.capture());

        PaymentOrder paymentOrder = paymentCaptor.getValue();
        assertThat(paymentOrder.getBuyerMemberId()).isEqualTo(10L);
        assertThat(paymentOrder.getBuyerName()).isNull();
        assertThat(paymentOrder.getBuyerEmail()).isNull();
        assertThat(paymentOrder.getBuyerPhone()).isNull();
    }

    @Test
    void create_createsPaidOrderForGuest() {
        givenDefaultOrderDependencies(paidEvent());

        given(orderAccessTokenProvider.create(
                eq("EVT-20260803-A81C29F4307B"),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
            .willReturn("guest-order-token");

        CreateTicketOrderResponse response = ticketOrderService.create(
            1L,
            null,
            guestRequest(2)
        );

        ArgumentCaptor<PaymentOrder> paymentCaptor =
            ArgumentCaptor.forClass(PaymentOrder.class);

        verify(paymentOrderRepository).save(paymentCaptor.capture());

        PaymentOrder paymentOrder = paymentCaptor.getValue();
        assertThat(paymentOrder.getBuyerMemberId()).isNull();
        assertThat(paymentOrder.getBuyerName()).isEqualTo("guest");
        assertThat(paymentOrder.getBuyerEmail()).isEqualTo("guest@example.com");
        assertThat(paymentOrder.getBuyerPhone()).isEqualTo("010-1234-5678");
        assertThat(response.getOrderAccessToken()).isEqualTo("guest-order-token");
    }

    @Test
    void create_createsFreeOrderAndExchangeCodesForMember() {
        givenDefaultOrderDependencies(freeEvent());
        given(exchangeCodeGenerator.generate())
            .willReturn("A1B2-C3D4-E5F6", "0000-1111-2222");
        when(exchangeCodeRepository.save(any(ExchangeCode.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreateTicketOrderResponse response = ticketOrderService.create(
            1L,
            10L,
            new CreateTicketOrderRequest(2, null)
        );

        ArgumentCaptor<PaymentOrder> paymentCaptor =
            ArgumentCaptor.forClass(PaymentOrder.class);
        ArgumentCaptor<TicketOrder> ticketCaptor =
            ArgumentCaptor.forClass(TicketOrder.class);
        ArgumentCaptor<ExchangeCode> exchangeCaptor =
            ArgumentCaptor.forClass(ExchangeCode.class);

        verify(paymentOrderRepository).save(paymentCaptor.capture());
        verify(ticketOrderRepository).save(ticketCaptor.capture());
        verify(exchangeCodeRepository, org.mockito.Mockito.times(2))
            .save(exchangeCaptor.capture());

        assertThat(paymentCaptor.getValue().getStatus())
            .isEqualTo(PaymentOrderStatus.PAID.name());
        assertThat(paymentCaptor.getValue().getExpiresAt()).isNull();
        assertThat(ticketCaptor.getValue().getStatus())
            .isEqualTo(TicketOrderStatus.CONFIRMED.name());
        assertThat(ticketCaptor.getValue().getConfirmedAt()).isNotNull();
        assertThat(exchangeCaptor.getAllValues())
            .hasSize(2)
            .allSatisfy(exchangeCode ->
                assertThat(exchangeCode.getHolderMemberId()).isEqualTo(10L));
        assertThat(response.getPaymentRequired()).isFalse();
        assertThat(response.getExchangeCodes()).hasSize(2);
    }

    @Test
    void create_createsFreeOrderAndExchangeCodesForGuest() {
        givenDefaultOrderDependencies(freeEvent());
        given(exchangeCodeGenerator.generate()).willReturn("A1B2-C3D4-E5F6");
        given(orderAccessTokenProvider.create(
                eq("EVT-20260803-A81C29F4307B"),
                org.mockito.ArgumentMatchers.any(OffsetDateTime.class)))
            .willReturn("guest-order-token");
        when(exchangeCodeRepository.save(any(ExchangeCode.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));

        CreateTicketOrderResponse response =
            ticketOrderService.create(1L, null, guestRequest(1));

        ArgumentCaptor<ExchangeCode> exchangeCaptor =
            ArgumentCaptor.forClass(ExchangeCode.class);

        verify(exchangeCodeRepository).save(exchangeCaptor.capture());
        assertThat(exchangeCaptor.getValue().getHolderMemberId()).isNull();
        assertThat(response.getOrderAccessToken()).isEqualTo("guest-order-token");
    }

    @Test
    void create_usesServerEventPriceForTotalAmount() {
        givenDefaultOrderDependencies(paidEvent());

        ticketOrderService.create(1L, 10L, new CreateTicketOrderRequest(3, null));

        ArgumentCaptor<PaymentOrder> paymentCaptor =
            ArgumentCaptor.forClass(PaymentOrder.class);

        verify(paymentOrderRepository).save(paymentCaptor.capture());
        assertThat(paymentCaptor.getValue().getTotalAmount())
            .isEqualByComparingTo("30000");
    }

    @Test
    void create_failsWhenEventDoesNotExist() {
        given(eventTicketReader.getTicketSnapshot(1L))
            .willThrow(new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND));

        assertThatThrownBy(() -> ticketOrderService.create(
                1L,
                10L,
                new CreateTicketOrderRequest(1, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EVENT_NOT_FOUND);
    }

    @Test
    void create_failsWhenInventoryReserveFails() {
        given(eventTicketReader.getTicketSnapshot(1L)).willReturn(paidEvent());
        given(orderNoGenerator.generate()).willReturn("EVT-20260803-A81C29F4307B");
        given(ticketInventoryGateway.reserve(1L, 2)).willReturn(false);

        assertThatThrownBy(() -> ticketOrderService.create(
                1L,
                10L,
                new CreateTicketOrderRequest(2, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.TICKET_SOLD_OUT);
    }

    @Test
    void create_failsWhenOrderNoGenerationFails() {
        given(eventTicketReader.getTicketSnapshot(1L)).willReturn(paidEvent());
        given(orderNoGenerator.generate())
            .willThrow(new BusinessException(GlobalErrorCode.ORDER_NUMBER_GENERATION_FAILED));

        assertThatThrownBy(() -> ticketOrderService.create(
                1L,
                10L,
                new CreateTicketOrderRequest(2, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_NUMBER_GENERATION_FAILED);
    }

    @Test
    void create_distinguishesExchangeCodeGenerationFailure() {
        givenDefaultOrderDependencies(freeEvent());
        given(exchangeCodeGenerator.generate())
            .willThrow(new BusinessException(GlobalErrorCode.EXCHANGE_CODE_GENERATION_FAILED));

        assertThatThrownBy(() -> ticketOrderService.create(
                1L,
                10L,
                new CreateTicketOrderRequest(1, null)))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.EXCHANGE_CODE_GENERATION_FAILED);
    }

    @Test
    void create_propagatesExchangeCodeGenerationFailure() {
        givenDefaultOrderDependencies(freeEvent());
        given(exchangeCodeGenerator.generate())
            .willThrow(new IllegalStateException("duplicate"));

        assertThatThrownBy(() -> ticketOrderService.create(
                1L,
                10L,
                new CreateTicketOrderRequest(1, null)))
            .isInstanceOf(IllegalStateException.class);
    }

    private void givenDefaultOrderDependencies(EventTicketSnapshot event) {
        given(eventTicketReader.getTicketSnapshot(1L)).willReturn(event);
        given(orderNoGenerator.generate()).willReturn("EVT-20260803-A81C29F4307B");
        given(ticketInventoryGateway.reserve(eq(1L), anyInt())).willReturn(true);
        when(paymentOrderRepository.save(any(PaymentOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
        when(ticketOrderRepository.save(any(TicketOrder.class)))
            .thenAnswer(invocation -> invocation.getArgument(0));
    }

    private CreateTicketOrderRequest guestRequest(int quantity) {
        return new CreateTicketOrderRequest(
            quantity,
            new GuestBuyerRequest("guest", "guest@example.com", "010-1234-5678")
        );
    }

    private EventTicketSnapshot paidEvent() {
        return event(BigDecimal.valueOf(10000));
    }

    private EventTicketSnapshot freeEvent() {
        return event(BigDecimal.ZERO);
    }

    private EventTicketSnapshot event(BigDecimal ticketPrice) {
        return new EventTicketSnapshot(
            1L,
            EventStatus.PUBLISHED,
            ticketPrice,
            10,
            0,
            5,
            OffsetDateTime.now().minusHours(1),
            OffsetDateTime.now().plusHours(1),
            OffsetDateTime.now().plusDays(1)
        );
    }
}
