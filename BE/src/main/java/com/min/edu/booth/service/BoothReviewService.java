package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReview;
import com.min.edu.booth.dto.BoothAverageRatingResponse;
import com.min.edu.booth.dto.BoothReviewResponse;
import com.min.edu.booth.dto.CreateBoothReviewRequest;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothReviewService {

    private final BoothReviewRepository boothReviewRepository;

    // 1. 리뷰 작성 (별점 + 리뷰 텍스트)
    public BoothReviewResponse createReview(
            Long boothId,
            CreateBoothReviewRequest request,
            Long memberId) {

        // 중복 리뷰 확인 (회원 1인 부스당 1개만)
        boothReviewRepository.findByMemberIdAndBoothId(memberId, boothId)
                .ifPresent(existing -> {
                    throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
                });

        // 리뷰 생성
        BoothReview review = BoothReview.builder()
                .boothId(boothId)
                .memberId(memberId)
                .rating(request.getRating())
                .comment(request.getComment())
                .createdAt(OffsetDateTime.now())
                .build();

        BoothReview saved = boothReviewRepository.saveAndFlush(review);
        return toResponse(saved);
    }

    // 2. 평균 별점 조회
    @Transactional(readOnly = true)
    public BoothAverageRatingResponse getAverageRating(Long boothId) {
        Double averageRating = boothReviewRepository.findAverageRatingByBoothId(boothId)
                .orElse(null);
        long reviewCount = boothReviewRepository.countByBoothId(boothId);

        return BoothAverageRatingResponse.builder()
                .boothId(boothId)
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .build();
    }

    // 3. 부스별 후기 목록 조회 (WBS-159)
    @Transactional(readOnly = true)
    public Page<BoothReviewResponse> getBoothReviews(Long boothId, Pageable pageable) {
        return boothReviewRepository.findByBoothIdOrderByCreatedAtDesc(boothId, pageable)
                .map(this::toResponse);
    }

    // 4. 내 작성 후기 목록 조회 (WBS-160)
    @Transactional(readOnly = true)
    public Page<BoothReviewResponse> getMyReviews(Long memberId, Pageable pageable) {
        return boothReviewRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable)
                .map(this::toResponse);
    }

    private BoothReviewResponse toResponse(BoothReview review) {
        return BoothReviewResponse.builder()
                .id(review.getId())
                .boothId(review.getBoothId())
                .memberId(review.getMemberId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}