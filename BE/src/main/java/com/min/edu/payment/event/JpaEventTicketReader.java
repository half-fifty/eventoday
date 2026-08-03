package com.min.edu.payment.event;

import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JpaEventTicketReader implements EventTicketReader {

    private final EntityManager entityManager;

    @Override
    @Transactional(readOnly = true)
    public EventTicketSnapshot getTicketSnapshot(Long eventId) {
        Event event = entityManager.find(Event.class, eventId);

        if (event == null) {
            throw new BusinessException(GlobalErrorCode.EVENT_NOT_FOUND);
        }

        return new EventTicketSnapshot(
            event.getId(),
            event.getStatus(),
            event.getTicketPrice(),
            event.getTicketTotalQuantity(),
            event.getTicketSoldQuantity(),
            event.getTicketPurchaseLimit(),
            event.getTicketSalesStartAt(),
            event.getTicketSalesEndAt(),
            event.getEndAt()
        );
    }
}
