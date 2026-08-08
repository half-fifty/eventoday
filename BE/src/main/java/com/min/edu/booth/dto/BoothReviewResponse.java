package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewResponse {

    private Long id;
    private Long boothId;
    private Long memberId;
    private Short rating;
    private String comment;
    private OffsetDateTime createdAt;
}
