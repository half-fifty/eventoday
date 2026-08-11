package com.min.edu.booth.service;

import com.min.edu.booth.dto.RecommendedBoothsResponse;
import com.min.edu.booth.repository.BoothQrScanRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class BoothRecommendationService {

    private final BoothQrScanRepository qrScanRepository;
    private static final int CONGESTION_THRESHOLD = 3;  // 혼잡한 부스 TOP 3

    /**
     * WBS-163: 혼잡도 기반 부스 추천
     * 1. 혼잡한 부스 파악 (상위 3개, INNER JOIN)
     * 2. 추천 부스 조회 (LEFT JOIN, 혼잡 부수는 DB에서 제외)
     * 3. in-memory 필터링 제거 → DB에서 직접 제외
     * 4. 추천 이유 포함
     */
    public RecommendedBoothsResponse getRecommendedBooths(Long eventId, Pageable pageable) {
        OffsetDateTime since = OffsetDateTime.now().minusMinutes(10);

        // 1️⃣ 혼잡한 부스 TOP 3 조회 (INNER JOIN, 고정)
        Pageable congestedPageable = PageRequest.of(0, CONGESTION_THRESHOLD);
        var congestedPage = qrScanRepository.findPopularBooths(eventId, since, congestedPageable);

        List<RecommendedBoothsResponse.CongestionBooth> congestedBooths = new ArrayList<>();
        Set<Long> congestedBoothIds = new HashSet<>();

        // 혼잡한 부스 정보 수집
        for (Object[] row : congestedPage.getContent()) {
            Long boothId = ((Number) row[0]).longValue();
            long congestionCount = ((Number) row[1]).longValue();

            congestedBooths.add(RecommendedBoothsResponse.CongestionBooth.builder()
                    .boothId(boothId)
                    .congestionCount(congestionCount)
                    .build());
            congestedBoothIds.add(boothId);
        }

        // 2️⃣ 추천 부스 조회 (항상 findRecommendedBooths 사용!)
        List<RecommendedBoothsResponse.RecommendedBooth> recommendedBooths = new ArrayList<>();
        int rank = 1;

        // LEFT JOIN으로 모든 부수 조회, 혼잡한 부수는 DB에서 제외
        var recommendedPage = qrScanRepository.findRecommendedBooths(
                eventId,
                since,
                congestedBoothIds,  // ← DB NOT IN 절에서 제외
                pageable
        );

        // 3️⃣ 추천 부수 (혼잡한 부수는 이미 DB에서 제외됨)
        for (Object[] row : recommendedPage.getContent()) {
            Long boothId = ((Number) row[0]).longValue();
            long congestionCount = ((Number) row[1]).longValue();

            recommendedBooths.add(RecommendedBoothsResponse.RecommendedBooth.builder()
                    .boothId(boothId)
                    .congestionCount(congestionCount)
                    .rank(rank++)  // ← 가장 한산한 부수부터 rank 1
                    .build());
        }

        // 4️⃣ 추천 메시지 생성
        String recommendation = buildRecommendationMessage(congestedBooths, recommendedBooths);

        return RecommendedBoothsResponse.builder()
                .recommendedBooths(recommendedBooths)
                .congestedBooths(congestedBooths)
                .recommendation(recommendation)
                .build();
    }

    /**
     * 추천 메시지 생성
     */
    private String buildRecommendationMessage(
            List<RecommendedBoothsResponse.CongestionBooth> congestedBooths,
            List<RecommendedBoothsResponse.RecommendedBooth> recommendedBooths) {

        if (congestedBooths.isEmpty()) {
            return "현재 모든 부수가 한산합니다. 편안하게 방문해주세요.";
        }

        // 혼잡한 부수 ID 목록
        String congestedList = congestedBooths.stream()
                .map(b -> b.getBoothId().toString())
                .collect(Collectors.joining(", "));

        // 추천 부수가 없으면 혼잡도 메시지만 반환
        if (recommendedBooths.isEmpty()) {
            return String.format(
                    "%s 부수가 지금 이용객들이 많이 몰려있어 혼잡합니다.",
                    congestedList
            );
        }

        // 추천 부수 ID 목록 (상위 3개)
        String recommendedList = recommendedBooths.stream()
                .limit(3)
                .map(b -> b.getBoothId().toString())
                .collect(Collectors.joining(", "));

        return String.format(
                "%s 부수가 지금 이용객들이 많이 몰려있어 혼잡합니다. " +
                        "비교적 한산한 %s 부수라인을 추천드립니다.",
                congestedList,
                recommendedList
        );
    }
}