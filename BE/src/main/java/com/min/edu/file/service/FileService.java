package com.min.edu.file.service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.file.domain.FileAccessLevel;
import com.min.edu.file.domain.FileAsset;
import com.min.edu.file.dto.FileUploadResponseDto;
import com.min.edu.file.repository.FileAssetRepository;
import com.min.edu.file.storage.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FileService {

    // 실행 파일을 포함한 허용 금지 MIME 타입 목록
    private static final Set<String> BLOCKED_MIME_TYPES = Set.of(
            "application/x-msdownload",
            "application/x-executable",
            "application/x-sh",
            "application/x-bat",
            "application/x-msdos-program"
    );

    // 파일 용도별 최대 크기: 기본 10MB
    private static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;

    private final FileAssetRepository fileAssetRepository;
    private final FileStorageService fileStorageService;

    /**
     * 파일을 검증하고 S3에 업로드한 뒤, 메타정보를 DB에 저장한다.
     *
     * @param file        업로드할 파일
     * @param accessLevel PUBLIC 또는 PRIVATE (기본값 PRIVATE)
     * @param uploaderId  업로드한 회원 ID
     * @return 저장된 파일 메타정보
     */
    @Transactional
    public FileUploadResponseDto upload(MultipartFile file, FileAccessLevel accessLevel, Long uploaderId) {
        // 빈 파일 검증
        if (file.isEmpty()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // 파일 크기 검증
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new BusinessException(GlobalErrorCode.FILE_SIZE_EXCEEDED);
        }

        // 실행 파일 및 허용되지 않는 MIME 타입 차단
        String mimeType = file.getContentType();
        if (mimeType == null || BLOCKED_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException(GlobalErrorCode.INVALID_FILE_TYPE);
        }

        // S3 업로드 후 storageKey 획득
        String storageKey = fileStorageService.upload(file);

        // 파일 메타정보 DB 저장
        FileAsset fileAsset = FileAsset.builder()
                .uploadedBy(uploaderId)
                .storageKey(storageKey)
                .originalName(file.getOriginalFilename())
                .mimeType(mimeType)
                .fileSize(file.getSize())
                .accessLevel(accessLevel)
                .createdAt(OffsetDateTime.now())
                .build();

        FileAsset saved = fileAssetRepository.save(fileAsset);

        return FileUploadResponseDto.from(saved);
    }

    /**
     * fileId로 파일 메타정보를 조회한다.
     */
    @Transactional(readOnly = true)
    public FileAsset getFileAsset(Long fileId) {
        return fileAssetRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.FILE_NOT_FOUND));
    }
}