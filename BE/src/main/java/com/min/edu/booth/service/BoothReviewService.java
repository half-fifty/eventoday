package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReview;
import com.min.edu.booth.dto.BoothAverageRatingResponse;
import com.min.edu.booth.dto.BoothReviewResponse;
import com.min.edu.booth.dto.CreateBoothReviewRequest;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
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

    /**
     * 1. 리뷰 작성
     */
    public BoothReviewResponse createReview(
            Long boothId,
            CreateBoothReviewRequest request,
            Long memberId) {

        OffsetDateTime now = OffsetDateTime.now();

        // 1) 중복 리뷰 확인
        // (1) 같은 사용자가 이미 작성한 리뷰 있는지 확인
        boothReviewRepository.findByMemberIdAndBoothId(memberId, boothId)
                .ifPresent(existing -> {
                    throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
                });

        // 2) 리뷰 객체 생성
        // (1) 필드 설정
        BoothReview review = BoothReview.builder()
                .boothId(boothId)
                .memberId(memberId)
                .rating(request.getRating())
                .comment(request.getComment())
                .createdAt(now)
                .updatedAt(now)
                .build();

        try {
            // 3) DB에 저장
            // (1) saveAndFlush로 즉시 반영
            // (2) UNIQUE 제약 위반 시 DataIntegrityViolationException 발생
            BoothReview saved = boothReviewRepository.saveAndFlush(review);
            return toResponse(saved);

        } catch (DataIntegrityViolationException e) {
            // (3) UNIQUE 제약 위반 처리
            // (a) (member_id, booth_id) 제약 위반 = 중복 리뷰
            // (b) BusinessException으로 변환
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    /**
     * 2. 평균 별점 조회
     */
    public BoothAverageRatingResponse getAverageRating(Long boothId) {
        // 1) 부스 평점 통계 조회
        // (1) Optional<Double> 처리
        Double averageRating = boothReviewRepository.findAverageRatingByBoothId(boothId)
                .orElse(0.0);

        // 2) 응답 생성
        return BoothAverageRatingResponse.builder()
                .boothId(boothId)
                .averageRating(averageRating)
                .build();
    }

    /**
     * 3. 부스별 후기 목록 (WBS-159)
     */
    public Page<BoothReviewResponse> getBoothReviews(Long boothId, Pageable pageable) {
        // 1) 부스의 모든 리뷰 조회
        // (1) 생성 순서 역순 (최신 먼저)
        Page<BoothReview> reviews = boothReviewRepository.findByBoothIdOrderByCreatedAtDesc(boothId, pageable);

        // 2) DTO 변환
        return reviews.map(this::toResponse);
    }

    /**
     * 4. 내 작성 후기 목록 (WBS-160)
     */
    public Page<BoothReviewResponse> getMyReviews(Long memberId, Pageable pageable) {
        // 1) 사용자의 모든 리뷰 조회
        // (1) 생성 순서 역순 (최신 먼저)
        Page<BoothReview> reviews = boothReviewRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);

        // 2) DTO 변환
        return reviews.map(this::toResponse);
    }

    /**
     * 5. 부스별 리뷰 검색
     */
    public Page<BoothReviewResponse> searchReviews(
            Long boothId, String keyword, Pageable pageable) {

        // 1) 키워드로 리뷰 검색
        // (1) comment 필드에서 포함된 리뷰 찾기
        // (2) 대소문자 구분 안 함
        Page<BoothReview> reviews = boothReviewRepository
                .findByBoothIdAndCommentContainingIgnoreCase(boothId, keyword, pageable);

        // 2) DTO 변환
        return reviews.map(this::toResponse);
    }

    /**
     * 6. 리뷰 수정
     */
    public BoothReviewResponse updateReview(
            Long reviewId,
            CreateBoothReviewRequest request,
            Long memberId) {

        // 1) 리뷰 조회
        // (1) 리뷰 ID로 조회
        BoothReview review = boothReviewRepository.findById(reviewId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2) 본인만 수정 가능
        // (1) 멤버 ID 확인
        if (!review.getMemberId().equals(memberId)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }

        // 3) 리뷰 정보 업데이트
        // (1) 별점 수정 (이미 Short 타입)
        review.updateRating(request.getRating());

        // (2) 댓글 수정
        review.updateComment(request.getComment());

        // (3) 수정 시간 업데이트
        review.updateUpdatedAt(OffsetDateTime.now());

        // 4) DB 저장
        boothReviewRepository.saveAndFlush(review);
        return toResponse(review);
    }

    /**
     * 7. 리뷰 삭제
     */
    public void deleteReview(Long reviewId, Long boothId, Long memberId) {
        // 1) 리뷰 조회
        // (1) 부스별 리뷰 확인
        BoothReview review = boothReviewRepository.findByIdAndBoothId(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2) 권한 확인
        // (1) 본인만 삭제 가능
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
                .memberId(review.getMemberId())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .build();
    }
}