package com.min.edu.interest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class InterestBoothResponse {

    @JsonProperty("boothId")
    private Long boothId;

    @JsonProperty("eventId")
    private Long eventId;

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("shortIntro")
    private String shortIntro;

    @JsonProperty("vacancyNotificationEnabled")
    private Boolean vacancyNotificationEnabled;

    @JsonProperty("averageRating")
    private Double averageRating;

    @JsonProperty("reviewCount")
    private Long reviewCount;

    @JsonProperty("hasAvailableSlots")
    private Boolean hasAvailableSlots;

    // 최근 10분 QR 스캔 집계 기준 실시간 혼잡도: HIGH / MEDIUM / LOW
    @JsonProperty("congestionLevel")
    private String congestionLevel;
}