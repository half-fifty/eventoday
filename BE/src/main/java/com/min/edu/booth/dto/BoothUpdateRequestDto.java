package com.min.edu.booth.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoothUpdateRequestDto {

    @NotBlank
    private String boothCode;

    @NotBlank
    private String boothType;

    private String floorName;

    private String zoneName;

    private String locationDescription;

    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal widthMeter;

    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal depthMeter;

    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal areaSqm;

    private List<String> basicEquipment;

    private boolean electricityAvailable;

    private boolean waterAvailable;

    private boolean drainageAvailable;

    private boolean internetAvailable;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    private BigDecimal price;
}
