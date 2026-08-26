package com.min.edu.booth.service;

import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.dto.MobileGuideBoothDetailResponse;
import com.min.edu.booth.dto.MobileGuideBoothListResponse;
import com.min.edu.booth.dto.MobileGuideMainResponse;
import com.min.edu.interest.repository.BoothInterestRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReservationSlotRepository;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileGuideService {

    private final BoothRepository boothRepository;
    private final EventRepository eventRepository;
    private final BoothReviewRepository boothReviewRepository;
    private final BoothInterestRepository boothInterestRepository;
    private final BoothReservationSlotRepository reservationSlotRepository;

    /**
     * 1. 모바일 안내 메인 데이터
     */
    public MobileGuideMainResponse getGuideMain(Long eventId, Long memberId) {
        // 1) 행사 조회
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2) 부스 조회
        // (1) 행사의 모든 부스 조회
        List<Booth> booths = boothRepository.findByEventId(eventId);

        // 3) 부스 ID 수집 (Batch 쿼리용)
        Set<Long> boothIds = booths.stream()
                .map(Booth::getId)
                .collect(Collectors.toSet());

        // 4) Batch 쿼리 - 평점
        // (1) 모든 부스의 평점을 한 번에 조회
        List<Object[]> averageRatings = boothReviewRepository.findAverageRatingsByBoothIds(boothIds);
        Map<Long, Double> ratingMap = new HashMap<>();
        for (Object[] row : averageRatings) {
            Long boothId = ((Number) row[0]).longValue();
            Double rating = (Double) row[1];
            ratingMap.put(boothId, rating);
        }

        // 5) Batch 쿼리 - 후기 수
        // (1) 모든 부스의 후기 수를 한 번에 조회
        List<Object[]> reviewCounts = boothReviewRepository.findReviewCountsByBoothIds(boothIds);
        Map<Long, Long> countMap = new HashMap<>();
        for (Object[] row : reviewCounts) {
            Long boothId = ((Number) row[0]).longValue();
            Long count = ((Number) row[1]).longValue();
            countMap.put(boothId, count);
        }

        // 6) Batch 쿼리 - 관심 부스 (로그인한 경우만)
        Map<Long, Boolean> interestMap = new HashMap<>();
        if (memberId != null) {
            // (1) 사용자가 관심 표시한 부스 ID들을 한 번에 조회
            Set<Long> interestedBoothIds = boothInterestRepository.findBoothIdsByMemberId(memberId);
            for (Long boothId : boothIds) {
                interestMap.put(boothId, interestedBoothIds.contains(boothId));
            }
        } else {
            // (2) 비로그인은 모두 false
            for (Long boothId : boothIds) {
                interestMap.put(boothId, false);
            }
        }

        // 7) 응답 생성
        List<MobileGuideBoothListResponse> boothList = booths.stream()
                .map(booth -> toGuideBoothListResponse(
                        booth,
                        ratingMap.getOrDefault(booth.getId(), null),
                        countMap.getOrDefault(booth.getId(), 0L),
                        interestMap.getOrDefault(booth.getId(), false)
                ))
                .toList();

        return MobileGuideMainResponse.builder()
                .eventId(eventId)
                .eventName(event.getName())
                .eventStartDate(event.getStartAt())
                .eventEndDate(event.getEndAt())
                .location(event.getVenueName())
                .totalBooths(booths.size())
                .booths(boothList)
                .build();
    }

    /**
     * 2. 모바일 부스 검색 (Pageable 지원)
     */
    public Page<MobileGuideBoothListResponse> searchBooths(
            Long eventId,
            String keyword,
            Long memberId,
            Pageable pageable) {

        // 1) 부스 검색 (페이지)
        Page<Booth> boothPage = boothRepository.findByEventIdAndDisplayNameContainingIgnoreCase(
                eventId, keyword, pageable);

        // 2) 부스 ID 수집
        Set<Long> boothIds = boothPage.getContent().stream()
                .map(Booth::getId)
                .collect(Collectors.toSet());

        // 3) Batch 쿼리 - 평점
        // (1) 페이지 내 부스의 평점을 한 번에 조회
        List<Object[]> averageRatings = boothReviewRepository.findAverageRatingsByBoothIds(boothIds);
        Map<Long, Double> ratingMap = new HashMap<>();
        for (Object[] row : averageRatings) {
            Long boothId = ((Number) row[0]).longValue();
            Double rating = (Double) row[1];
            ratingMap.put(boothId, rating);
        }

        // 4) Batch 쿼리 - 후기 수
        // (1) 페이지 내 부스의 후기 수를 한 번에 조회
        List<Object[]> reviewCounts = boothReviewRepository.findReviewCountsByBoothIds(boothIds);
        Map<Long, Long> countMap = new HashMap<>();
        for (Object[] row : reviewCounts) {
            Long boothId = ((Number) row[0]).longValue();
            Long count = ((Number) row[1]).longValue();
            countMap.put(boothId, count);
        }

        // 5) Batch 쿼리 - 관심 부스
        Map<Long, Boolean> interestMap = new HashMap<>();
        if (memberId != null) {
            Set<Long> interestedBoothIds = boothInterestRepository.findBoothIdsByMemberId(memberId);
            for (Long boothId : boothIds) {
                interestMap.put(boothId, interestedBoothIds.contains(boothId));
            }
        } else {
            for (Long boothId : boothIds) {
                interestMap.put(boothId, false);
            }
        }

        // 6) 응답 변환
        List<MobileGuideBoothListResponse> content = boothPage.getContent().stream()
                .map(booth -> toGuideBoothListResponse(
                        booth,
                        ratingMap.getOrDefault(booth.getId(), null),
                        countMap.getOrDefault(booth.getId(), 0L),
                        interestMap.getOrDefault(booth.getId(), false)
                ))
                .toList();

        return new PageImpl<>(content, pageable, boothPage.getTotalElements());
    }

    /**
     * 3. 모바일 부스 상세
     */
    public MobileGuideBoothDetailResponse getBoothDetail(Long eventId, Long boothId, Long memberId) {
        // 1) 부스 조회
        // (1) 부스 존재 & 행사 소속 확인
        Booth booth = boothRepository.findByIdAndEventId(boothId, eventId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        // 2) 관심도 조회
        // (1) 로그인한 경우만 조회
        Boolean isInterested = memberId != null
                ? boothInterestRepository.existsByMemberIdAndBoothId(memberId, boothId)
                : false;

        // 3) 평점 조회
        Double averageRating = boothReviewRepository.findAverageRatingByBoothId(boothId)
                .orElse(null);

        // 4) 후기 수 조회
        long reviewCount = boothReviewRepository.countByBoothId(boothId);

        // 5) 예약 가능 슬롯 확인
        boolean hasAvailableSlots = hasAvailableSlots(boothId);

        // 6) 응답 생성
        return MobileGuideBoothDetailResponse.builder()
                .boothId(boothId)
                .boothCode(booth.getBoothCode())
                .displayName(booth.getDisplayName())
                .shortIntro(booth.getShortIntro())
                .description(booth.getDescription())
                .boothType(booth.getBoothType())
                .location(booth.getLocationDescription() != null
                        ? booth.getLocationDescription()
                        : booth.getZoneName())
                .representativeFileId(booth.getRepresentativeFileId())
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .isInterested(isInterested)
                .hasAvailableSlots(hasAvailableSlots)
                .build();
    }

    /**
     * Booth → MobileGuideBoothListResponse 변환
     * - Preloaded 값 사용 (쿼리 없음)
     */
    private MobileGuideBoothListResponse toGuideBoothListResponse(
            Booth booth,
            Double averageRating,
            Long reviewCount,
            Boolean isInterested) {

        return MobileGuideBoothListResponse.builder()
                .boothId(booth.getId())
                .boothCode(booth.getBoothCode())
                .displayName(booth.getDisplayName())
                .shortIntro(booth.getShortIntro())
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .isInterested(isInterested)
                .build();
    }

    /**
     * 부스의 예약 가능 슬롯 확인
     */
    private boolean hasAvailableSlots(Long boothId) {
        // 1) 부스의 OPEN 상태 슬롯 중 자리가 있는지 확인
        return reservationSlotRepository.existsByBoothIdAndAvailableSlots(boothId);
    }
}