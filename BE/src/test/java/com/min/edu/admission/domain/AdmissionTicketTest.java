package com.min.edu.admission.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class AdmissionTicketTest {

    @Test
    void issue_createsIssuedAdmissionTicketWithQrToken() {
        OffsetDateTime now = OffsetDateTime.now();

        AdmissionTicket admissionTicket = AdmissionTicket.issue(
            7L,
            10L,
            "qr-token",
            now
        );

        assertThat(admissionTicket.getExchangeCodeId()).isEqualTo(7L);
        assertThat(admissionTicket.getMemberId()).isEqualTo(10L);
        assertThat(admissionTicket.getQrToken()).isEqualTo("qr-token");
        assertThat(admissionTicket.getStatus()).isEqualTo(AdmissionTicketStatus.ISSUED);
        assertThat(admissionTicket.getIssuedAt()).isEqualTo(now);
        assertThat(admissionTicket.getUsedAt()).isNull();
        assertThat(admissionTicket.getCancelledAt()).isNull();
    }

    @Test
    void checkIn_changesIssuedTicketToUsed() {
        OffsetDateTime issuedAt = OffsetDateTime.now();
        OffsetDateTime usedAt = issuedAt.plusMinutes(1);
        AdmissionTicket admissionTicket = AdmissionTicket.issue(7L, 10L, "qr-token", issuedAt);

        admissionTicket.checkIn(usedAt);

        assertThat(admissionTicket.getStatus()).isEqualTo(AdmissionTicketStatus.USED);
        assertThat(admissionTicket.getUsedAt()).isEqualTo(usedAt);
        assertThat(admissionTicket.getCancelledAt()).isNull();
    }

    @Test
    void cancelCheckIn_restoresUsedTicketToIssuedWithoutCancelledAt() {
        OffsetDateTime issuedAt = OffsetDateTime.now();
        AdmissionTicket admissionTicket = AdmissionTicket.issue(7L, 10L, "qr-token", issuedAt);
        admissionTicket.checkIn(issuedAt.plusMinutes(1));

        admissionTicket.cancelCheckIn();

        assertThat(admissionTicket.getStatus()).isEqualTo(AdmissionTicketStatus.ISSUED);
        assertThat(admissionTicket.getUsedAt()).isNull();
        assertThat(admissionTicket.getCancelledAt()).isNull();
    }

    @Test
    void checkInAndCancelCheckIn_failForInvalidStates() {
        AdmissionTicket admissionTicket =
            AdmissionTicket.issue(7L, 10L, "qr-token", OffsetDateTime.now());

        assertThatThrownBy(admissionTicket::cancelCheckIn)
            .isInstanceOf(IllegalStateException.class);

        admissionTicket.checkIn(OffsetDateTime.now());

        assertThatThrownBy(() -> admissionTicket.checkIn(OffsetDateTime.now()))
            .isInstanceOf(IllegalStateException.class);
    }
}
