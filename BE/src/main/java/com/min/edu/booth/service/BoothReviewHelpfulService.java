package com.min.edu.booth.service;

import com.min.edu.booth.domain.BoothReview;
import com.min.edu.booth.domain.BoothReviewHelpfulVote;
import com.min.edu.booth.repository.BoothReviewHelpfulVoteRepository;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 리뷰 "도움이 돼요" 등록/취소를 다룬다. 신고(BoothReviewModerationService)와 구조는 비슷하지만
// 임계치 도달 시 파괴적 액션(자동 삭제)이 없는 단순 참여 신호라 비관적 락 없이 유니크 제약으로만 동시성을 막는다.
@Service
@RequiredArgsConstructor
@Transactional
public class BoothReviewHelpfulService {

    private final BoothReviewRepository boothReviewRepository;
    private final BoothReviewHelpfulVoteRepository boothReviewHelpfulVoteRepository;

    /**
     * "도움이 돼요" 등록. 본인 리뷰에는 누를 수 없고, 같은 리뷰에 두 번 누를 수 없다.
     */
    public void markHelpful(Long boothId, Long reviewId, Long memberId) {
        BoothReview review = boothReviewRepository.findByIdAndBoothId(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 숨김 처리된 리뷰는 공개 목록에서 이미 제외되므로, 직접 API 호출로 우회하는 경우도 막는다.
        if (review.isHidden()) {
            throw new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND);
        }

        if (review.getMemberId().equals(memberId)) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_SELF_HELPFUL_NOT_ALLOWED);
        }

        try {
            boothReviewHelpfulVoteRepository.saveAndFlush(BoothReviewHelpfulVote.builder()
                    .boothReviewId(reviewId)
                    .memberId(memberId)
                    .createdAt(OffsetDateTime.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 동시 요청이 사전 중복 검사를 함께 통과한 경우, 유니크 제약 위반을 409로 변환
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_ALREADY_HELPFUL, e);
        }
    }

    /**
     * "도움이 돼요" 취소 — 본인이 누른 것만 철회할 수 있다.
     */
    public void unmarkHelpful(Long boothId, Long reviewId, Long memberId) {
        boothReviewRepository.findByIdAndBoothId(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        BoothReviewHelpfulVote vote = boothReviewHelpfulVoteRepository
                .findByBoothReviewIdAndMemberId(reviewId, memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.BOOTH_REVIEW_HELPFUL_NOT_FOUND));

        boothReviewHelpfulVoteRepository.delete(vote);
    }
}
