package com.min.edu.booth.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.booth.domain.BoothReview;
import com.min.edu.booth.domain.BoothReviewReply;
import com.min.edu.booth.dto.BoothReviewReplyResponse;
import com.min.edu.booth.repository.BoothReviewReplyRepository;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.notification.domain.NotificationType;
import java.time.OffsetDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

// 부스 담당자(운영자)가 방문객 리뷰에 공식 답글을 남기는 기능. 신고/숨김(BoothReviewModerationService)과는
// 별개의 관심사라 서비스를 분리했다 — 저건 "안 좋은 내용을 치우는" 것이고 이건 "정상적으로 응대하는" 것.
@Service
@RequiredArgsConstructor
@Transactional
public class BoothReviewReplyService {

    private final BoothReviewRepository boothReviewRepository;
    private final BoothReviewReplyRepository boothReviewReplyRepository;
    private final BoothManagerPermissionChecker boothManagerPermissionChecker;
    private final ApplicationEventPublisher applicationEventPublisher;

    /**
     * 답글 등록/수정 — 이미 답글이 있으면 내용만 갱신한다 (리뷰당 답글은 하나).
     * 새로 등록되는 경우에만 리뷰 작성자에게 알림을 보낸다 (수정할 때마다 매번 알리면 스팸이 된다).
     */
    public BoothReviewReplyResponse createOrUpdateReply(
            Long boothId, Long reviewId, String content, AuthenticatedMemberDto manager) {
        boothManagerPermissionChecker.requireBoothManager(boothId, manager);

        // 비관적 락으로 같은 리뷰에 대한 동시 답글 등록을 직렬화한다. 락 없이 "답글 있나?" 조회만 하면
        // 두 요청이 동시에 "없음"을 보고 둘 다 새로 만들려다 두 번째 saveAndFlush가 유니크 제약
        // 위반(500)으로 실패할 수 있다.
        BoothReview review = boothReviewRepository.findByIdAndBoothIdForUpdate(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        OffsetDateTime now = OffsetDateTime.now();
        var existingReply = boothReviewReplyRepository.findByBoothReviewId(reviewId);
        boolean isNewReply = existingReply.isEmpty();
        BoothReviewReply reply = existingReply
                .map(existing -> {
                    existing.updateContent(content, now);
                    return existing;
                })
                .orElseGet(() -> BoothReviewReply.builder()
                        .boothReviewId(reviewId)
                        .managerMemberId(manager.getMemberId())
                        .content(content)
                        .createdAt(now)
                        .updatedAt(now)
                        .build());

        BoothReviewReply saved = boothReviewReplyRepository.saveAndFlush(reply);

        if (isNewReply) {
            applicationEventPublisher.publishEvent(new BoothReviewNotificationEvent(
                    review.getId(),
                    review.getMemberId(),
                    NotificationType.BOOTH_REVIEW_REPLIED,
                    "작성하신 리뷰에 답글이 달렸습니다",
                    truncate(content)));
        }

        return toResponse(saved);
    }

    private String truncate(String content) {
        if (content == null || content.length() <= 80) {
            return content;
        }
        return content.substring(0, 80) + "...";
    }

    public void deleteReply(Long boothId, Long reviewId, AuthenticatedMemberDto manager) {
        boothManagerPermissionChecker.requireBoothManager(boothId, manager);

        boothReviewRepository.findByIdAndBoothIdForUpdate(reviewId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        boothReviewReplyRepository.deleteByBoothReviewId(reviewId);
    }

    static BoothReviewReplyResponse toResponse(BoothReviewReply reply) {
        return BoothReviewReplyResponse.builder()
                .id(reply.getId())
                .content(reply.getContent())
                .createdAt(reply.getCreatedAt())
                .updatedAt(reply.getUpdatedAt())
                .build();
    }
}
