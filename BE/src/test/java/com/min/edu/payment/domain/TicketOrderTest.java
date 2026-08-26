package com.min.edu.payment.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class TicketOrderTest {

    private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-23T10:00:00+09:00");

    private TicketOrder create(String funnelSessionId, String funnelAnonymousId) {
        return TicketOrder.create(
                1L, 2L, BigDecimal.valueOf(10000), 1,
                TicketOrderStatus.PENDING_PAYMENT, null, funnelSessionId, funnelAnonymousId, NOW);
    }

    @Test
    void create_normalLengthFunnelValues_keepsThem() {
        TicketOrder ticketOrder = create("session-1", "anon-1");

        assertThat(ticketOrder.getFunnelSessionId()).isEqualTo("session-1");
        assertThat(ticketOrder.getFunnelAnonymousId()).isEqualTo("anon-1");
    }

    @Test
    void create_funnelSessionIdOver80Chars_dropsItInsteadOfFailingOrder() {
        String tooLong = "s".repeat(81);

        TicketOrder ticketOrder = create(tooLong, "anon-1");

        // 분석용 필드라 형식이 안 맞아도 주문 생성 자체를 막으면 안 된다 — DB 컬럼(80자) 초과 시
        // INSERT가 실패해 전체 주문이 롤백되는 대신, 조용히 버린다.
        assertThat(ticketOrder.getFunnelSessionId()).isNull();
        assertThat(ticketOrder.getFunnelAnonymousId()).isEqualTo("anon-1");
    }

    @Test
    void create_funnelSessionIdExactly80Chars_keepsIt() {
        String exact = "s".repeat(80);

        TicketOrder ticketOrder = create(exact, "anon-1");

        assertThat(ticketOrder.getFunnelSessionId()).isEqualTo(exact);
    }

    @Test
    void create_funnelAnonymousIdOver100Chars_dropsIt() {
        String tooLong = "a".repeat(101);

        TicketOrder ticketOrder = create("session-1", tooLong);

        assertThat(ticketOrder.getFunnelAnonymousId()).isNull();
        assertThat(ticketOrder.getFunnelSessionId()).isEqualTo("session-1");
    }
}
