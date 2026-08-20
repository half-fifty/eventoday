package com.min.edu.booth.controller;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.dto.BoothReviewReplyRequest;
import com.min.edu.booth.dto.BoothReviewReplyResponse;
import com.min.edu.booth.dto.BoothReviewResponse;
import com.min.edu.booth.dto.BoothReviewSortOption;
import com.min.edu.booth.dto.BoothReviewSummaryResponse;
import com.min.edu.booth.dto.CreateBoothReviewRequest;
import com.min.edu.booth.dto.ReportBoothReviewRequest;
import com.min.edu.booth.dto.UpdateBoothReviewRequest;
import com.min.edu.booth.service.BoothReviewModerationService;
import com.min.edu.booth.service.BoothReviewReplyService;
import com.min.edu.booth.service.BoothReviewService;
import com.min.edu.booth.service.BoothReviewSummaryService;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/booths/{boothId}/reviews")
public class BoothReviewController {

    private final BoothReviewService reviewService;
    private final BoothReviewSummaryService reviewSummaryService;
    private final BoothReviewModerationService reviewModerationService;
    private final BoothReviewReplyService reviewReplyService;

    /**
     * 부스 후기 작성
     */
    @PostMapping
    public ResponseEntity<BoothReviewResponse> createReview(
            @PathVariable Long boothId,
            @RequestBody @Valid CreateBoothReviewRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReviewResponse response = reviewService.createReview(
                boothId,
                request,
                principal.getMemberId()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 부스 후기 수정
     */
    @PutMapping("/{reviewId}")
    public ResponseEntity<BoothReviewResponse> updateReview(
            @PathVariable Long boothId,
            @PathVariable Long reviewId,
            @RequestBody @Valid UpdateBoothReviewRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReviewResponse response = reviewService.updateReview(
                boothId,
                reviewId,
                request,
                principal.getMemberId()
        );
        return ResponseEntity.ok(response);
    }

    /**
     * 부스 후기 삭제
     */
    @DeleteMapping("/{reviewId}")
    public ResponseEntity<Void> deleteReview(
            @PathVariable Long boothId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        reviewService.deleteReview(
                boothId,
                reviewId,
                principal.getMemberId()
        );
        return ResponseEntity.noContent().build();
    }

    /**
     * 부스의 모든 후기 조회 (페이징)
     */
    @GetMapping
    public ResponseEntity<Page<BoothReviewResponse>> getReviews(
            @PathVariable Long boothId,
            @PageableDefault(size = 10, page = 0)
            Pageable pageable,
            // 이름을 "sort"로 하면 스프링이 페이지네이션용 Pageable.sort 바인딩과 혼동해서
            // "ORDER BY br.RATING_DESC" 같은 잘못된 정렬 절을 자동으로 덧붙여버린다 (실제로 겪은 버그).
            // 그래서 별도 이름(sortBy)으로 받는다.
            @RequestParam(name = "sortBy", defaultValue = "LATEST") BoothReviewSortOption sort) {

        Page<BoothReviewResponse> response = reviewService.getReviews(boothId, pageable, sort);
        return ResponseEntity.ok(response);
    }

    /**
     * 부스의 평균 별점 조회
     */
    @GetMapping("/average-rating")
    public ResponseEntity<Double> getAverageRating(
            @PathVariable Long boothId) {

        Double averageRating = reviewService.getAverageRating(boothId);
        return ResponseEntity.ok(averageRating);
    }

    /**
     * 부스 리뷰 코멘트 AI 요약
     */
    @GetMapping("/summary")
    public ResponseEntity<BoothReviewSummaryResponse> getSummary(
            @PathVariable Long boothId) {

        BoothReviewSummaryResponse response = reviewSummaryService.getSummary(boothId);
        return ResponseEntity.ok(response);
    }

    /**
     * 부스 리뷰 신고 — 같은 리뷰를 두 번 신고할 수 없고, 누적 3건이면 자동 숨김 처리된다.
     */
    @PostMapping("/{reviewId}/reports")
    public ResponseEntity<Void> reportReview(
            @PathVariable Long boothId,
            @PathVariable Long reviewId,
            @RequestBody(required = false) @Valid ReportBoothReviewRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        if (principal == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }

        String reason = request != null ? request.getReason() : null;
        reviewModerationService.reportReview(boothId, reviewId, reason, principal.getMemberId());
        return ResponseEntity.noContent().build();
    }

    /**
     * 운영자(부스 담당자)의 리뷰 강제 숨김 — 신고 누적을 기다리지 않고 즉시 조치.
     */
    @PatchMapping("/{reviewId}/hide")
    public ResponseEntity<Void> hideReview(
            @PathVariable Long boothId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        reviewModerationService.hideReview(boothId, reviewId, principal);
        return ResponseEntity.noContent().build();
    }

    /**
     * 운영자가 숨김 처리를 되돌리는 경우 (오판이었다고 판단한 경우).
     */
    @PatchMapping("/{reviewId}/unhide")
    public ResponseEntity<Void> unhideReview(
            @PathVariable Long boothId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        reviewModerationService.unhideReview(boothId, reviewId, principal);
        return ResponseEntity.noContent().build();
    }

    /**
     * 운영자(부스 담당자)의 리뷰 답글 등록/수정 — 이미 답글이 있으면 내용만 갱신된다.
     */
    @PutMapping("/{reviewId}/reply")
    public ResponseEntity<BoothReviewReplyResponse> createOrUpdateReply(
            @PathVariable Long boothId,
            @PathVariable Long reviewId,
            @RequestBody @Valid BoothReviewReplyRequest request,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        BoothReviewReplyResponse response =
                reviewReplyService.createOrUpdateReply(boothId, reviewId, request.getContent(), principal);
        return ResponseEntity.ok(response);
    }

    /**
     * 운영자의 리뷰 답글 삭제.
     */
    @DeleteMapping("/{reviewId}/reply")
    public ResponseEntity<Void> deleteReply(
            @PathVariable Long boothId,
            @PathVariable Long reviewId,
            @AuthenticationPrincipal AuthenticatedMemberDto principal) {

        reviewReplyService.deleteReply(boothId, reviewId, principal);
        return ResponseEntity.noContent().build();
    }
}