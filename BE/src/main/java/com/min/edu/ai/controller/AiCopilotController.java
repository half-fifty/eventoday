package com.min.edu.ai.controller;

import com.min.edu.ai.dto.AiCopilotRequest;
import com.min.edu.ai.dto.AiCopilotResponse;
import com.min.edu.ai.service.AiCopilotService;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class AiCopilotController {

    private final AiCopilotService aiCopilotService;

    public AiCopilotController(AiCopilotService aiCopilotService) {
        this.aiCopilotService = aiCopilotService;
    }

    @PostMapping("/events/{eventId}/ai/copilot")
    public ApiResponse<AiCopilotResponse> ask(
            @PathVariable Long eventId,
            @Valid @RequestBody AiCopilotRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(aiCopilotService.ask(eventId, request, actor));
    }
}
