package com.min.edu.application.dto;

import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * 부스 신청 목록 페이징 응답 - BoothPageResponse 패턴과 동일
 */
@Getter
@AllArgsConstructor
public class BoothApplicationPageResponse {

    private List<BoothApplicationResponseDto> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean first;
    private boolean last;
    private boolean empty;
}