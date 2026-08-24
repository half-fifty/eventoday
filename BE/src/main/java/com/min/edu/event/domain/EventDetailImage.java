package com.min.edu.event.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.OffsetDateTime;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "event_detail_images")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class EventDetailImage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false)
    private Long eventId;

    @Column(name = "file_id", nullable = false)
    private Long fileId;

    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "alt_text", length = 300)
    private String altText;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    public static EventDetailImage create(Long eventId, Long fileId, int displayOrder,
            String altText, OffsetDateTime now) {
        return EventDetailImage.builder()
                .eventId(eventId)
                .fileId(fileId)
                .displayOrder(displayOrder)
                .altText(altText == null || altText.isBlank() ? null : altText.trim())
                .createdAt(now)
                .updatedAt(now)
                .build();
    }
}
