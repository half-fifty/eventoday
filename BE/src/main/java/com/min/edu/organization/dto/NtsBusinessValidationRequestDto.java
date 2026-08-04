package com.min.edu.organization.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class NtsBusinessValidationRequestDto {

    private List<Business> businesses;

    @Getter
    @AllArgsConstructor
    public static class Business {

        @JsonProperty("b_no")
        private String businessNumber;

        @JsonProperty("start_dt")
        private String startDate;

        @JsonProperty("p_nm")
        private String representativeName;
    }
}
