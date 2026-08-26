package com.min.edu.file.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.file.domain.FileAccessLevel;
import com.min.edu.file.dto.FileMetaResponseDto;
import com.min.edu.file.dto.FileUploadResponseDto;
import com.min.edu.file.service.FileService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.MediaType;

/**
 * 공통 파일 API
 * POST /v1/files          - 파일 업로드
 * GET  /v1/files/{fileId} - 파일 메타정보 조회
 * GET  /v1/files/{fileId}/download - 파일 다운로드
 */
@RestController
@RequestMapping("/v1/files")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /**
     * 파일 업로드
     * - 권한: MEMBER
     * - Content-Type: multipart/form-data
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<FileUploadResponseDto> uploadFile(
            @RequestParam("file") MultipartFile file,
            @RequestParam(value = "accessLevel", defaultValue = "PRIVATE") FileAccessLevel accessLevel,
            @AuthenticationPrincipal AuthenticatedMemberDto authenticatedMember) {

        FileUploadResponseDto response = fileService.upload(
                file,
                accessLevel,
                authenticatedMember.getMemberId()
        );

        return ApiResponse.success(response);
    }

    /**
     * 파일 메타정보 조회
     * - PUBLIC 파일: 로그인 회원 누구나 조회 가능
     * - PRIVATE 파일: 업로드한 본인만 조회 가능
     */
    @GetMapping("/{fileId}")
    public ApiResponse<FileMetaResponseDto> getFileMeta(
            @PathVariable Long fileId,
            @AuthenticationPrincipal AuthenticatedMemberDto authenticatedMember) {

        FileMetaResponseDto response = fileService.getFileMeta(
                fileId,
                authenticatedMember.getMemberId()
        );

        return ApiResponse.success(response);
    }

    /**
     * 파일 다운로드 (FILE-API-003)
     * Presigned URL로 302 리다이렉트하여 S3에서 직접 다운로드한다.
     * - PUBLIC 파일: 로그인 회원 누구나 다운로드 가능
     * - PRIVATE 파일: 업로드한 본인만 다운로드 가능
     */
    @GetMapping("/{fileId}/download")
    public ResponseEntity<Void> downloadFile(
            @PathVariable Long fileId,
            @AuthenticationPrincipal AuthenticatedMemberDto authenticatedMember) {

        // 비로그인 사용자도 PUBLIC 파일은 내려받을 수 있어야 하므로 authenticatedMember가 null일 수 있다.
        // PRIVATE 파일 접근 제한은 FileService.findAndCheckAccess가 memberId==null을 소유자 불일치로 처리해 계속 막는다.
        Long memberId = authenticatedMember != null ? authenticatedMember.getMemberId() : null;
        String presignedUrl = fileService.getFileDownloadUrl(fileId, memberId);

        // 302 응답은 URL을 키로 브라우저에 캐시될 수 있어, no-store가 없으면 같은 기기에서
        // 계정을 바꿔도 이전 사용자의 PRIVATE 파일 Location이 재사용될 위험이 있다.
        return ResponseEntity.status(HttpStatus.FOUND)
                .header(HttpHeaders.CACHE_CONTROL, "no-store")
                .header(HttpHeaders.LOCATION, presignedUrl)
                .build();
    }
}