package com.min.edu.funnel.scheduler;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.funnel.service.FunnelSessionReconstructionService;

@ExtendWith(MockitoExtension.class)
class FunnelSessionBatchSchedulerTest {

    @Mock
    private EventRepository eventRepository;

    @Mock
    private FunnelSessionReconstructionService funnelSessionReconstructionService;

    @InjectMocks
    private FunnelSessionBatchScheduler scheduler;

    @Test
    void runDailyReconstruction_processesEachTargetEvent_withYesterdayAsTargetDate() {
        given(eventRepository.findIdsForFunnelReconstruction(
                eq(EventStatus.PUBLISHED), eq(EventStatus.ENDED), any(OffsetDateTime.class)))
            .willReturn(List.of(1L, 2L));

        scheduler.runDailyReconstruction();

        LocalDate expectedDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        verify(funnelSessionReconstructionService).reconstruct(1L, expectedDate);
        verify(funnelSessionReconstructionService).reconstruct(2L, expectedDate);
    }

    @Test
    void runDailyReconstruction_queriesWithTargetDateStartOfDayInKst() {
        given(eventRepository.findIdsForFunnelReconstruction(
                eq(EventStatus.PUBLISHED), eq(EventStatus.ENDED), any(OffsetDateTime.class)))
            .willReturn(List.of());

        scheduler.runDailyReconstruction();

        LocalDate expectedDate = LocalDate.now(ZoneId.of("Asia/Seoul")).minusDays(1);
        OffsetDateTime expectedDayStart = expectedDate.atStartOfDay(ZoneId.of("Asia/Seoul")).toOffsetDateTime();
        verify(eventRepository).findIdsForFunnelReconstruction(
                EventStatus.PUBLISHED, EventStatus.ENDED, expectedDayStart);
    }

    @Test
    void runDailyReconstruction_oneEventFails_stillProcessesRemainingEvents() {
        given(eventRepository.findIdsForFunnelReconstruction(
                eq(EventStatus.PUBLISHED), eq(EventStatus.ENDED), any(OffsetDateTime.class)))
            .willReturn(List.of(1L, 2L));
        doThrow(new RuntimeException("배치 실패")).when(funnelSessionReconstructionService)
                .reconstruct(eq(1L), any());

        scheduler.runDailyReconstruction();

        verify(funnelSessionReconstructionService).reconstruct(eq(2L), any());
    }

    @Test
    void runDailyReconstruction_noTargetEvents_doesNothing() {
        given(eventRepository.findIdsForFunnelReconstruction(
                eq(EventStatus.PUBLISHED), eq(EventStatus.ENDED), any(OffsetDateTime.class)))
            .willReturn(List.of());

        scheduler.runDailyReconstruction();

        verify(funnelSessionReconstructionService, never()).reconstruct(any(), any());
    }
}
