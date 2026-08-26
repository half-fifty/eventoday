package com.min.edu.event.dto;

import com.min.edu.event.domain.EventContent;
import com.min.edu.event.domain.EventContentAudience;
import com.min.edu.event.domain.EventContentType;
import com.min.edu.file.domain.FileAsset;
import java.time.OffsetDateTime;
import java.util.List;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public final class EventContentDtos {
    private EventContentDtos() {}

    /**
     * 공지·자료 목록·상세 응답 DTO
     * pinned(상단 고정), publishedAt(게시일시) 포함
     * fileName·fileSize: 첨부파일 원본명·크기 (FE 다운로드 파일명 표시용, 없으면 null)
     *
     * downloadUrl: 첨부파일 Presigned URL (CONTENT-006/007)
     * 콘텐츠 첨부는 PRIVATE으로 저장되어 /v1/files 다운로드로는 업로더 본인만 접근할 수 있다.
     * audience 검증을 통과한 요청에만 URL을 내려주는 방식으로 접근 권한을 맞춘다.
     * (부스 신청 첨부 목록 APP-API-009과 동일한 방식)
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
            String fileName,
            Long fileSize,
            String downloadUrl,
            String version,
            boolean pinned,
            OffsetDateTime publishedAt
    ) {
        public static Summary from(EventContent ec) {
            return from(ec, null, null);
        }

        public static Summary from(EventContent ec, FileAsset fileAsset) {
            return from(ec, fileAsset, null);
        }

        public static Summary from(EventContent ec, FileAsset fileAsset, String downloadUrl) {
            return new Summary(
                    ec.getId(),
                    ec.getEventId(),
                    ec.getContentType(),
                    ec.getResourceType(),
                    ec.getAudience(),
                    ec.getTitle(),
                    ec.getContent(),
                    ec.getFileId(),
                    fileAsset != null ? fileAsset.getOriginalName() : null,
                    fileAsset != null ? fileAsset.getFileSize() : null,
                    downloadUrl,
                    ec.getVersion(),
                    ec.isPinned(),
                    ec.getPublishedAt()
            );
        }
    }

    /**
     * 전체 공지·자료 목록 항목 (CONTENT-API-006)
     * 공지사항 페이지에서 행사 이름을 함께 표시하기 위해 Summary에 eventName을 얹은 형태
     */
    public record BoardItem(String eventName, Summary content) {}

    /**
     * 전체 공지·자료 목록 페이징 응답 (CONTENT-API-006)
     * BoothApplicationPageResponse와 동일한 필드 구성
     */
    public record BoardPageResponse(
            List<BoardItem> content,
            int page,
            int size,
            long totalElements,
            int totalPages,
            boolean first,
            boolean last,
            boolean empty
    ) {}

    /** 공지·자료 등록 요청 DTO */
    public record CreateRequest(
            @NotNull EventContentType contentType,
            @Size(max = 30) String resourceType,
            @NotNull EventContentAudience audience,
            @NotBlank @Size(max = 200) String title,
            String content,
            @Size(max = 20) String version,
            boolean pinned
    ) {}

    /** 공지·자료 수정 요청 DTO */
    public record UpdateRequest(
            @NotNull EventContentType contentType,
            @Size(max = 30) String resourceType,
            @NotNull EventContentAudience audience,
            @NotBlank @Size(max = 200) String title,
            String content,
            @Size(max = 20) String version,
            boolean pinned
    ) {}
}