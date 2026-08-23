package com.min.edu.event.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.response.ApiResponse;
import com.min.edu.event.dto.PosterExtractionDto;
import com.min.edu.event.service.PosterVisionExtractionService;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/v1")
public class PosterVisionController {
    private final PosterVisionExtractionService service;

    public PosterVisionController(PosterVisionExtractionService service) {
        this.service = service;
    }

    @PostMapping("/organizations/{organizationId}/events/poster-extraction")
    public ApiResponse<PosterExtractionDto> extract(@PathVariable Long organizationId,
            @RequestPart("image") MultipartFile image,
            @AuthenticationPrincipal AuthenticatedMemberDto actor) {
        return ApiResponse.success(service.extract(organizationId, image, actor));
    }
}
