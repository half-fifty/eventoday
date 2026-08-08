package com.min.edu.booth.service;

import com.min.edu.booth.domain.Booth;
import com.min.edu.booth.dto.MobileGuideBoothDetailResponse;
import com.min.edu.booth.dto.MobileGuideBoothListResponse;
import com.min.edu.booth.dto.MobileGuideMainResponse;
import com.min.edu.interest.repository.BoothInterestRepository;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.domain.Event;
import com.min.edu.event.repository.EventRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class MobileGuideService {

    private final BoothRepository boothRepository;
    private final EventRepository eventRepository;
    private final BoothReviewRepository boothReviewRepository;
    private final BoothInterestRepository boothInterestRepository;

    // 1. 모바일 안내 메인 데이터
    public MobileGuideMainResponse getGuideMain(Long eventId) {
        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        List<Booth> booths = boothRepository.findByEventId(eventId);

        List<MobileGuideBoothListResponse> boothList = booths.stream()
                .map(this::toGuideBoothListResponse)
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

    // 2. 모바일 부스 검색·목록 (검색어 기반)
    public List<MobileGuideBoothListResponse> searchBooths(Long eventId, String keyword, Long memberId) {
        List<Booth> booths = boothRepository.findByEventIdAndDisplayNameContainingIgnoreCase(eventId, keyword);

        return booths.stream()
                .map(booth -> toGuideBoothListResponse(booth, memberId))
                .toList();
    }

    // 3. 모바일 부스 상세
    public MobileGuideBoothDetailResponse getBoothDetail(Long eventId, Long boothId, Long memberId) {
        Booth booth = boothRepository.findByIdAndEventId(boothId, eventId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));

        Boolean isInterested = memberId != null
                ? boothInterestRepository.existsByMemberIdAndBoothId(memberId, boothId)
                : false;

        Double averageRating = boothReviewRepository.findAverageRatingByBoothId(boothId)
                .orElse(null);
        long reviewCount = boothReviewRepository.countByBoothId(boothId);

        return MobileGuideBoothDetailResponse.builder()
                .boothId(boothId)
                .boothCode(booth.getBoothCode())
                .displayName(booth.getDisplayName())
                .shortIntro(booth.getShortIntro())
                .description(booth.getDescription())
                .boothType(booth.getBoothType())
                .location(booth.getLocationDescription() != null ? booth.getLocationDescription() : booth.getZoneName())
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .isInterested(isInterested)
                .hasAvailableSlots(hasAvailableSlots(boothId))
                .build();
    }

    private MobileGuideBoothListResponse toGuideBoothListResponse(Booth booth) {
        Double averageRating = boothReviewRepository.findAverageRatingByBoothId(booth.getId())
                .orElse(null);
        long reviewCount = boothReviewRepository.countByBoothId(booth.getId());

        return MobileGuideBoothListResponse.builder()
                .boothId(booth.getId())
                .boothCode(booth.getBoothCode())
                .displayName(booth.getDisplayName())
                .shortIntro(booth.getShortIntro())
                .averageRating(averageRating)
                .reviewCount(reviewCount)
                .isInterested(false)
                .build();
    }

    private MobileGuideBoothListResponse toGuideBoothListResponse(Booth booth, Long memberId) {
        Double averageRating = boothReviewRepository.findAverageRatingByBoothId(booth.getId())
                .orElse(null);
        long reviewCount = boothReviewRepository.countByBoothId(booth.getId());

        Boolean isInterested = memberId != null
                ? boothInterestRepository.existsByMemberIdAndBoothId(memberId, booth.getId())
                : false;

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

    private boolean hasAvailableSlots(Long boothId) {
        // 실제로는 예약 가능한 슬롯이 있는지 확인
        // 지금은 true로 반환
        return true;
    }
}