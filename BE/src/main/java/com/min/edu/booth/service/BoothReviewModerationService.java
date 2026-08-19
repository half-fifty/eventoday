package com.min.edu.booth.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.BoothReview;
import com.min.edu.booth.domain.BoothReviewReport;
import com.min.edu.booth.repository.BoothReviewReportRepository;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 리뷰 신고 접수 + 신고 누적 시 자동 숨김, 그리고 운영자의 수동 숨김/해제를 다룬다.
// 삭제 대신 숨김(soft-hide)만 하는 이유: 하드 삭제하면 나중에 오판이었는지 감사(audit)할 근거가
// 사라지고, 운영자가 되돌릴 수도 없다. hidden_reason/hidden_at을 남겨 추적 가능하게 한다.
@Service
@RequiredArgsConstructor
@Transactional
public class BoothReviewModerationService {

    private static final int AUTO_HIDE_REPORT_THRESHOLD = 3;
    private static final String REASON_REPORTED = "REPORTED";
    private static final String REASON_MANAGER_HIDDEN = "MANAGER_HIDDEN";

    private final BoothReviewRepository boothReviewRepository;
    private final BoothReviewReportRepository boothReviewReportRepository;
    private final BoothManagerPermissionChecker boothManagerPermissionChecker;

    /**
     * 리뷰 신고. 같은 회원이 같은 리뷰를 두 번 신고할 수 없고, 신고가 누적 임계치(3건)를
     * 넘는 순간 자동으로 숨김 처리된다.
     */
    public void reportReview(Long boothId, Long reviewId, String reason, Long reporterMemberId) {
        BoothReview review = boothReviewRepository.findByIdAndBoothId(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 본인 리뷰 자기 신고 방지
        if (review.getMemberId().equals(reporterMemberId)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            boothReviewReportRepository.saveAndFlush(BoothReviewReport.builder()
                    .boothReviewId(reviewId)
                    .reporterMemberId(reporterMemberId)
                    .reason(reason)
                    .createdAt(OffsetDateTime.now())
                    .build());
        } catch (DataIntegrityViolationException e) {
            // 동시 신고가 사전 중복 검사를 함께 통과한 경우, 유니크 제약 위반을 409로 변환
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_ALREADY_REPORTED, e);
        }

        long reportCount = boothReviewReportRepository.countByBoothReviewId(reviewId);
        if (reportCount >= AUTO_HIDE_REPORT_THRESHOLD && !review.isHidden()) {
            review.hide(REASON_REPORTED, OffsetDateTime.now());
            boothReviewRepository.saveAndFlush(review);
        }
    }

    /**
     * 운영자(부스 담당자)의 수동 숨김 — 신고 누적을 기다릴 필요 없이 즉시 조치할 수 있다.
     */
    public void hideReview(Long boothId, Long reviewId, AuthenticatedMemberDto manager) {
        boothManagerPermissionChecker.requireBoothManager(boothId, manager);

        BoothReview review = boothReviewRepository.findByIdAndBoothId(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        review.hide(REASON_MANAGER_HIDDEN, OffsetDateTime.now());
        boothReviewRepository.saveAndFlush(review);
    }

    /**
     * 운영자가 숨김 처리를 오판이었다고 판단해 되돌리는 경우.
     */
    public void unhideReview(Long boothId, Long reviewId, AuthenticatedMemberDto manager) {
        boothManagerPermissionChecker.requireBoothManager(boothId, manager);

        BoothReview review = boothReviewRepository.findByIdAndBoothId(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        review.unhide();
        boothReviewRepository.saveAndFlush(review);
    }
}
