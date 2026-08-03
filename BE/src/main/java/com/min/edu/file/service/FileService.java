package com.min.edu.file.service;

import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.file.domain.FileAccessLevel;
import com.min.edu.file.domain.FileAsset;
import com.min.edu.file.dto.FileMetaResponseDto;
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
     * 파일 메타정보를 조회한다.
     * - PUBLIC 파일: 로그인 회원 누구나 조회 가능
     * - PRIVATE 파일: 업로드한 본인만 조회 가능
     *
     * @param fileId   조회할 파일 ID
     * @param memberId 현재 로그인 회원 ID
     * @return 파일 메타정보 + 다운로드 URL
     */
    @Transactional(readOnly = true)
    public FileMetaResponseDto getFileMeta(Long fileId, Long memberId) {
        FileAsset fileAsset = findAndCheckAccess(fileId, memberId);
        String downloadUrl = fileStorageService.generatePresignedUrl(fileAsset.getStorageKey());
        return FileMetaResponseDto.of(fileAsset, downloadUrl);
    }

    /**
     * 파일 다운로드용 Presigned URL을 반환한다.
     * - PUBLIC 파일: 로그인 회원 누구나 다운로드 가능
     * - PRIVATE 파일: 업로드한 본인만 다운로드 가능
     *
     * @param fileId   다운로드할 파일 ID
     * @param memberId 현재 로그인 회원 ID
     * @return 10분간 유효한 Presigned URL
     */
    @Transactional(readOnly = true)
    public String getFileDownloadUrl(Long fileId, Long memberId) {
        FileAsset fileAsset = findAndCheckAccess(fileId, memberId);
        return fileStorageService.generatePresignedUrl(fileAsset.getStorageKey());
    }

    /**
     * 파일을 조회하고 접근 권한을 검증하는 공통 메서드.
     * PRIVATE 파일은 업로드한 본인만 접근 가능하다.
     */
    private FileAsset findAndCheckAccess(Long fileId, Long memberId) {
        FileAsset fileAsset = fileAssetRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.FILE_NOT_FOUND));

        if (fileAsset.getAccessLevel() == FileAccessLevel.PRIVATE
                && !fileAsset.getUploadedBy().equals(memberId)) {
            throw new BusinessException(GlobalErrorCode.FILE_ACCESS_DENIED);
        }

        return fileAsset;
    }
}