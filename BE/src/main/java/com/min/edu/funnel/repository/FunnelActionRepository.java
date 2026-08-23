package com.min.edu.funnel.repository;

import java.time.OffsetDateTime;

import org.springframework.data.domain.ScrollPosition;
import org.springframework.data.domain.Window;
import org.springframework.data.elasticsearch.repository.ElasticsearchRepository;

import com.min.edu.funnel.domain.FunnelAction;

public interface FunnelActionRepository extends ElasticsearchRepository<FunnelAction, String> {

    /**
     * 배치 컷오프 판단은 receivedAt(서버 수신 시각) 기준으로만 한다 (event-contract.md 참고).
     * 하루치 전체를 List로 한 번에 올리면 대규모 행사에서 힙 사용량이 급증하고
     * Elasticsearch의 index.max_result_window(기본 10,000)에도 걸릴 수 있어, sessionId 기준
     * keyset scroll(Window)로 페이지 단위 조회한다. sessionId로 정렬해야 같은 세션의 액션이
     * 페이지 경계를 넘어가도 연속으로 도착해 스트리밍 집계가 가능하다 (actionId는 동률 정렬용 보조키).
     */
    Window<FunnelAction> findFirst500ByEventIdAndReceivedAtBetweenOrderBySessionIdAscActionIdAsc(
            Long eventId, OffsetDateTime start, OffsetDateTime end, ScrollPosition position);
}
