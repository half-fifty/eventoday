package com.min.edu.booth.dto;

import java.math.BigDecimal;
import java.util.List;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoothUpdateRequestDto {

    @NotBlank
    @Size(max = 30)
    private String boothCode;

    @NotBlank
    @Size(max = 50)
    private String boothType;

    @Size(max = 50)
    private String floorName;

    @Size(max = 50)
    private String zoneName;

    @Size(max = 200)
    private String locationDescription;

    @DecimalMin(value = "0", inclusive = true)
    @Digits(integer = 4, fraction = 2)
    private BigDecimal widthMeter;

    @DecimalMin(value = "0", inclusive = true)
    @Digits(integer = 4, fraction = 2)
    private BigDecimal depthMeter;

    @DecimalMin(value = "0", inclusive = true)
    @Digits(integer = 6, fraction = 2)
    private BigDecimal areaSqm;

    private List<String> basicEquipment;

    private boolean electricityAvailable;

    private boolean waterAvailable;

    private boolean drainageAvailable;

    private boolean internetAvailable;

    @NotNull
    @DecimalMin(value = "0", inclusive = true)
    @Digits(integer = 12, fraction = 0)
    private BigDecimal price;
}
