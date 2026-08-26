package com.min.edu.organization.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;

class OrganizationTest {

    private Organization exhibitor() {
        return Organization.createBusinessOrganization(
            OrganizationType.EXHIBITOR, "부스참가사", "1234567890", "홍길동",
            "contact@example.com", "0212345678",
            null, null, null, null, null, null,
            OffsetDateTime.now()
        );
    }

    private Organization organizer() {
        return Organization.createBusinessOrganization(
            OrganizationType.ORGANIZER, "행사개최사", "0987654321", "김철수",
            "contact@example.com", "0212345678",
            null, null, null, null, null, null,
            OffsetDateTime.now()
        );
    }

    @Test
    void createBusinessOrganization_exhibitorStartsActive() {
        assertThat(exhibitor().getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    void createBusinessOrganization_organizerStartsPending() {
        assertThat(organizer().getStatus()).isEqualTo(OrganizationStatus.PENDING);
    }

    @Test
    void approve_fromPending_movesToActive() {
        Organization organization = organizer();

        organization.approve(OffsetDateTime.now());

        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.ACTIVE);
    }

    @Test
    void approve_fromActive_throwsIllegalState() {
        Organization organization = exhibitor();

        assertThatThrownBy(() -> organization.approve(OffsetDateTime.now()))
            .isInstanceOf(IllegalStateException.class);
    }

    @Test
    void suspend_fromActive_movesToSuspended() {
        Organization organization = exhibitor();

        organization.suspend(OffsetDateTime.now());

        assertThat(organization.getStatus()).isEqualTo(OrganizationStatus.SUSPENDED);
    }

    @Test
    void suspend_fromPending_throwsIllegalState() {
        Organization organization = organizer();

        assertThatThrownBy(() -> organization.suspend(OffsetDateTime.now()))
            .isInstanceOf(IllegalStateException.class);
    }
}
