package com.min.edu.admission.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

    @Test
    void redeem_changesIssuedCodeToRedeemedAndRecordsRedeemedAt() {
        OffsetDateTime now = OffsetDateTime.now();
        ExchangeCode exchangeCode = ExchangeCode.createForTicketOrder(
            1L,
            3L,
            10L,
            "ABCDEF-123456-7890AB",
            null,
            now.minusMinutes(1)
        );

        exchangeCode.redeem(now);

        assertThat(exchangeCode.getStatus()).isEqualTo(ExchangeCodeStatus.REDEEMED);
        assertThat(exchangeCode.getRedeemedAt()).isEqualTo(now);
        assertThat(exchangeCode.getUpdatedAt()).isEqualTo(now);
        assertThat(exchangeCode.getEventId()).isEqualTo(1L);
        assertThat(exchangeCode.getTicketOrderId()).isEqualTo(3L);
        assertThat(exchangeCode.getExchangeCodeRequestId()).isNull();
        assertThat(exchangeCode.getExpiresAt()).isNull();
    }

    @Test
    void redeem_failsWhenCodeIsNotIssued() {
        OffsetDateTime now = OffsetDateTime.now();
        ExchangeCode exchangeCode = ExchangeCode.builder()
            .eventId(1L)
            .ticketOrderId(3L)
            .holderMemberId(10L)
            .code("ABCDEF-123456-7890AB")
            .status(ExchangeCodeStatus.REDEEMED)
            .createdAt(now)
            .updatedAt(now)
            .build();

        assertThatThrownBy(() -> exchangeCode.redeem(now.plusMinutes(1)))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void assignHolder_setsHolderWithoutChangingSourceFields() {
        OffsetDateTime now = OffsetDateTime.now();
        ExchangeCode exchangeCode = ExchangeCode.createForExchangeCodeRequest(
            1L,
            7L,
            "ABCDEF-123456-7890AB",
            now.plusDays(1),
            now.minusMinutes(1)
        );

        exchangeCode.assignHolder(10L, now);

        assertThat(exchangeCode.getHolderMemberId()).isEqualTo(10L);
        assertThat(exchangeCode.getExchangeCodeRequestId()).isEqualTo(7L);
        assertThat(exchangeCode.getTicketOrderId()).isNull();
        assertThat(exchangeCode.getUpdatedAt()).isEqualTo(now);
    }
}
