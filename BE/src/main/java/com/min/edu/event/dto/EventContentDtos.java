package com.min.edu.event.dto;

import com.min.edu.event.domain.EventContent;
import com.min.edu.event.domain.EventContentAudience;
import com.min.edu.event.domain.EventContentType;
import java.time.OffsetDateTime;

public final class EventContentDtos {
    private EventContentDtos() {}

    /**
     * 공지·자료 목록·상세 응답 DTO
     * pinned(상단 고정), publishedAt(게시일시) 포함
     */
    public record Summary(
            Long contentId,
            Long eventId,
            EventContentType contentType,
            String resourceType,
            EventContentAudience audience,
            String title,
            String content,
            Long fileId,
            String version,
            boolean pinned,
            OffsetDateTime publishedAt
    ) {
        public static Summary from(EventContent ec) {
            return new Summary(
                    ec.getId(),
                    ec.getEventId(),
                    ec.getContentType(),
                    ec.getResourceType(),
                    ec.getAudience(),
                    ec.getTitle(),
                    ec.getContent(),
                    ec.getFileId(),
                    ec.getVersion(),
                    ec.isPinned(),
                    ec.getPublishedAt()
            );
        }
    }
}