package com.min.edu.booth.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.dto.CreateBoothReservationSlotRequest;
import com.min.edu.booth.repository.BoothOrganizationMemberRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReservationRepository;
import com.min.edu.booth.repository.BoothReservationSlotRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import com.min.edu.organization.domain.OrganizationRole;
import java.time.OffsetDateTime;
import org.junit.jupiter.api.Test;

class BoothReservationSlotServiceTest {

    private final BoothReservationSlotRepository slotRepository = mock(BoothReservationSlotRepository.class);
    private final BoothReservationRepository reservationRepository = mock(BoothReservationRepository.class);
    private final BoothRepository boothRepository = mock(BoothRepository.class);
    private final BoothOrganizationMemberRepository organizationMemberRepository = mock(BoothOrganizationMemberRepository.class);

    private final BoothReservationSlotService service = new BoothReservationSlotService(
            slotRepository,
            reservationRepository,
            boothRepository,
            organizationMemberRepository
    );

    @Test
    void createReservationSlot_withInvalidTimeRange_throwsBusinessException() {
        AuthenticatedMemberDto actor = new AuthenticatedMemberDto(1L, PlatformRole.USER);
        Booth booth = Booth.builder().id(10L).assignedOrganizationId(100L).build();

        when(boothRepository.findById(10L)).thenReturn(java.util.Optional.of(booth));
        when(organizationMemberRepository.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                any(), any(), any(), any())).thenReturn(true);
        when(slotRepository.existsByBoothIdAndStartAt(any(), any())).thenReturn(false);

        CreateBoothReservationSlotRequest request = CreateBoothReservationSlotRequest.builder()
                .startAt(OffsetDateTime.parse("2026-08-11T12:00:00+09:00"))
                .endAt(OffsetDateTime.parse("2026-08-11T11:00:00+09:00"))
                .capacity(10)
                .build();

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.createReservationSlot(10L, request, actor));

        assert exception.getErrorCode() == GlobalErrorCode.INVALID_INPUT_VALUE;
    }
}
