package com.min.edu.funnel.repository;

import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.min.edu.funnel.domain.FunnelAction;

public interface FunnelActionRepository extends ElasticsearchRepository<FunnelAction, String> {

    /**
     * 배치 컷오프 판단은 receivedAt(서버 수신 시각) 기준으로만 한다 (event-contract.md 참고).
     */
    List<FunnelAction> findByEventIdAndReceivedAtBetween(Long eventId, OffsetDateTime start, OffsetDateTime end);
}
