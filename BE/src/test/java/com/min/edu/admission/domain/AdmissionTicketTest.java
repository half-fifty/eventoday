package com.min.edu.admission.domain;

import static org.assertj.core.api.Assertions.assertThat;

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
}
