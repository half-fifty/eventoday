package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.service.EventOperationAccessService;
import com.min.edu.payment.repository.TicketOrderDetailProjection;
import com.min.edu.payment.repository.TicketOrderRepository;
import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

class TicketOrderOperationQueryServiceTest {

    private final TicketOrderRepository ticketOrderRepository = Mockito.mock(TicketOrderRepository.class);
    private final EventOperationAccessService eventOperationAccessService =
        Mockito.mock(EventOperationAccessService.class);
    private final TicketOrderOperationQueryService service =
        new TicketOrderOperationQueryService(ticketOrderRepository, eventOperationAccessService);

    @Test
    void verifiesOperationalAccessAndReturnsOrderInCurrentEvent() {
        TicketOrderDetailProjection projection = projection(100L);
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.of(projection));

        TicketOrderOperationQueryService.TicketOrderOperationView result =
            service.getTicketOrderStatus(100L, 10L, " ORDER-1 ");

        verify(eventOperationAccessService).requireOperationalAccess(100L, 10L);
        verify(ticketOrderRepository).findTicketOrderDetailByOrderNo("ORDER-1");
        assertThat(result.orderNo()).isEqualTo("ORDER-1");
        assertThat(result.eventName()).isEqualTo("Eventoday Conference");
        assertThat(result.totalAmount()).isEqualByComparingTo("20000");
    }

    @Test
    void rejectsOrderFromAnotherEvent() {
        TicketOrderDetailProjection projection = projection(200L);
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.of(projection));

        assertThatThrownBy(() -> service.getTicketOrderStatus(100L, 10L, "ORDER-1"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.ORDER_ACCESS_DENIED);
    }

    @Test
    void rejectsMissingOrder() {
        given(ticketOrderRepository.findTicketOrderDetailByOrderNo("ORDER-1"))
            .willReturn(Optional.empty());

        assertThatThrownBy(() -> service.getTicketOrderStatus(100L, 10L, "ORDER-1"))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.TICKET_ORDER_NOT_FOUND);
    }

    private TicketOrderDetailProjection projection(Long eventId) {
        TicketOrderDetailProjection projection = Mockito.mock(TicketOrderDetailProjection.class);
        given(projection.getOrderNo()).willReturn("ORDER-1");
        given(projection.getEventId()).willReturn(eventId);
        given(projection.getEventName()).willReturn("Eventoday Conference");
        given(projection.getQuantity()).willReturn(2);
        given(projection.getTotalAmount()).willReturn(BigDecimal.valueOf(20000));
        given(projection.getPaymentOrderStatus()).willReturn("PAID");
        given(projection.getTicketOrderStatus()).willReturn("CONFIRMED");
        given(projection.getExpiresAt()).willReturn(OffsetDateTime.parse("2026-08-03T10:10:00+09:00"));
        given(projection.getConfirmedAt()).willReturn(OffsetDateTime.parse("2026-08-03T10:00:00+09:00"));
        return projection;
    }
}
