package com.min.edu.funnel.scheduler;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.funnel.service.FunnelSessionReconstructionService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 매일 새벽 3시(KST)에 전날 하루치 퍼널 이벤트를 공개된(PUBLISHED) 행사별로 재구성한다.
 * 자정 직후 이벤트가 Kafka→ES까지 색인되는 지연, 결제 확정 지연 등을 감안해 3시간 버퍼를 둔다
 * (technical-design.md 참고).
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FunnelSessionBatchScheduler {

    private static final ZoneId KST = ZoneId.of("Asia/Seoul");

    private final EventRepository eventRepository;
    private final FunnelSessionReconstructionService funnelSessionReconstructionService;

    @Scheduled(cron = "${funnel.session-batch.cron:0 0 3 * * *}", zone = "Asia/Seoul")
    public void runDailyReconstruction() {
        LocalDate targetDate = LocalDate.now(KST).minusDays(1);
        List<Long> eventIds = eventRepository.findIdsByStatus(EventStatus.PUBLISHED);

        for (Long eventId : eventIds) {
            reconstructOne(eventId, targetDate);
        }
    }

    // 한 행사 처리가 실패해도 나머지 행사 배치는 계속 진행되도록 여기서 예외를 흡수한다.
    private void reconstructOne(Long eventId, LocalDate targetDate) {
        try {
            funnelSessionReconstructionService.reconstruct(eventId, targetDate);
        } catch (Exception e) {
            log.warn("퍼널 세션 재구성 실패: eventId={}, targetDate={}", eventId, targetDate, e);
        }
    }
}
