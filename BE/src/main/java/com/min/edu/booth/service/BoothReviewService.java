package com.min.edu.booth.service;

import com.min.edu.admission.repository.AdmissionTicketRepository;
import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothReview;
import com.min.edu.booth.domain.BoothReviewPhoto;
import com.min.edu.booth.domain.BoothReviewReply;
import com.min.edu.booth.dto.BoothReviewPhotoResponse;
import com.min.edu.booth.dto.BoothReviewRatingSummaryResponse;
import com.min.edu.booth.dto.BoothReviewReplyResponse;
import com.min.edu.booth.dto.BoothReviewResponse;
import com.min.edu.booth.dto.BoothReviewSortOption;
import com.min.edu.booth.dto.CreateBoothReviewRequest;
import com.min.edu.booth.dto.UpdateBoothReviewRequest;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReviewHelpfulVoteRepository;
import com.min.edu.booth.repository.BoothReviewPhotoRepository;
import com.min.edu.booth.repository.BoothReviewReplyRepository;
import com.min.edu.booth.repository.BoothReviewReportRepository;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventStatus;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.file.repository.FileAssetRepository;
import com.min.edu.member.domain.Member;
import com.min.edu.member.repository.MemberRepository;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
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

    // 행사 종료 직후 현장이 아니라 귀가 후에 후기를 남기려는 관람객이 많아, 종료 시각을 넘겨도
    // 이 기간 안이면 계속 작성할 수 있게 유예를 둔다.
    private static final long REVIEW_WRITABLE_GRACE_PERIOD_DAYS = 7;

    // "도움이 돼요"가 이 수 이상이면 "믿을 수 있는 리뷰" 뱃지를 붙인다.
    static final int TRUSTED_REVIEW_HELPFUL_THRESHOLD = 3;

    private final BoothReviewRepository boothReviewRepository;
    private final MemberRepository memberRepository;
    private final BoothRepository boothRepository;
    private final EventRepository eventRepository;
    private final AdmissionTicketRepository admissionTicketRepository;
    private final BoothReviewProfanityFilter profanityFilter;
    private final BoothReviewAiModerationService aiModerationService;
    private final BoothReviewReplyRepository boothReviewReplyRepository;
    private final BoothReviewPhotoRepository boothReviewPhotoRepository;
    private final BoothReviewReportRepository boothReviewReportRepository;
    private final BoothReviewHelpfulVoteRepository boothReviewHelpfulVoteRepository;
    private final FileAssetRepository fileAssetRepository;

    /**
     * 1. 리뷰 작성
     *    - 티켓 구매자만 작성 가능 (AdmissionTicket 보유 여부로 확인)
     *    - 진행 중인 행사의 부스만 작성 가능 (PUBLISHED 상태 + startAt~endAt 사이)
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

        // 2) 부스 · 행사 조회 및 "진행 중인 행사"인지 확인
        Booth booth = boothRepository.findById(boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.BOOTH_NOT_FOUND));
        Event event = eventRepository.findById(booth.getEventId())
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // EventLifecycleScheduler가 endAt이 지난 PUBLISHED 행사를 매분 ENDED로 전환하므로,
        // 종료 유예기간(7일)을 PUBLISHED만으로 판단하면 종료 1분 뒤부터 사실상 죽은 코드가 된다.
        // 유예기간 안에서는 ENDED 상태도 리뷰 작성 가능한 상태로 인정한다.
        boolean isReviewableStatus = event.getStatus() == EventStatus.PUBLISHED
                || event.getStatus() == EventStatus.ENDED;
        boolean isWritablePeriod = isReviewableStatus
                && !now.isBefore(event.getStartAt())
                && now.isBefore(event.getEndAt().plusDays(REVIEW_WRITABLE_GRACE_PERIOD_DAYS));
        if (!isWritablePeriod) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_EVENT_NOT_ONGOING);
        }

        // 3) 티켓 구매자인지 확인 (해당 행사의 AdmissionTicket 보유 여부)
        if (!admissionTicketRepository.existsByMemberIdAndEventId(memberId, event.getId())) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_TICKET_REQUIRED);
        }

        // 4) 금칙어 필터 — 로컬 문자열 매칭이라 항상 즉시 동작 (1차 방어선)
        if (profanityFilter.containsBannedWord(request.getComment())) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_COMMENT_BLOCKED);
        }

        // 5) AI 기반 악성 판별 — 금칙어 필터를 우회하는 완곡한 욕설/혐오/도배성 스팸을 2차로 거른다.
        //    LLM 장애 시에는 통과시킨다(BoothReviewAiModerationService 내부에서 fail-open 처리).
        if (aiModerationService.isAbusive(request.getComment())) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_COMMENT_FLAGGED_BY_AI);
        }

        // 6) Member 조회 (memberName 저장용)
        Member member = memberRepository.findById(memberId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 7) 리뷰 객체 생성
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
            savePhotos(saved.getId(), memberId, request.getFileIds());
            return toResponse(saved);

        } catch (DataIntegrityViolationException e) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    // 리뷰-파일 연결을 저장한다.
    private void savePhotos(Long reviewId, Long memberId, List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return;
        }

        // null 항목/중복 ID는 booth_review_photos의 NOT NULL·유니크 제약을 그대로 위반해 raw한
        // 영속성 예외로 이어지므로, 여기서 먼저 걸러 명확한 검증 오류로 바꾼다.
        if (fileIds.stream().anyMatch(Objects::isNull)) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        Set<Long> distinctIds = new LinkedHashSet<>(fileIds);
        if (distinctIds.size() != fileIds.size()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }

        // fileId가 실제 존재하는지는 FK가 보장하지만, "내가 업로드한 파일인지"는 별도로 확인해야 한다.
        // 안 그러면 다른 회원의 PRIVATE 파일 ID를 그대로 붙여서 내 리뷰에 연결할 수 있다.
        long ownedCount = fileAssetRepository.countByIdInAndUploadedBy(fileIds, memberId);
        if (ownedCount != fileIds.size()) {
            throw new BusinessException(GlobalErrorCode.FILE_ACCESS_DENIED);
        }

        OffsetDateTime now = OffsetDateTime.now();
        List<BoothReviewPhoto> photos = new ArrayList<>();
        for (int i = 0; i < fileIds.size(); i++) {
            photos.add(BoothReviewPhoto.builder()
                    .boothReviewId(reviewId)
                    .fileId(fileIds.get(i))
                    .sortOrder(i)
                    .createdAt(now)
                    .build());
        }
        boothReviewPhotoRepository.saveAll(photos);
    }

    /**
     * 2. 평균 별점 + 리뷰 개수 조회
     */
    public BoothReviewRatingSummaryResponse getAverageRating(Long boothId) {
        Double average = boothReviewRepository.findAverageRatingByBoothId(boothId).orElse(0.0);
        long count = boothReviewRepository.countByBoothId(boothId);
        return BoothReviewRatingSummaryResponse.builder()
                .averageRating(average)
                .reviewCount(count)
                .build();
    }

    /**
     * 3. 부스별 후기 목록 (WBS-159)
     *
     * @param viewerMemberId 조회하는 회원 ID (비로그인이면 null) — "내가 이미 신고했는지" 배지 계산용
     */
    public Page<BoothReviewResponse> getBoothReviews(
            Long boothId, Pageable pageable, BoothReviewSortOption sort, Long viewerMemberId) {
        Page<BoothReview> reviews = switch (sort == null ? BoothReviewSortOption.LATEST : sort) {
            case RATING_DESC -> boothReviewRepository.findByBoothIdOrderByRatingDesc(boothId, pageable);
            case RATING_ASC -> boothReviewRepository.findByBoothIdOrderByRatingAsc(boothId, pageable);
            case HELPFUL_DESC -> boothReviewRepository.findByBoothIdOrderByHelpfulCountDesc(boothId, pageable);
            case LATEST -> boothReviewRepository.findByBoothIdOrderByCreatedAtDesc(boothId, pageable);
        };
        Booth booth = boothRepository.findById(boothId).orElse(null);
        Event event = booth != null ? eventRepository.findById(booth.getEventId()).orElse(null) : null;
        Map<Long, BoothReviewReplyResponse> repliesByReviewId = repliesByReviewId(reviews.getContent());
        Map<Long, List<BoothReviewPhotoResponse>> photosByReviewId = photosByReviewId(reviews.getContent());
        Set<Long> reportedReviewIds = reportedReviewIdsByViewer(reviews.getContent(), viewerMemberId);
        Map<Long, Long> helpfulCountsByReviewId = helpfulCountsByReviewId(reviews.getContent());
        Set<Long> helpfulReviewIds = helpfulReviewIdsByViewer(reviews.getContent(), viewerMemberId);
        // 비로그인도 조회 가능한 공개 엔드포인트라 다른 사람의 memberId는 노출하지 않는다.
        return reviews.map(review -> toResponse(review, booth, event, false,
                repliesByReviewId.get(review.getId()),
                photosByReviewId.getOrDefault(review.getId(), List.of()),
                reportedReviewIds.contains(review.getId()),
                isMine(review, viewerMemberId),
                helpfulCountsByReviewId.getOrDefault(review.getId(), 0L),
                helpfulReviewIds.contains(review.getId())));
    }

    // memberId를 노출하지 않는 공개 목록에서도 프론트가 "내 리뷰"를 판별할 수 있도록 별도 플래그로 계산한다.
    private boolean isMine(BoothReview review, Long viewerMemberId) {
        return viewerMemberId != null && review.getMemberId().equals(viewerMemberId);
    }

    /**
     * 3-1. Controller에서 호출하는 getReviews() 메서드
     */
    public Page<BoothReviewResponse> getReviews(
            Long boothId, Pageable pageable, BoothReviewSortOption sort, Long viewerMemberId) {
        return getBoothReviews(boothId, pageable, sort, viewerMemberId);
    }

    /**
     * 4. 내 작성 후기 목록 (WBS-160) - 여러 부스에 걸쳐 조회되므로 부스 정보를 배치로 가져온다.
     */
    public Page<BoothReviewResponse> getMyReviews(Long memberId, Pageable pageable) {
        Page<BoothReview> reviews = boothReviewRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
        Map<Long, Booth> boothsById = boothsById(reviews.getContent());
        Map<Long, Event> eventsById = eventsById(boothsById.values());
        Map<Long, BoothReviewReplyResponse> repliesByReviewId = repliesByReviewId(reviews.getContent());
        Map<Long, List<BoothReviewPhotoResponse>> photosByReviewId = photosByReviewId(reviews.getContent());
        Map<Long, Long> helpfulCountsByReviewId = helpfulCountsByReviewId(reviews.getContent());
        return reviews.map(review -> {
            Booth booth = boothsById.get(review.getBoothId());
            Event event = booth != null ? eventsById.get(booth.getEventId()) : null;
            // 로그인한 본인의 후기 목록이라 본인 memberId 노출은 안전하다. 본인 리뷰는 자기 자신을
            // 신고하거나 "도움이 돼요"를 누를 수 없으니 reportedByMe/helpfulByMe는 항상 false, mine은 항상 true.
            return toResponse(review, booth, event, true,
                    repliesByReviewId.get(review.getId()),
                    photosByReviewId.getOrDefault(review.getId(), List.of()),
                    false, true,
                    helpfulCountsByReviewId.getOrDefault(review.getId(), 0L), false);
        });
    }

    /**
     * 5. 부스별 리뷰 검색
     *
     * @param viewerMemberId 조회하는 회원 ID (비로그인이면 null) — "내가 이미 신고했는지" 배지 계산용
     */
    public Page<BoothReviewResponse> searchReviews(
            Long boothId, String keyword, Pageable pageable, Long viewerMemberId) {
        if (keyword == null || keyword.isBlank()) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        Page<BoothReview> reviews = boothReviewRepository
                .findByBoothIdAndCommentContainingIgnoreCase(boothId, keyword, pageable);
        Booth booth = boothRepository.findById(boothId).orElse(null);
        Event event = booth != null ? eventRepository.findById(booth.getEventId()).orElse(null) : null;
        Map<Long, BoothReviewReplyResponse> repliesByReviewId = repliesByReviewId(reviews.getContent());
        Map<Long, List<BoothReviewPhotoResponse>> photosByReviewId = photosByReviewId(reviews.getContent());
        Set<Long> reportedReviewIds = reportedReviewIdsByViewer(reviews.getContent(), viewerMemberId);
        Map<Long, Long> helpfulCountsByReviewId = helpfulCountsByReviewId(reviews.getContent());
        Set<Long> helpfulReviewIds = helpfulReviewIdsByViewer(reviews.getContent(), viewerMemberId);
        // 비로그인도 조회 가능한 공개 엔드포인트라 다른 사람의 memberId는 노출하지 않는다.
        return reviews.map(review -> toResponse(review, booth, event, false,
                repliesByReviewId.get(review.getId()),
                photosByReviewId.getOrDefault(review.getId(), List.of()),
                reportedReviewIds.contains(review.getId()),
                isMine(review, viewerMemberId),
                helpfulCountsByReviewId.getOrDefault(review.getId(), 0L),
                helpfulReviewIds.contains(review.getId())));
    }

    // 페이지 안 리뷰 ID들 중 이 회원이 이미 신고한 것만 배치로 조회한다 (N+1 방지). 비로그인이면 전부 false.
    private Set<Long> reportedReviewIdsByViewer(List<BoothReview> reviews, Long viewerMemberId) {
        if (viewerMemberId == null) {
            return Set.of();
        }
        List<Long> reviewIds = reviews.stream().map(BoothReview::getId).toList();
        if (reviewIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(boothReviewReportRepository
                .findBoothReviewIdByReporterMemberIdAndBoothReviewIdIn(viewerMemberId, reviewIds));
    }

    // 페이지 안 리뷰 ID들의 "도움이 돼요" 개수를 배치로 조회한다 (N+1 방지).
    private Map<Long, Long> helpfulCountsByReviewId(List<BoothReview> reviews) {
        List<Long> reviewIds = reviews.stream().map(BoothReview::getId).toList();
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        return boothReviewHelpfulVoteRepository.countsByBoothReviewIdIn(reviewIds).stream()
                .collect(Collectors.toMap(row -> (Long) row[0], row -> (Long) row[1]));
    }

    // 페이지 안 리뷰 ID들 중 이 회원이 이미 "도움이 돼요"를 누른 것만 배치로 조회한다. 비로그인이면 전부 false.
    private Set<Long> helpfulReviewIdsByViewer(List<BoothReview> reviews, Long viewerMemberId) {
        if (viewerMemberId == null) {
            return Set.of();
        }
        List<Long> reviewIds = reviews.stream().map(BoothReview::getId).toList();
        if (reviewIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(boothReviewHelpfulVoteRepository
                .findBoothReviewIdByMemberIdAndBoothReviewIdIn(viewerMemberId, reviewIds));
    }

    private Map<Long, Booth> boothsById(List<BoothReview> reviews) {
        List<Long> boothIds = reviews.stream().map(BoothReview::getBoothId).distinct().toList();
        return boothRepository.findAllById(boothIds).stream()
                .collect(Collectors.toMap(Booth::getId, Function.identity()));
    }

    private Map<Long, Event> eventsById(Collection<Booth> booths) {
        List<Long> eventIds = booths.stream().map(Booth::getEventId).distinct().toList();
        return eventRepository.findAllById(eventIds).stream()
                .collect(Collectors.toMap(Event::getId, Function.identity()));
    }

    // 페이지 안 리뷰 ID들의 답글을 쿼리 1번으로 배치 조회한다 (N+1 방지).
    private Map<Long, BoothReviewReplyResponse> repliesByReviewId(List<BoothReview> reviews) {
        List<Long> reviewIds = reviews.stream().map(BoothReview::getId).toList();
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        return boothReviewReplyRepository.findByBoothReviewIdIn(reviewIds).stream()
                .collect(Collectors.toMap(BoothReviewReply::getBoothReviewId, this::toReplyResponse));
    }

    private BoothReviewReplyResponse toReplyResponse(BoothReviewReply reply) {
        return BoothReviewReplyResponse.builder()
                .id(reply.getId())
                .content(reply.getContent())
                .createdAt(reply.getCreatedAt())
                .updatedAt(reply.getUpdatedAt())
                .build();
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

        // 4) 금칙어 필터 + AI 악성 판별 — 작성 시와 동일하게 수정 시에도 적용한다.
        //    이 검사가 없으면 깨끗한 리뷰로 작성 검증을 통과시킨 뒤 수정으로 우회해 악성 내용을
        //    채워 넣을 수 있다.
        if (profanityFilter.containsBannedWord(request.getContent())) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_COMMENT_BLOCKED);
        }
        if (aiModerationService.isAbusive(request.getContent())) {
            throw new BusinessException(GlobalErrorCode.BOOTH_REVIEW_COMMENT_FLAGGED_BY_AI);
        }

        // 5) 리뷰 정보 업데이트
        review.updateRating(request.getRating());
        review.updateComment(request.getContent());
        review.updateUpdatedAt(OffsetDateTime.now());

        // 6) DB 저장
        boothReviewRepository.saveAndFlush(review);

        // 7) 사진 교체 — fileIds가 null이면 기존 사진을 그대로 두고, 값이 오면(빈 배열 포함) 통째로 교체한다.
        if (request.getFileIds() != null) {
            boothReviewPhotoRepository.deleteByBoothReviewId(review.getId());
            savePhotos(review.getId(), memberId, request.getFileIds());
        }

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
     * BoothReview → BoothReviewResponse 변환 (부스·행사 단건 조회를 곁들인다)
     */
    private BoothReviewResponse toResponse(BoothReview review) {
        Booth booth = boothRepository.findById(review.getBoothId()).orElse(null);
        Event event = booth != null ? eventRepository.findById(booth.getEventId()).orElse(null) : null;
        BoothReviewReplyResponse reply = boothReviewReplyRepository.findByBoothReviewId(review.getId())
                .map(this::toReplyResponse)
                .orElse(null);
        List<BoothReviewPhotoResponse> photos = boothReviewPhotoRepository
                .findByBoothReviewIdOrderBySortOrderAsc(review.getId()).stream()
                .map(this::toPhotoResponse)
                .toList();
        // 작성/수정 직후 본인에게 돌려주는 응답이라 본인 memberId 노출은 안전하다.
        // 방금 작성/수정한 자기 리뷰이니 reportedByMe/helpfulByMe는 항상 false, mine은 항상 true.
        long helpfulCount = boothReviewHelpfulVoteRepository.countByBoothReviewId(review.getId());
        return toResponse(review, booth, event, true, reply, photos, false, true, helpfulCount, false);
    }

    /**
     * BoothReview → BoothReviewResponse 변환 (목록 조회 시 배치로 조회해둔 booth/event를 재사용한다)
     *
     * @param exposeMemberId 비로그인도 볼 수 있는 공개 목록(부스별 후기)에서는 false로 넘겨
     *                       다른 회원의 memberId가 노출되지 않도록 한다.
     * @param reportedByMe   조회하는 회원이 이 리뷰를 이미 신고했는지
     * @param mine           조회하는 회원 본인이 작성한 리뷰인지 (memberId를 못 내려주는 공개 목록에서도
     *                       프론트가 이 값으로 "내 리뷰"를 판별할 수 있다)
     * @param helpfulCount   "도움이 돼요" 누적 수
     * @param helpfulByMe    조회하는 회원이 이미 "도움이 돼요"를 눌렀는지
     */
    private BoothReviewResponse toResponse(
            BoothReview review, Booth booth, Event event, boolean exposeMemberId,
            BoothReviewReplyResponse reply, List<BoothReviewPhotoResponse> photos, boolean reportedByMe,
            boolean mine, long helpfulCount, boolean helpfulByMe) {
        return BoothReviewResponse.builder()
                .id(review.getId())
                .boothId(review.getBoothId())
                .eventId(booth != null ? booth.getEventId() : null)
                .eventName(event != null ? event.getName() : null)
                .boothDisplayName(booth != null ? booth.getDisplayName() : null)
                .boothCode(booth != null ? booth.getBoothCode() : null)
                .memberId(exposeMemberId ? review.getMemberId() : null)
                .memberName(review.getMemberName())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .hidden(review.isHidden())
                .hiddenReason(review.getHiddenReason())
                .reportedByMe(reportedByMe)
                .mine(mine)
                .reply(reply)
                .photos(photos)
                .helpfulCount(helpfulCount)
                .helpfulByMe(helpfulByMe)
                .trustedReview(helpfulCount >= TRUSTED_REVIEW_HELPFUL_THRESHOLD)
                .build();
    }

    private BoothReviewPhotoResponse toPhotoResponse(BoothReviewPhoto photo) {
        return BoothReviewPhotoResponse.builder()
                .fileId(photo.getFileId())
                .sortOrder(photo.getSortOrder())
                .build();
    }

    // 페이지 안 리뷰 ID들의 사진을 쿼리 1번으로 배치 조회한다 (N+1 방지).
    private Map<Long, List<BoothReviewPhotoResponse>> photosByReviewId(List<BoothReview> reviews) {
        List<Long> reviewIds = reviews.stream().map(BoothReview::getId).toList();
        if (reviewIds.isEmpty()) {
            return Map.of();
        }
        return boothReviewPhotoRepository.findByBoothReviewIdInOrderBySortOrderAsc(reviewIds).stream()
                .collect(Collectors.groupingBy(
                        BoothReviewPhoto::getBoothReviewId,
                        Collectors.mapping(this::toPhotoResponse, Collectors.toList())));
    }
}