package com.min.edu.event.ai.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.event.ai.dto.EventContentAiDtos;
import com.min.edu.event.ai.service.EventContentAiService;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/**
 * 행사 공지·자료 AI 작성 보조 컨트롤러 (해당 행사 EVENT_MANAGER 또는 PLATFORM_ADMIN)
 *
 * API 키는 서버에서만 다루고, 프론트엔드는 이 엔드포인트를 통해서만 모델을 호출한다.
 * 생성 결과를 저장하지 않으므로 조회 성격이지만, 요청 본문이 길고 외부 호출을 유발해 POST로 둔다.
 */
@RestController
public class EventContentAiController {

    private final EventContentAiService service;

    public EventContentAiController(EventContentAiService service) {
        this.service = service;
    }

    @PostMapping("/v1/events/{eventId}/ai/content")
    public ApiResponse<EventContentAiDtos.GenerateResponse> generate(
            @PathVariable Long eventId,
            @Valid @RequestBody EventContentAiDtos.GenerateRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        if (request == null) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        return ApiResponse.success(service.generate(eventId, request, actor));
    }
}
