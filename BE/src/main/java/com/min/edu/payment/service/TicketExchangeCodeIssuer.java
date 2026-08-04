package com.min.edu.payment.service;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;

import org.springframework.stereotype.Component;

import com.min.edu.admission.domain.ExchangeCode;
import com.min.edu.admission.repository.ExchangeCodeRepository;
import com.min.edu.admission.support.ExchangeCodeGenerator;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.payment.domain.TicketOrder;

import lombok.RequiredArgsConstructor;

@Component
@RequiredArgsConstructor
public class TicketExchangeCodeIssuer {

    private final ExchangeCodeRepository exchangeCodeRepository;
    private final ExchangeCodeGenerator exchangeCodeGenerator;

    public List<ExchangeCode> issueIfAbsent(
            TicketOrder ticketOrder,
            Long holderMemberId,
            OffsetDateTime now) {
        List<ExchangeCode> existingCodes =
            exchangeCodeRepository.findAllByTicketOrderIdOrderByIdAsc(ticketOrder.getId());

        if (!existingCodes.isEmpty()) {
            if (existingCodes.size() != ticketOrder.getTotalQuantity()) {
                throw new BusinessException(GlobalErrorCode.PAYMENT_DATA_INCONSISTENT);
            }

            return existingCodes;
        }

        List<ExchangeCode> issuedCodes = new ArrayList<>();

        for (int i = 0; i < ticketOrder.getTotalQuantity(); i++) {
            ExchangeCode exchangeCode = ExchangeCode.createForTicketOrder(
                ticketOrder.getEventId(),
                ticketOrder.getId(),
                holderMemberId,
                exchangeCodeGenerator.generate(),
                null,
                now
            );
            issuedCodes.add(exchangeCodeRepository.save(exchangeCode));
        }

        return issuedCodes;
    }
}
