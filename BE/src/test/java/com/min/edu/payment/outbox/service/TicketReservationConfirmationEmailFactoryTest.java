package com.min.edu.payment.outbox.service;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

import com.min.edu.common.mail.EmailMessage;
import com.min.edu.payment.outbox.dto.TicketReservationConfirmationEmailPayload;

class TicketReservationConfirmationEmailFactoryTest {

    @Test
    void create_keepsReservationConfirmationContentAndEscapesHtml() {
        TicketReservationConfirmationEmailFactory factory =
            new TicketReservationConfirmationEmailFactory();

        EmailMessage message = factory.create(new TicketReservationConfirmationEmailPayload(
            "ORDER-1<script>",
            "guest@example.com",
            "event<Name>"
        ));

        assertThat(message.to()).isEqualTo("guest@example.com");
        assertThat(message.subject()).contains("Eventoday");
        assertThat(message.content()).contains("event&lt;Name&gt;", "ORDER-1&lt;script&gt;");
        assertThat(message.content()).doesNotContain("event<Name>", "ORDER-1<script>");
    }
}
