package com.min.edu.file.dto;

import com.min.edu.file.domain.FileAccessLevel;
import com.min.edu.file.domain.FileAsset;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.OffsetDateTime;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class FileMetaResponseDto {

    private Long fileId;
    private String originalName;
    private String mimeType;
    private Long fileSize;
    private FileAccessLevel accessLevel;
    private String downloadUrl;
    private OffsetDateTime createdAt;

    public static FileMetaResponseDto of(FileAsset fileAsset, String downloadUrl) {
        return FileMetaResponseDto.builder()
                .fileId(fileAsset.getId())
                .originalName(fileAsset.getOriginalName())
                .mimeType(fileAsset.getMimeType())
                .fileSize(fileAsset.getFileSize())
                .accessLevel(fileAsset.getAccessLevel())
                .downloadUrl(downloadUrl)
                .createdAt(fileAsset.getCreatedAt())
                .build();
    }
}