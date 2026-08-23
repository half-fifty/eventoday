package com.min.edu.payment.outbox.service;

import org.springframework.stereotype.Component;

import com.min.edu.common.mail.EmailMessage;
import com.min.edu.payment.outbox.dto.TicketReservationConfirmationEmailPayload;

@Component
public class TicketReservationConfirmationEmailFactory {

    static final String SUBJECT = "[Eventoday] 티켓 예매가 완료되었습니다";

    public EmailMessage create(TicketReservationConfirmationEmailPayload payload) {
        return new EmailMessage(
            payload.buyerEmail(),
            SUBJECT,
            buildContent(payload)
        );
    }

    private String buildContent(TicketReservationConfirmationEmailPayload payload) {
        return """
            <div style="font-family: Arial, sans-serif; color: #222; line-height: 1.6;">
              <h2>티켓 예매가 완료되었습니다</h2>
              <p>행사명: %s</p>
              <p>주문번호: <strong>%s</strong></p>
              <p>비회원 예매 조회 시 주문번호 + 구매 이메일 + 구매 전화번호가 필요합니다.</p>
              <p>Eventoday에서 비회원 예매 조회를 이용해주세요.</p>
            </div>
            """.formatted(
                escapeHtml(payload.eventName()),
                escapeHtml(payload.orderNo())
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
