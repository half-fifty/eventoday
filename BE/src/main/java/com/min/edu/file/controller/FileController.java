package com.min.edu.file.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.file.domain.FileAccessLevel;
import com.min.edu.file.dto.FileUploadResponseDto;
import com.min.edu.file.dto.FileMetaResponseDto;
import com.min.edu.file.service.FileService;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

/**
 * 공통 파일 업로드 API
 * POST /v1/files
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
     * - 권한: MEMBER (로그인 회원)
     * - Content-Type: multipart/form-data
     *
     * @param file        업로드할 파일
     * @param accessLevel 파일 공개 수준 (PUBLIC/PRIVATE, 기본값 PRIVATE)
     * @param authenticatedMember 현재 로그인 회원
     * @return 업로드된 파일 메타정보
     */
    @PostMapping
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
     *
     * @param fileId              조회할 파일 ID
     * @param authenticatedMember 현재 로그인 회원
     * @return 파일 메타정보 + 다운로드 URL
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
}