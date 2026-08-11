package com.min.edu.booth.service;

import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothReservation;
import com.min.edu.booth.domain.BoothReservationSlot;
import com.min.edu.booth.domain.BoothReservationSlotStatus;
import com.min.edu.booth.domain.BoothReservationStatus;
import com.min.edu.booth.dto.BoothReservationResponse;
import com.min.edu.booth.dto.CreateBoothReservationRequest;
import com.min.edu.booth.event.BoothVacancyEvent;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReservationRepository;
import com.min.edu.booth.repository.BoothReservationSlotRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.member.repository.MemberRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoothReservationWithRedisServiceTest {

    @Mock
    private BoothReservationRepository reservationRepository;

    @Mock
    private BoothReservationSlotRepository slotRepository;

    @Mock
    private BoothRepository boothRepository;

    @Mock
    private BoothManagerPermissionChecker boothManagerPermissionChecker;

    @Mock
    private MemberRepository memberRepository;

    @Mock
    private RedisReservationService redisReservationService;

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private BoothReservationWithRedisService service;

    @Test
    void createReservationWithRedis_allowsReservationWhenNoPriorReservationExists() {
        Long boothId = 1L;
        Long memberId = 10L;
        Long slotId = 2L;
        CreateBoothReservationRequest request = CreateBoothReservationRequest.builder()
                .slotId(slotId)
                .partySize(2)
                .build();

        Booth booth = Booth.builder()
                .id(boothId)
                .eventId(100L)
                .boothCode("B1")
                .boothType("FOOD")
                .locationDescription("A1")
                .price(BigDecimal.ZERO)
                .status(com.min.edu.booth.domain.BoothStatus.AVAILABLE)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        BoothReservationSlot slot = BoothReservationSlot.builder()
                .id(slotId)
                .boothId(boothId)
                .startAt(OffsetDateTime.now())
                .endAt(OffsetDateTime.now().plusHours(1))
                .capacity(10)
                .reservedCount(0)
                .status(BoothReservationSlotStatus.OPEN)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(boothRepository.findById(boothId)).thenReturn(Optional.of(booth));
        when(slotRepository.findByIdWithLock(slotId)).thenReturn(Optional.of(slot));
        when(reservationRepository.existsByMemberIdAndBoothId(memberId, boothId))
                .thenReturn(false);
        when(redisReservationService.reserveSlot(boothId, slotId, memberId)).thenReturn(true);
        when(reservationRepository.saveAndFlush(any(BoothReservation.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(slotRepository.saveAndFlush(any(BoothReservationSlot.class))).thenAnswer(invocation -> invocation.getArgument(0));

        boolean wasActive = TransactionSynchronizationManager.isSynchronizationActive();
        if (wasActive) {
            TransactionSynchronizationManager.clear();
        }

        BoothReservationResponse response = service.createReservationWithRedis(boothId, request, memberId);

        assertThat(response).isNotNull();
        assertThat(response.getStatus()).isEqualTo(BoothReservationStatus.RESERVED);
        verify(reservationRepository).existsByMemberIdAndBoothId(memberId, boothId);
    }

    @Test
    void createReservationWithRedis_blocksReservationWhenAnyPriorReservationExists() {
        Long boothId = 1L;
        Long memberId = 10L;
        Long slotId = 2L;
        CreateBoothReservationRequest request = CreateBoothReservationRequest.builder()
                .slotId(slotId)
                .partySize(2)
                .build();

        Booth booth = Booth.builder()
                .id(boothId)
                .eventId(100L)
                .boothCode("B1")
                .boothType("FOOD")
                .locationDescription("A1")
                .price(BigDecimal.ZERO)
                .status(com.min.edu.booth.domain.BoothStatus.AVAILABLE)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        BoothReservationSlot slot = BoothReservationSlot.builder()
                .id(slotId)
                .boothId(boothId)
                .startAt(OffsetDateTime.now())
                .endAt(OffsetDateTime.now().plusHours(1))
                .capacity(10)
                .reservedCount(0)
                .status(BoothReservationSlotStatus.OPEN)
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build();

        when(boothRepository.findById(boothId)).thenReturn(Optional.of(booth));
        when(slotRepository.findByIdWithLock(slotId)).thenReturn(Optional.of(slot));
        // 취소된 예약이라도 이미 존재하면(상태 무관) 같은 부스 재예약은 막혀야 한다.
        when(reservationRepository.existsByMemberIdAndBoothId(memberId, boothId))
                .thenReturn(true);

        assertThatThrownBy(() -> service.createReservationWithRedis(boothId, request, memberId))
                .isInstanceOf(BusinessException.class);
    }
}
