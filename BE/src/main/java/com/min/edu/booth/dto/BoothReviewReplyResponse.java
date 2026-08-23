package com.min.edu.booth.dto;

import java.time.OffsetDateTime;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewReplyResponse {

    private Long id;

    private String content;

    private OffsetDateTime createdAt;

    private OffsetDateTime updatedAt;
}
