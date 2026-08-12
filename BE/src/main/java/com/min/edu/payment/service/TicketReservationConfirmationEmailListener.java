package com.min.edu.payment.service;

import com.min.edu.common.mail.EmailMessage;
import com.min.edu.common.mail.EmailSender;
import com.min.edu.payment.event.TicketReservationCompletedEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Slf4j
@Component
@RequiredArgsConstructor
public class TicketReservationConfirmationEmailListener {

    private static final String SUBJECT = "[Eventoday] 티켓 예매가 완료되었습니다.";

    private final EmailSender emailSender;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void sendConfirmationEmail(TicketReservationCompletedEvent event) {
        if (event.buyerEmail() == null || event.buyerEmail().isBlank()) {
            return;
        }

        try {
            emailSender.send(new EmailMessage(
                event.buyerEmail(),
                SUBJECT,
                buildContent(event)
            ));
        } catch (RuntimeException exception) {
            log.error(
                "Ticket reservation confirmation email failed - orderNo: {}",
                event.orderNo(),
                exception
            );
        }
    }

    private String buildContent(TicketReservationCompletedEvent event) {
        return """
            <div style="font-family: Arial, sans-serif; color: #222; line-height: 1.6;">
              <h2>티켓 예매가 완료되었습니다.</h2>
              <p>행사명: %s</p>
              <p>주문번호: <strong>%s</strong></p>
              <p>비회원 예매 조회 시 주문번호 + 구매 이메일 + 구매 전화번호가 필요합니다.</p>
              <p>Eventoday에서 비회원 예매 조회를 이용해주세요.</p>
            </div>
            """.formatted(
                escapeHtml(event.eventName()),
                escapeHtml(event.orderNo())
            );
    }

    private String escapeHtml(String value) {
        if (value == null) {
            return "";
        }
        return value
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&#39;");
    }
}
