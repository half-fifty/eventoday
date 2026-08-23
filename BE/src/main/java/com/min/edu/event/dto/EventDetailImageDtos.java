package com.min.edu.event.dto;

import com.min.edu.event.domain.EventDetailImage;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.List;

public final class EventDetailImageDtos {
    private EventDetailImageDtos() {}

    public record Item(Long id, Long fileId, int displayOrder, String altText) {
        public static Item from(EventDetailImage image) {
            return new Item(image.getId(), image.getFileId(), image.getDisplayOrder(), image.getAltText());
        }
    }

    public record SaveItem(
            @NotNull Long fileId,
            @Size(max = 300) String altText) {}

    public record SaveRequest(
            @NotNull @Size(max = 20) List<@Valid SaveItem> images) {}
}
