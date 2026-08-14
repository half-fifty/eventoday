package com.min.edu.interest.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateVacancyNotificationRequest {

    @JsonProperty("enabled")
    @NotNull(message = "enabled is required")
    private Boolean enabled;
}
