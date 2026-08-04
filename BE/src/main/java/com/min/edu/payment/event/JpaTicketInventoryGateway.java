package com.min.edu.payment.event;

import org.springframework.stereotype.Component;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class JpaTicketInventoryGateway implements TicketInventoryGateway {

    private final EntityManager entityManager;

    @Override
    public boolean reserve(Long eventId, int quantity) {
        int updatedRows = entityManager.createQuery("""
                update Event e
                   set e.ticketSoldQuantity = e.ticketSoldQuantity + :quantity
                 where e.id = :eventId
                   and e.ticketSoldQuantity + :quantity <= e.ticketTotalQuantity
                """)
            .setParameter("eventId", eventId)
            .setParameter("quantity", quantity)
            .executeUpdate();

        entityManager.clear();

        return updatedRows == 1;
    }

    @Override
    public boolean release(Long eventId, int quantity) {
        int updatedRows = entityManager.createQuery("""
                update Event e
                   set e.ticketSoldQuantity = e.ticketSoldQuantity - :quantity
                 where e.id = :eventId
                   and e.ticketSoldQuantity - :quantity >= 0
                """)
            .setParameter("eventId", eventId)
            .setParameter("quantity", quantity)
            .executeUpdate();

        return updatedRows == 1;
    }
}
