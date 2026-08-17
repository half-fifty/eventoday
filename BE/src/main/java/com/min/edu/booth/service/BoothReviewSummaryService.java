package com.min.edu.booth.service;

import com.min.edu.booth.ai.OpenAiChatClient;
import com.min.edu.booth.domain.BoothReview;
import com.min.edu.booth.domain.BoothReviewSummary;
import com.min.edu.booth.dto.BoothReviewSummaryResponse;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.booth.repository.BoothReviewSummaryRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class BoothReviewSummaryService {

    private static final int MIN_REVIEWS_FOR_SUMMARY = 3;
    private static final int MAX_REVIEWS_FOR_PROMPT = 50;

    private final BoothReviewRepository boothReviewRepository;
    private final BoothReviewSummaryRepository boothReviewSummaryRepository;
    private final BoothRepository boothRepository;
    private final OpenAiChatClient openAiChatClient;

    public BoothReviewSummaryResponse getSummary(Long boothId) {
        if (!boothRepository.existsById(boothId)) {
            throw new BusinessException(GlobalErrorCode.BOOTH_NOT_FOUND);
        }

        long commentedCount = boothReviewRepository.countByBoothIdAndCommentIsNotBlank(boothId);
        Double ratingAverage = boothReviewRepository.findAverageRatingByBoothId(boothId).orElse(null);
        int totalReviewCount = (int) boothReviewRepository.countByBoothId(boothId);

        if (commentedCount < MIN_REVIEWS_FOR_SUMMARY) {
            return BoothReviewSummaryResponse.builder()
                .boothId(boothId)
                .available(false)
                .reviewCount(totalReviewCount)
                .ratingAverage(ratingAverage)
                .build();
        }

        Optional<BoothReviewSummary> cached = boothReviewSummaryRepository.findById(boothId);
        if (cached.isPresent() && cached.get().getReviewCountAtSummary() == commentedCount) {
            return toResponse(cached.get(), totalReviewCount, ratingAverage, false);
        }

        try {
            String summary = generateSummary(boothId, ratingAverage);
            BoothReviewSummary saved = upsert(boothId, cached.orElse(null), summary, (int) commentedCount);
            return toResponse(saved, totalReviewCount, ratingAverage, false);
        } catch (BusinessException exception) {
            if (cached.isPresent()) {
                return toResponse(cached.get(), totalReviewCount, ratingAverage, true);
            }
            throw exception;
        }
    }

    private String generateSummary(Long boothId, Double ratingAverage) {
        List<BoothReview> reviews = boothReviewRepository.findRecentCommentedReviews(
            boothId, PageRequest.of(0, MAX_REVIEWS_FOR_PROMPT));
        String prompt = buildPrompt(reviews, ratingAverage);
        return openAiChatClient.summarize(prompt);
    }

    private String buildPrompt(List<BoothReview> reviews, Double ratingAverage) {
        String commentList = reviews.stream()
            .map(review -> "- (%d점) %s".formatted(review.getRating(), review.getComment().trim()))
            .collect(Collectors.joining("\n"));

        return """
            아래는 어떤 부스에 대한 방문객 리뷰 목록이야 (평균 별점: %s점).
            리뷰들을 바탕으로 이 부스의 장점과 아쉬운 점을 한국어로 3~4줄 이내로 요약해줘.
            과장하거나 리뷰에 없는 내용을 지어내지 말고, 실제 리뷰에서 반복되는 의견 위주로 정리해줘.

            리뷰 목록:
            %s
            """.formatted(
            ratingAverage == null ? "정보 없음" : "%.1f".formatted(ratingAverage),
            commentList
        );
    }

    private BoothReviewSummary upsert(
            Long boothId, BoothReviewSummary existing, String summary, int reviewCountAtSummary) {
        OffsetDateTime now = OffsetDateTime.now();
        if (existing != null) {
            existing.update(summary, reviewCountAtSummary, now);
            return existing;
        }

        BoothReviewSummary created = BoothReviewSummary.builder()
            .boothId(boothId)
            .summary(summary)
            .reviewCountAtSummary(reviewCountAtSummary)
            .generatedAt(now)
            .build();
        return boothReviewSummaryRepository.save(created);
    }

    private BoothReviewSummaryResponse toResponse(
            BoothReviewSummary summary, int totalReviewCount, Double ratingAverage, boolean stale) {
        return BoothReviewSummaryResponse.builder()
            .boothId(summary.getBoothId())
            .available(true)
            .summary(summary.getSummary())
            .reviewCount(totalReviewCount)
            .ratingAverage(ratingAverage)
            .generatedAt(summary.getGeneratedAt())
            .stale(stale)
            .build();
    }
}
