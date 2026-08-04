package com.min.edu.booth.dto;

import jakarta.validation.constraints.Size;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class BoothIntroUpdateRequestDto {

    @Size(max = 150)
    private String displayName;

    @Size(max = 300)
    private String shortIntro;

    private String description;

    private String exhibitionContent;

    private Long representativeFileId;
}
