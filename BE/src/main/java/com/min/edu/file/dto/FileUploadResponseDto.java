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
public class FileUploadResponseDto {

    private Long fileId;
    private String originalName;
    private String mimeType;
    private Long fileSize;
    private FileAccessLevel accessLevel;
    private OffsetDateTime createdAt;

    public static FileUploadResponseDto from(FileAsset fileAsset) {
        return FileUploadResponseDto.builder()
                .fileId(fileAsset.getId())
                .originalName(fileAsset.getOriginalName())
                .mimeType(fileAsset.getMimeType())
                .fileSize(fileAsset.getFileSize())
                .accessLevel(fileAsset.getAccessLevel())
                .createdAt(fileAsset.getCreatedAt())
                .build();
    }
}