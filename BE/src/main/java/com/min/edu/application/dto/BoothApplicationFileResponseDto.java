package com.min.edu.application.dto;

import com.min.edu.booth.domain.BoothApplicationFileType;
import com.min.edu.file.domain.FileAsset;
import lombok.Builder;
import lombok.Getter;

/**
 * 부스 신청 첨부파일 응답 DTO
 */
@Getter
@Builder
public class BoothApplicationFileResponseDto {

    private Long fileId;
    /** 파일 구분: ESTIMATE(견적서), OTHER(기타) */
    private BoothApplicationFileType fileType;
    private String originalName;
    private String mimeType;
    private Long fileSize;
    /** S3 Presigned URL (10분 유효) */
    private String downloadUrl;

    public static BoothApplicationFileResponseDto of(
            BoothApplicationFileType fileType,
            FileAsset fileAsset,
            String downloadUrl) {
        return BoothApplicationFileResponseDto.builder()
                .fileId(fileAsset.getId())
                .fileType(fileType)
                .originalName(fileAsset.getOriginalName())
                .mimeType(fileAsset.getMimeType())
                .fileSize(fileAsset.getFileSize())
                .downloadUrl(downloadUrl)
                .build();
    }
}