package com.min.edu.booth.dto;

import com.min.edu.booth.domain.VenueMapType;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class VenueMapCreateRequestDto {

    @NotNull
    private VenueMapType mapType;

    @NotBlank
    @Size(max = 50)
    private String floorName;

    @NotNull
    private Long imageFileId;

    @NotNull
    @Positive
    private Integer originalWidth;

    @NotNull
    @Positive
    private Integer originalHeight;
}
