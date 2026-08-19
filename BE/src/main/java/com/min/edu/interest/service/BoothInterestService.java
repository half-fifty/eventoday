package com.min.edu.interest.service;

import com.min.edu.booth.domain.BoothInterest;
import com.min.edu.booth.repository.BoothQrScanRepository;
import com.min.edu.booth.repository.BoothReservationSlotRepository;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.interest.dto.InterestBoothResponse;
import com.min.edu.interest.repository.BoothInterestRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothInterestService {

    // VenueMapCongestionService와 동일한 기준(최근 10분 QR 스캔 수)을 쓴다.
    private static final int CONGESTION_WINDOW_MINUTES = 10;
    private static final long CONGESTION_HIGH_THRESHOLD = 20;
    private static final long CONGESTION_MEDIUM_THRESHOLD = 10;

    private final BoothInterestRepository boothInterestRepository;
    private final BoothReviewRepository boothReviewRepository;
    private final BoothReservationSlotRepository boothReservationSlotRepository;
    private final BoothQrScanRepository boothQrScanRepository;

    public void register(Long memberId, Long boothId) {
        BoothInterest interest = boothInterestRepository.findByMemberIdAndBoothId(memberId, boothId)
                .orElse(BoothInterest.builder()
                        .memberId(memberId)
                        .boothId(boothId)
                        .vacancyNotificationEnabled(false)
                        .createdAt(OffsetDateTime.now())
                        .build());

        boothInterestRepository.saveAndFlush(interest);
    }

    public void remove(Long memberId, Long boothId) {
        boothInterestRepository.findByMemberIdAndBoothId(memberId, boothId)
                .ifPresent(boothInterestRepository::delete);
    }

    /**
     * 관심 부스 목록. 이름/소개뿐 아니라 평점·후기수·예약 가능 여부·실시간 혼잡도까지 같이 내려줘서,
     * "관심 등록해둔 부스, 지금 가도 되나?"를 이 화면 하나로 판단할 수 있게 한다.
     */
    @Transactional(readOnly = true)
    public List<InterestBoothResponse> getMyInterests(Long memberId) {
        List<Object[]> rows = boothInterestRepository.findInterestBoothsByMemberId(memberId);
        if (rows.isEmpty()) {
            return List.of();
        }

        Set<Long> boothIds = rows.stream().map(row -> (Long) row[0]).collect(Collectors.toSet());

        // 평점/후기수는 부스가 여러 개여도 쿼리 1번씩으로 끝나도록 배치 조회 (모바일 안내와 동일한 패턴)
        Map<Long, Double> ratingMap = boothReviewRepository.findAverageRatingsByBoothIds(boothIds).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> (Double) r[1]));
        Map<Long, Long> reviewCountMap = boothReviewRepository.findReviewCountsByBoothIds(boothIds).stream()
                .collect(Collectors.toMap(r -> ((Number) r[0]).longValue(), r -> ((Number) r[1]).longValue()));

        // 예약 가능 여부·혼잡도는 부스별로 개별 조회한다. 관심 부스 목록은 한 사용자당 많아야 수십 개
        // 규모라 배치로 묶을 만큼의 이점이 없고, 혼잡도는 애초에 "최근 10분" 실시간 집계라 배치화하기도 까다롭다.
        OffsetDateTime since = OffsetDateTime.now().minusMinutes(CONGESTION_WINDOW_MINUTES);

        return rows.stream()
                .map(row -> {
                    Long boothId = (Long) row[0];
                    Long eventId = (Long) row[1];
                    String displayName = (String) row[2];
                    String shortIntro = (String) row[3];
                    Boolean vacancyNotificationEnabled = (Boolean) row[4];

                    boolean hasAvailableSlots = boothReservationSlotRepository.existsByBoothIdAndAvailableSlots(boothId);
                    long recentScans = boothQrScanRepository.countByBoothIdAndScannedAtAfter(boothId, since);

                    return InterestBoothResponse.builder()
                            .boothId(boothId)
                            .eventId(eventId)
                            .displayName(displayName)
                            .shortIntro(shortIntro)
                            .vacancyNotificationEnabled(vacancyNotificationEnabled)
                            .averageRating(ratingMap.get(boothId))
                            .reviewCount(reviewCountMap.getOrDefault(boothId, 0L))
                            .hasAvailableSlots(hasAvailableSlots)
                            .congestionLevel(congestionLevel(recentScans))
                            .build();
                })
                .toList();
    }

    private String congestionLevel(long recentScans) {
        if (recentScans >= CONGESTION_HIGH_THRESHOLD) {
            return "HIGH";
        }
        if (recentScans >= CONGESTION_MEDIUM_THRESHOLD) {
            return "MEDIUM";
        }
        return "LOW";
    }

    // 관심 등록한 부스에 대해 빈자리 알림 수신 여부를 켜고 끈다. 관심 등록 자체가 안 돼있으면 실패.
    public void updateVacancyNotification(Long memberId, Long boothId, boolean enabled) {
        BoothInterest interest = boothInterestRepository.findByMemberIdAndBoothId(memberId, boothId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        interest.updateVacancyNotificationEnabled(enabled);
        boothInterestRepository.saveAndFlush(interest);
    }
}
