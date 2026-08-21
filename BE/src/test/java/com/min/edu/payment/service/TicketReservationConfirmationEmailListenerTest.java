package com.min.edu.payment.service;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.willThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import com.min.edu.common.mail.EmailMessage;
import com.min.edu.common.mail.EmailSender;
import com.min.edu.payment.event.TicketReservationCompletedEvent;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class TicketReservationConfirmationEmailListenerTest {

    @Mock
    private EmailSender emailSender;

    @Test
    void sendConfirmationEmail_sendsReservationInformation() {
        TicketReservationConfirmationEmailListener listener =
            new TicketReservationConfirmationEmailListener(emailSender);

        listener.sendConfirmationEmail(new TicketReservationCompletedEvent(
            "EVT-20260811-ABCDEF",
            "guest@example.com",
            "test-event"
        ));

        ArgumentCaptor<EmailMessage> captor = ArgumentCaptor.forClass(EmailMessage.class);
        verify(emailSender).send(captor.capture());
        EmailMessage message = captor.getValue();
        org.assertj.core.api.Assertions.assertThat(message.to()).isEqualTo("guest@example.com");
        org.assertj.core.api.Assertions.assertThat(message.subject())
            .isEqualTo("[Eventoday] ticket purchase completed");
        org.assertj.core.api.Assertions.assertThat(message.content())
            .contains("test-event")
            .contains("EVT-20260811-ABCDEF")
            .doesNotContain("orderAccessToken")
            .doesNotContain("qrToken");
    }

    @Test
    void sendConfirmationEmail_swallowsEmailSenderException() {
        TicketReservationConfirmationEmailListener listener =
            new TicketReservationConfirmationEmailListener(emailSender);
        willThrow(new IllegalStateException("smtp failed"))
            .given(emailSender)
            .send(any(EmailMessage.class));

        assertThatCode(() -> listener.sendConfirmationEmail(new TicketReservationCompletedEvent(
            "EVT-20260811-ABCDEF",
            "guest@example.com",
            "test-event"
        ))).doesNotThrowAnyException();
    }

    @Test
    void sendConfirmationEmail_ignoresBlankEmail() {
        TicketReservationConfirmationEmailListener listener =
            new TicketReservationConfirmationEmailListener(emailSender);

        listener.sendConfirmationEmail(new TicketReservationCompletedEvent(
            "EVT-20260811-ABCDEF",
            " ",
            "test-event"
        ));

        verify(emailSender, never()).send(any());
    }
}
