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
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;
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

        // 이 메서드 자체는 커밋됐지만, 이 트랜잭션에 참여한 상위 트랜잭션(예: 회원가입)이
        // 나중에 롤백되면 DB의 file_assets 행은 함께 롤백되는 반면 S3 객체는 그대로 남는다.
        // 상위 트랜잭션 완료 시점에 롤백 여부를 확인해 S3 고아 파일을 보상 삭제한다.
        registerCompensatingDeleteOnRollback(storageKey);

        return FileUploadResponseDto.from(saved);
    }

    private void registerCompensatingDeleteOnRollback(String storageKey) {
        if (!TransactionSynchronizationManager.isSynchronizationActive()) {
            return;
        }

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                if (status == TransactionSynchronization.STATUS_ROLLED_BACK) {
                    log.warn("상위 트랜잭션 롤백으로 S3 파일 보상 삭제. storageKey={}", storageKey);
                    fileStorageService.delete(storageKey);
                }
            }
        });
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
     * 플랫폼 관리자가 심사 등의 목적으로 소유자 제한 없이 파일을 열람할 때 사용한다.
     * 호출하는 쪽에서 PLATFORM_ADMIN 권한 검증을 이미 마쳤다는 전제로 소유자 검사를 생략한다.
     */
    @Transactional(readOnly = true)
    public String getFileDownloadUrlForAdmin(Long fileId) {
        FileAsset fileAsset = fileAssetRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.FILE_NOT_FOUND));
        return fileStorageService.generatePresignedUrl(fileAsset.getStorageKey());
    }

    /**
     * 플랫폼 관리자가 심사 자료 등을 완전히 폐기할 때 사용한다. DB 메타데이터와
     * 저장소 원본을 함께 삭제한다. 호출하는 쪽에서 권한 검증을 이미 마쳤다는 전제로
     * 소유자 검사를 생략한다.
     */
    @Transactional
    public void deleteFile(Long fileId) {
        fileAssetRepository.findById(fileId).ifPresent(fileAsset -> {
            fileStorageService.delete(fileAsset.getStorageKey());
            fileAssetRepository.delete(fileAsset);
        });
    }

    /**
     * 다른 도메인이 자신의 엔티티에 fileId를 참조로 저장하기 전에,
     * 해당 회원이 그 파일에 접근 가능한지만 검증한다 (presigned URL은 발급하지 않음).
     */
    @Transactional(readOnly = true)
    public void assertAccessible(Long fileId, Long memberId) {
        findAndCheckAccess(fileId, memberId);
    }

    @Transactional(readOnly = true)
    public void assertPublicAccessible(Long fileId, Long memberId) {
        FileAsset fileAsset = findAndCheckAccess(fileId, memberId);
        if (fileAsset.getAccessLevel() != FileAccessLevel.PUBLIC) {
            throw new BusinessException(GlobalErrorCode.FILE_ACCESS_DENIED);
        }
    }

    @Transactional(readOnly = true)
    public void assertPublicImageAccessible(Long fileId, Long memberId) {
        FileAsset fileAsset = findAndCheckAccess(fileId, memberId);
        if (fileAsset.getAccessLevel() != FileAccessLevel.PUBLIC) {
            throw new BusinessException(GlobalErrorCode.FILE_ACCESS_DENIED);
        }
        if (fileAsset.getMimeType() == null || !fileAsset.getMimeType().startsWith("image/")) {
            throw new BusinessException(GlobalErrorCode.INVALID_FILE_TYPE);
        }
    }

    /**
     * 대표 이미지로 쓰기 위해 파일이 PUBLIC 상태인지 검증한다.
     */
    @Transactional(readOnly = true)
    public void assertPublicAccessible(Long fileId) {
        FileAsset fileAsset = fileAssetRepository.findById(fileId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.FILE_NOT_FOUND));

        if (fileAsset.getAccessLevel() != FileAccessLevel.PUBLIC) {
            throw new BusinessException(GlobalErrorCode.FILE_ACCESS_DENIED);
        }
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
