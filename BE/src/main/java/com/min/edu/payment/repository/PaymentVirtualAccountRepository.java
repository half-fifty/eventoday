package com.min.edu.payment.repository;

import com.min.edu.payment.domain.PaymentVirtualAccount;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.Collection;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PaymentVirtualAccountRepository
        extends JpaRepository<PaymentVirtualAccount, Long> {

    Optional<PaymentVirtualAccount> findByPaymentId(Long paymentId);

    List<PaymentVirtualAccount> findAllByPaymentIdIn(Collection<Long> paymentIds);

    @Query("""
        SELECT va
        FROM PaymentVirtualAccount va
        JOIN Payment p ON p.id = va.paymentId
        WHERE p.status = 'WAITING_FOR_DEPOSIT'
            AND va.dueAt <= :now
        ORDER BY va.dueAt ASC, va.id ASC
        """)
    List<PaymentVirtualAccount> findDueWaitingAccounts(
        @Param("now") OffsetDateTime now,
        Pageable pageable
    );
}
