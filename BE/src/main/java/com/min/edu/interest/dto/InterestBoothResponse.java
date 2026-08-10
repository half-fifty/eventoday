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

    @JsonProperty("displayName")
    private String displayName;

    @JsonProperty("shortIntro")
    private String shortIntro;

    @JsonProperty("vacancyNotificationEnabled")
    private Boolean vacancyNotificationEnabled;
}