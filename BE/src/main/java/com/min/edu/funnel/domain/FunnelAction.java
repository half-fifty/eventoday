package com.min.edu.funnel.domain;

import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.data.annotation.Id;
import org.springframework.data.elasticsearch.annotations.Document;
import org.springframework.data.elasticsearch.annotations.Field;
import org.springframework.data.elasticsearch.annotations.FieldType;

import com.min.edu.funnel.dto.FunnelActionEventDto;

import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 사용자 행동 1건의 원본 기록. 수정되지 않는 append-only 데이터로, actionId를 ES 문서 _id로 사용해
 * Kafka 재전송 시에도 같은 문서를 덮어쓰기만 하도록(멱등) 한다.
 */
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Document(indexName = "funnel-actions")
public class FunnelAction {

    @Id
    private String actionId;

    @Field(type = FieldType.Long)
    private Long eventId;

    @Field(type = FieldType.Keyword)
    private String anonymousId;

    @Field(type = FieldType.Long)
    private Long userId;

    @Field(type = FieldType.Keyword)
    private String sessionId;

    @Field(type = FieldType.Keyword)
    private String actionType;

    @Field(type = FieldType.Date)
    private OffsetDateTime occurredAt;

    @Field(type = FieldType.Date)
    private OffsetDateTime receivedAt;

    @Field(type = FieldType.Object)
    private Map<String, Object> properties;

    @Builder
    private FunnelAction(
            String actionId,
            Long eventId,
            String anonymousId,
            Long userId,
            String sessionId,
            String actionType,
            OffsetDateTime occurredAt,
            OffsetDateTime receivedAt,
            Map<String, Object> properties) {
        this.actionId = actionId;
        this.eventId = eventId;
        this.anonymousId = anonymousId;
        this.userId = userId;
        this.sessionId = sessionId;
        this.actionType = actionType;
        this.occurredAt = occurredAt;
        this.receivedAt = receivedAt;
        this.properties = properties;
    }

    public static FunnelAction from(FunnelActionEventDto eventDto) {
        return FunnelAction.builder()
            .actionId(eventDto.actionId())
            .eventId(eventDto.eventId())
            .anonymousId(eventDto.anonymousId())
            .userId(eventDto.userId())
            .sessionId(eventDto.sessionId())
            .actionType(eventDto.actionType())
            .occurredAt(eventDto.occurredAt())
            .receivedAt(eventDto.receivedAt())
            .properties(eventDto.properties())
            .build();
    }
}
