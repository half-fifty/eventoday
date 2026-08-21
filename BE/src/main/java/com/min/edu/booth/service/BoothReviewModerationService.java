package com.min.edu.booth.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.BoothReview;
import com.min.edu.booth.domain.BoothReviewReport;
import com.min.edu.booth.domain.BoothReviewReportReason;
import com.min.edu.booth.dto.BoothReviewReportSummaryResponse;
import com.min.edu.booth.repository.BoothReviewReportRepository;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.notification.domain.NotificationType;
import java.time.OffsetDateTime;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
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
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 리뷰 신고. 같은 회원이 같은 리뷰를 두 번 신고할 수 없고, 신고가 누적 임계치(3건)를
     * 넘는 순간 자동으로 숨김 처리된다.
     */
    public void reportReview(
            Long boothId, Long reviewId, BoothReviewReportReason reasonCode, String reason, Long reporterMemberId) {
        // 비관적 락으로 이 리뷰에 대한 신고 접수를 직렬화한다. 락 없이 COUNT만 하면, 동시에 들어온
        // 신고 3건이 각자 자기 신고만 반영된 개수를 보고(예: 1, 1, 1) 아무도 임계치(3)를 못 넘겨
        // 자동 숨김이 누락될 수 있다 — READ COMMITTED에서 실제로 재현되는 경쟁 상태.
        BoothReview review = boothReviewRepository.findByIdAndBoothIdForUpdate(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 본인 리뷰 자기 신고 방지
        if (review.getMemberId().equals(reporterMemberId)) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_SELF_REPORT_NOT_ALLOWED);
        }

        try {
            boothReviewReportRepository.saveAndFlush(BoothReviewReport.builder()
                    .boothReviewId(reviewId)
                    .reporterMemberId(reporterMemberId)
                    .reasonCode(reasonCode)
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
            publishHiddenNotification(review, "신고가 누적되어 비공개 처리되었습니다.");
        }
    }

    /**
     * 운영자(부스 담당자)의 수동 숨김 — 신고 누적을 기다릴 필요 없이 즉시 조치할 수 있다.
     */
    public void hideReview(Long boothId, Long reviewId, AuthenticatedMemberDto manager) {
        boothManagerPermissionChecker.requireBoothManager(boothId, manager);

        BoothReview review = boothReviewRepository.findByIdAndBoothIdForUpdate(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 이미 숨김 상태면(신고 누적으로 자동 숨김된 경우 포함) 조용히 끝낸다 - 매니저가 두 번
        // 클릭하거나 이미 자동 숨김된 리뷰를 다시 숨기려 하면, 재알림 없이 멱등하게 처리한다.
        if (review.isHidden()) {
            return;
        }

        review.hide(REASON_MANAGER_HIDDEN, OffsetDateTime.now());
        boothReviewRepository.saveAndFlush(review);
        publishHiddenNotification(review, "부스 담당자에 의해 비공개 처리되었습니다.");
    }

    /**
     * 운영자가 숨김 처리를 오판이었다고 판단해 되돌리는 경우.
     */
    public void unhideReview(Long boothId, Long reviewId, AuthenticatedMemberDto manager) {
        boothManagerPermissionChecker.requireBoothManager(boothId, manager);

        BoothReview review = boothReviewRepository.findByIdAndBoothIdForUpdate(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        review.unhide();
        boothReviewRepository.saveAndFlush(review);
    }

    private void publishHiddenNotification(BoothReview review, String reasonMessage) {
        applicationEventPublisher.publishEvent(new BoothReviewNotificationEvent(
                review.getId(),
                review.getMemberId(),
                NotificationType.BOOTH_REVIEW_HIDDEN,
                "작성하신 리뷰가 비공개 처리되었습니다",
                reasonMessage));
    }

    /**
     * 운영자(부스 담당자)용 "신고된 리뷰" 대시보드 — 자동 숨김 임계치(3건)에 못 미친 신고 1~2건짜리
     * 리뷰도 여기서 미리 확인하고 필요하면 hideReview로 선제 조치할 수 있게 한다. 신고 많은 순 정렬.
     */
    public List<BoothReviewReportSummaryResponse> listReportedReviews(Long boothId, AuthenticatedMemberDto manager) {
        boothManagerPermissionChecker.requireBoothManager(boothId, manager);

        List<BoothReviewReport> reports = boothReviewReportRepository.findByBoothId(boothId);
        if (reports.isEmpty()) {
            return List.of();
        }

        Map<Long, List<BoothReviewReport>> reportsByReviewId = reports.stream()
                .collect(Collectors.groupingBy(
                        BoothReviewReport::getBoothReviewId, LinkedHashMap::new, Collectors.toList()));

        Map<Long, BoothReview> reviewsById = boothReviewRepository.findAllById(reportsByReviewId.keySet()).stream()
                .collect(Collectors.toMap(BoothReview::getId, Function.identity()));

        return reportsByReviewId.entrySet().stream()
                // 신고 목록 조회와 리뷰 배치 조회 사이(둘 다 락 없는 별도 SELECT)에 작성자가 직접
                // 리뷰를 삭제하면(FK CASCADE로 신고 기록은 이미 사라졌어도, 그 사이 시점에 잡힌
                // 스냅샷엔 남아있을 수 있음) reviewsById에 해당 리뷰가 없을 수 있다 - 그런 리뷰는
                // 대시보드에서 건너뛴다.
                .filter(entry -> reviewsById.containsKey(entry.getKey()))
                .map(entry -> toSummary(reviewsById.get(entry.getKey()), entry.getValue()))
                .sorted(Comparator.comparingLong(BoothReviewReportSummaryResponse::getReportCount).reversed())
                .toList();
    }

    private BoothReviewReportSummaryResponse toSummary(BoothReview review, List<BoothReviewReport> reports) {
        List<BoothReviewReportSummaryResponse.ReportDetail> details = reports.stream()
                .map(report -> BoothReviewReportSummaryResponse.ReportDetail.builder()
                        .reasonCode(report.getReasonCode())
                        .reason(report.getReason())
                        .createdAt(report.getCreatedAt())
                        .build())
                .toList();
        return BoothReviewReportSummaryResponse.builder()
                .reviewId(review.getId())
                .memberName(review.getMemberName())
                .rating(review.getRating())
                .comment(review.getComment())
                .hidden(review.isHidden())
                .reportCount(details.size())
                .reports(details)
                .build();
    }
}
