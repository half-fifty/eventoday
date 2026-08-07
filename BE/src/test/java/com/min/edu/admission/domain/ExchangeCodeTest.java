package com.min.edu.admission.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ExchangeCodeTest {

    @Test
    void createForExchangeCodeRequest_createsExternalSalesCode() {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = now.plusDays(1);

        ExchangeCode exchangeCode = ExchangeCode.createForExchangeCodeRequest(
            1L,
            7L,
            "ABCDEF-123456-7890AB",
            expiresAt,
            now
        );

        assertThat(exchangeCode.getEventId()).isEqualTo(1L);
        assertThat(exchangeCode.getExchangeCodeRequestId()).isEqualTo(7L);
        assertThat(exchangeCode.getTicketOrderId()).isNull();
        assertThat(exchangeCode.getHolderMemberId()).isNull();
        assertThat(exchangeCode.getStatus()).isEqualTo(ExchangeCodeStatus.ISSUED);
        assertThat(exchangeCode.getExpiresAt()).isEqualTo(expiresAt);
        assertThat(exchangeCode.getRedeemedAt()).isNull();
        assertThat(exchangeCode.getCreatedAt()).isEqualTo(now);
        assertThat(exchangeCode.getUpdatedAt()).isEqualTo(now);
    }

    @Test
    void createForTicketOrder_keepsTicketOrderCodeShape() {
        OffsetDateTime now = OffsetDateTime.now();

        ExchangeCode exchangeCode = ExchangeCode.createForTicketOrder(
            1L,
            3L,
            10L,
            "ABCDEF-123456-7890AB",
            null,
            now
        );

        assertThat(exchangeCode.getEventId()).isEqualTo(1L);
        assertThat(exchangeCode.getExchangeCodeRequestId()).isNull();
        assertThat(exchangeCode.getTicketOrderId()).isEqualTo(3L);
        assertThat(exchangeCode.getHolderMemberId()).isEqualTo(10L);
        assertThat(exchangeCode.getStatus()).isEqualTo(ExchangeCodeStatus.ISSUED);
    }
}
