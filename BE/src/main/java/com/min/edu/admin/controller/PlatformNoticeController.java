package com.min.edu.admin.controller;

import com.min.edu.admin.dto.PlatformAdminDtos;
import com.min.edu.admin.service.PlatformNoticeService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.response.ApiResponse;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * 플랫폼(사이트 전체) 공지 컨트롤러
 *
 * 공개 조회와 관리자 전용 CRUD의 URL 접두어가 달라
 * 클래스 단위 @RequestMapping 없이 메서드마다 전체 경로를 지정한다 (EventContentController와 동일한 방식).
 */
@RestController
public class PlatformNoticeController {

    private final PlatformNoticeService service;

    public PlatformNoticeController(PlatformNoticeService service) {
        this.service = service;
    }

    /**
     * 사이트 공지 목록 (공개 — 비로그인 포함)
     *
     * 공지사항 페이지에서 페이지 이동과 검색에 사용한다.
     * 파라미터를 생략하면 첫 페이지 20건을 반환한다.
     */
    @GetMapping("/platform-notices")
    public ApiResponse<PlatformAdminDtos.NoticePageResponse> notices(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String keyword) {
        return ApiResponse.success(service.notices(page, size, keyword));
    }

    /** 사이트 공지 상세 (공개 — 비로그인 포함) */
    @GetMapping("/platform-notices/{noticeId}")
    public ApiResponse<PlatformAdminDtos.Notice> notice(@PathVariable Long noticeId) {
        return ApiResponse.success(service.notice(noticeId));
    }

    /** 사이트 공지 등록 (PLATFORM_ADMIN) */
    @PostMapping("/v1/admin/platform/notices")
    public ApiResponse<PlatformAdminDtos.Notice> createNotice(
            @RequestBody PlatformAdminDtos.NoticeCreateRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        if (request == null) throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        return ApiResponse.success(service.createNotice(request, actor));
    }

    /** 사이트 공지 수정 (PLATFORM_ADMIN) */
    @PatchMapping("/v1/admin/platform/notices/{noticeId}")
    public ApiResponse<PlatformAdminDtos.Notice> updateNotice(
            @PathVariable Long noticeId,
            @RequestBody PlatformAdminDtos.NoticeUpdateRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        if (request == null) throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        return ApiResponse.success(service.updateNotice(noticeId, request, actor));
    }

    /** 사이트 공지 삭제 (PLATFORM_ADMIN) */
    @DeleteMapping("/v1/admin/platform/notices/{noticeId}")
    public ApiResponse<Void> deleteNotice(
            @PathVariable Long noticeId,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        service.deleteNotice(noticeId, actor);
        return ApiResponse.success();
    }
}
