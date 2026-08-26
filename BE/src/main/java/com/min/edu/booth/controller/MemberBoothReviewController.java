package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothReviewResponse;
import com.min.edu.booth.service.BoothReviewService;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/members/me/booth-reviews")
public class MemberBoothReviewController {

    private final BoothReviewService reviewService;

    // 내가 작성한 부스 후기 목록 (WBS-160)
    @GetMapping
    public ResponseEntity<Page<BoothReviewResponse>> getMyReviews(
            @AuthenticationPrincipal AuthenticatedMemberDto principal,
            @PageableDefault(size = 10) Pageable pageable) {

        if (principal == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        return ResponseEntity.ok(reviewService.getMyReviews(principal.getMemberId(), pageable));
    }
}
