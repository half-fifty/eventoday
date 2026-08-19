package com.min.edu.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

// fileId만 내려주고, 실제 이미지 URL은 프론트에서 기존 공통 파일 API(GET /v1/files/{fileId}/download)로
// 가져온다 — 접근 제어(PUBLIC/PRIVATE)를 파일 도메인 한 곳에서만 처리하기 위함.
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class BoothReviewPhotoResponse {

    private Long fileId;

    private Integer sortOrder;
}
