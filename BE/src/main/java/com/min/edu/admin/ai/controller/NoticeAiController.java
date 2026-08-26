package com.min.edu.admin.ai.controller;

import com.min.edu.admin.ai.dto.NoticeAiDtos;
import com.min.edu.admin.ai.service.NoticeAiService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 공지 AI 작성 보조 컨트롤러 (PLATFORM_ADMIN 전용)
 *
 * API 키는 서버에서만 다루고, 프론트엔드는 이 엔드포인트를 통해서만 모델을 호출한다.
 * 생성 결과를 저장하지 않으므로 조회 성격이지만, 요청 본문이 길고 외부 호출을 유발해 POST로 둔다.
 */
@RestController
@RequestMapping("/v1/admin/ai")
public class NoticeAiController {

    private final NoticeAiService service;

    public NoticeAiController(NoticeAiService service) {
        this.service = service;
    }

    @PostMapping("/content")
    public ApiResponse<NoticeAiDtos.GenerateResponse> generate(
            @Valid @RequestBody NoticeAiDtos.GenerateRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        if (request == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return ApiResponse.success(service.generate(request, actor));
    }
}
