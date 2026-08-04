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
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.OffsetDateTime;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileService {

    // 허용된 MIME 타입 화이트리스트 (블랙리스트는 Content-Type 조작으로 우회 가능)
    private static final Set<String> ALLOWED_MIME_TYPES = Set.of(
            "image/jpeg",
            "image/png",
            "image/gif",
            "image/webp",
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document"
    );

    // 허용된 파일 확장자 (MIME 타입 우회 2차 방어)
    private static final Set<String> ALLOWED_EXTENSIONS = Set.of(
            "jpg", "jpeg", "png", "gif", "webp", "pdf", "doc", "docx"
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

        // MIME 타입 화이트리스트 검증
        String mimeType = file.getContentType();
        if (mimeType == null || !ALLOWED_MIME_TYPES.contains(mimeType)) {
            throw new BusinessException(GlobalErrorCode.INVALID_FILE_TYPE);
        }

        // 파일 확장자 검증
        String originalName = file.getOriginalFilename();
        if (originalName == null || originalName.lastIndexOf('.') == -1) {
            throw new BusinessException(GlobalErrorCode.INVALID_FILE_TYPE);
        }
        String ext = originalName.substring(originalName.lastIndexOf('.') + 1).toLowerCase();
        if (!ALLOWED_EXTENSIONS.contains(ext)) {
            throw new BusinessException(GlobalErrorCode.INVALID_FILE_TYPE);
        }

        // S3 업로드 후 storageKey 획득
        String storageKey = fileStorageService.upload(file);

        // 파일 메타정보 DB 저장 — 실패 시 S3 고아 파일 보상 삭제
        FileAsset saved;
        try {
            FileAsset fileAsset = FileAsset.builder()
                    .uploadedBy(uploaderId)
                    .storageKey(storageKey)
                    .originalName(file.getOriginalFilename())
                    .mimeType(mimeType)
                    .fileSize(file.getSize())
                    .accessLevel(accessLevel)
                    .createdAt(OffsetDateTime.now())
                    .build();

            saved = fileAssetRepository.save(fileAsset);
        } catch (Exception e) {
            log.error("DB 저장 실패로 S3 파일 보상 삭제. storageKey={}", storageKey, e);
            fileStorageService.delete(storageKey);
            throw new BusinessException(GlobalErrorCode.FILE_UPLOAD_FAILED);
        }

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