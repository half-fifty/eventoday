package com.min.edu.booth.service;

import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.domain.BoothReview;
import com.min.edu.booth.dto.BoothReviewResponse;
import com.min.edu.booth.dto.CreateBoothReviewRequest;
import com.min.edu.booth.dto.UpdateBoothReviewRequest;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.Member;
import com.min.edu.member.repository.MemberRepository;
import java.util.Collection;
import java.util.List;
import java.util.Map;
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

    private final BoothReviewRepository boothReviewRepository;
    private final MemberRepository memberRepository;
    private final BoothRepository boothRepository;
    private final EventRepository eventRepository;

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
        Booth booth = boothRepository.findById(boothId).orElse(null);
        Event event = booth != null ? eventRepository.findById(booth.getEventId()).orElse(null) : null;
        return reviews.map(review -> toResponse(review, booth, event));
    }

    /**
     * 3-1. Controller에서 호출하는 getReviews() 메서드
     */
    public Page<BoothReviewResponse> getReviews(Long boothId, Pageable pageable) {
        return getBoothReviews(boothId, pageable);
    }

    /**
     * 4. 내 작성 후기 목록 (WBS-160) - 여러 부스에 걸쳐 조회되므로 부스 정보를 배치로 가져온다.
     */
    public Page<BoothReviewResponse> getMyReviews(Long memberId, Pageable pageable) {
        Page<BoothReview> reviews = boothReviewRepository.findByMemberIdOrderByCreatedAtDesc(memberId, pageable);
        Map<Long, Booth> boothsById = boothsById(reviews.getContent());
        Map<Long, Event> eventsById = eventsById(boothsById.values());
        return reviews.map(review -> {
            Booth booth = boothsById.get(review.getBoothId());
            Event event = booth != null ? eventsById.get(booth.getEventId()) : null;
            return toResponse(review, booth, event);
        });
    }

    /**
     * 5. 부스별 리뷰 검색
     */
    public Page<BoothReviewResponse> searchReviews(
            Long boothId, String keyword, Pageable pageable) {
        Page<BoothReview> reviews = boothReviewRepository
                .findByBoothIdAndCommentContainingIgnoreCase(boothId, keyword, pageable);
        Booth booth = boothRepository.findById(boothId).orElse(null);
        Event event = booth != null ? eventRepository.findById(booth.getEventId()).orElse(null) : null;
        return reviews.map(review -> toResponse(review, booth, event));
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
     * BoothReview → BoothReviewResponse 변환 (부스·행사 단건 조회를 곁들인다)
     */
    private BoothReviewResponse toResponse(BoothReview review) {
        Booth booth = boothRepository.findById(review.getBoothId()).orElse(null);
        Event event = booth != null ? eventRepository.findById(booth.getEventId()).orElse(null) : null;
        return toResponse(review, booth, event);
    }

    /**
     * BoothReview → BoothReviewResponse 변환 (목록 조회 시 배치로 조회해둔 booth/event를 재사용한다)
     */
    private BoothReviewResponse toResponse(BoothReview review, Booth booth, Event event) {
        return BoothReviewResponse.builder()
                .id(review.getId())
                .boothId(review.getBoothId())
                .eventId(booth != null ? booth.getEventId() : null)
                .eventName(event != null ? event.getName() : null)
                .boothDisplayName(booth != null ? booth.getDisplayName() : null)
                .boothCode(booth != null ? booth.getBoothCode() : null)
                .memberId(review.getMemberId())
                .memberName(review.getMemberName())
                .rating(review.getRating())
                .comment(review.getComment())
                .createdAt(review.getCreatedAt())
                .updatedAt(review.getUpdatedAt())
                .build();
    }
}