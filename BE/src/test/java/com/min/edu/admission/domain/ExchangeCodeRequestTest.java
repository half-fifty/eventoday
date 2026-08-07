package com.min.edu.admission.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class ExchangeCodeRequestTest {

    @Test
    void issue_changesApprovedRequestToIssued() {
        ExchangeCodeRequest request = ExchangeCodeRequest.create(
            1L, 10L, 3, "purpose", OffsetDateTime.now());
        request.approve(99L, OffsetDateTime.now());

        request.issue();

        assertThat(request.getStatus()).isEqualTo(ExchangeCodeRequestStatus.ISSUED);
        assertThat(request.getReviewedBy()).isEqualTo(99L);
        assertThat(request.getReviewedAt()).isNotNull();
        assertThat(request.getEmailedAt()).isNull();
    }

    @Test
    void issue_failsWhenRequestIsNotApproved() {
        ExchangeCodeRequest request = ExchangeCodeRequest.create(
            1L, 10L, 3, "purpose", OffsetDateTime.now());

        assertThatThrownBy(request::issue)
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void markEmailed_recordsEmailedAtForIssuedRequest() {
        ExchangeCodeRequest request = ExchangeCodeRequest.create(
            1L, 10L, 3, "purpose", OffsetDateTime.now());
        request.approve(99L, OffsetDateTime.now());
        request.issue();
        OffsetDateTime emailedAt = OffsetDateTime.now();

        request.markEmailed(emailedAt);

        assertThat(request.getEmailedAt()).isEqualTo(emailedAt);
    }

    @Test
    void markEmailed_failsWhenRequestIsNotIssued() {
        ExchangeCodeRequest request = ExchangeCodeRequest.create(
            1L, 10L, 3, "purpose", OffsetDateTime.now());

        assertThatThrownBy(() -> request.markEmailed(OffsetDateTime.now()))
            .isInstanceOf(IllegalStateException.class);
    }
}
