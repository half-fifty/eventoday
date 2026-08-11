package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReview;
import com.min.edu.booth.dto.BoothReviewResponse;
import com.min.edu.booth.dto.CreateBoothReviewRequest;
import com.min.edu.booth.dto.UpdateBoothReviewRequest;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.Member;
import com.min.edu.member.repository.MemberRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
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
    private final MemberRepository memberRepository;

    /**
     * 1. 리뷰 작성
     */
    public BoothReviewResponse createReview(
            Long boothId,
            CreateBoothReviewRequest request,
            Long memberId) {

        OffsetDateTime now = OffsetDateTime.now();

        // 1) 중복 리뷰 확인
        boothReviewRepository.findByMemberIdAndBoothId(memberId, boothId)
                .ifPresent(existing -> {
                    throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
                });

        // 2) Member 조회 (memberName 저장용)
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 3) 리뷰 객체 생성
        BoothReview review = BoothReview.builder()
                .boothId(boothId)
                .memberId(memberId)
                .memberName(member.getNickname())  // ✅ memberName 저장
                .rating(request.getRating())
                .comment(request.getComment())
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            BoothReview saved = boothReviewRepository.saveAndFlush(review);
            return toResponse(saved);

        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * 2. 평균 별점 조회
     */
    public Double getAverageRating(Long boothId) {
        return boothReviewRepository.findAverageRatingByBoothId(boothId)
                .orElse(0.0);
    }

    /**
     * 3. 부스별 후기 목록 (WBS-159)
     */
    public Page<BoothReviewResponse> getBoothReviews(Long boothId, Pageable pageable) {
        Page<BoothReview> reviews = boothReviewRepository.findByBoothIdOrderByCreatedAtDesc(boothId, pageable);
        return reviews.map(this::toResponse);
    }

    /**
     * 3-1. Controller에서 호출하는 getReviews() 메서드
     */
    public Page<BoothReviewResponse> getReviews(Long boothId, Pageable pageable) {
        return getBoothReviews(boothId, pageable);
    }

    /**
     * 4. 내 작성 후기 목록 (WBS-160)
     */
    public Page<BoothReviewResponse> getMyReviews(Long memberId, Pageable pageable) {
        Page<BoothReview> reviews = boothReviewRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
        return reviews.map(this::toResponse);
    }

    /**
     * 5. 부스별 리뷰 검색
     */
    public Page<BoothReviewResponse> searchReviews(
            Long boothId, String keyword, Pageable pageable) {
        Page<BoothReview> reviews = boothReviewRepository
                .findByBoothIdAndCommentContainingIgnoreCase(boothId, keyword, pageable);
        return reviews.map(this::toResponse);
    }

    /**
     * 6. 리뷰 수정
     */
    public BoothReviewResponse updateReview(
            Long boothId,
            Long reviewId,
            UpdateBoothReviewRequest request,
            Long memberId) {

        // 1) 리뷰 조회
        BoothReview review = boothReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2) 부스 ID 확인
        if (!review.getBoothId().equals(boothId)) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        // 3) 본인만 수정 가능
        if (!review.getMemberId().equals(memberId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        // 4) 리뷰 정보 업데이트
        review.updateRating(request.getRating());
        review.updateComment(request.getContent());
        review.updateUpdatedAt(OffsetDateTime.now());

        // 5) DB 저장
        boothReviewRepository.saveAndFlush(review);
        return toResponse(review);
    }

    /**
     * 7. 리뷰 삭제
     */
    public void deleteReview(Long boothId, Long reviewId, Long memberId) {
        // 1) 리뷰 조회
        BoothReview review = boothReviewRepository.findByIdAndBoothId(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2) 권한 확인
        if (!review.getMemberId().equals(memberId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        // 3) 삭제
        boothReviewRepository.delete(review);
    }

    /**
     * BoothReview → BoothReviewResponse 변환
     */
    private BoothReviewResponse toResponse(BoothReview review) {
        return BoothReviewResponse.builder()
                .id(review.getId())
                .boothId(review.getBoothId())
                .memberName(review.getMemberName())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}