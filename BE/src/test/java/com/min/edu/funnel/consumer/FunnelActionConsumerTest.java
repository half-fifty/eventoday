package com.min.edu.funnel.consumer;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.elasticsearch.test.autoconfigure.DataElasticsearchTest;
import org.springframework.context.annotation.Import;

import com.min.edu.TestcontainersConfiguration;
import com.min.edu.funnel.domain.FunnelAction;
import com.min.edu.funnel.dto.FunnelActionEventDto;
import com.min.edu.funnel.repository.FunnelActionRepository;

/**
 * OpenAiChatClient 등 다른 도메인의 빈까지 필요한 전체 SpringBootTest 대신,
 * Elasticsearch 관련 빈만 로드하는 슬라이스 테스트로 검증 범위를 좁힌다.
 */
@DataElasticsearchTest
@Import({TestcontainersConfiguration.class, FunnelActionConsumer.class})
class FunnelActionConsumerTest {

    @Autowired
    private FunnelActionConsumer funnelActionConsumer;

    @Autowired
    private FunnelActionRepository funnelActionRepository;

    @AfterEach
    void tearDown() {
        funnelActionRepository.deleteAll();
    }

    private FunnelActionEventDto eventDto(String actionId) {
        return new FunnelActionEventDto(
                actionId,
                "b9d8a554-940f-4d72-b6de-711616158aad",
                42L,
                "anon-1",
                null,
                "VIEW_EVENT_DETAIL",
                OffsetDateTime.now(),
                OffsetDateTime.now(),
                Map.of());
    }

    @Test
    void consume_indexesFunnelActionIntoElasticsearch() {
        FunnelActionEventDto eventDto = eventDto("action-1");

        funnelActionConsumer.consume(eventDto);

        Optional<FunnelAction> saved = funnelActionRepository.findById("action-1");
        assertThat(saved).isPresent();
        assertThat(saved.get().getSessionId()).isEqualTo(eventDto.sessionId());
        assertThat(saved.get().getEventId()).isEqualTo(42L);
        assertThat(saved.get().getActionType()).isEqualTo("VIEW_EVENT_DETAIL");
    }

    @Test
    void consume_sameActionIdTwice_overwritesInsteadOfDuplicating() {
        FunnelActionEventDto first = eventDto("action-dup");
        FunnelActionEventDto retried = eventDto("action-dup");

        funnelActionConsumer.consume(first);
        funnelActionConsumer.consume(retried);

        assertThat(funnelActionRepository.count()).isEqualTo(1);
    }
}
