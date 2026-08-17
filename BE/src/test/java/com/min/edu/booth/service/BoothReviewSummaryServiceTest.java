package com.min.edu.booth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;

@ExtendWith(MockitoExtension.class)
class BoothReviewSummaryServiceTest {

    @Mock private BoothReviewRepository boothReviewRepository;
    @Mock private BoothReviewSummaryRepository boothReviewSummaryRepository;
    @Mock private BoothRepository boothRepository;
    @Mock private OpenAiChatClient openAiChatClient;

    private BoothReviewSummaryService service;

    private static final Long BOOTH_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new BoothReviewSummaryService(
            boothReviewRepository,
            boothReviewSummaryRepository,
            boothRepository,
            openAiChatClient
        );
        given(boothRepository.existsById(BOOTH_ID)).willReturn(true);
    }

    @Test
    void 부스가_없으면_예외() {
        given(boothRepository.existsById(BOOTH_ID)).willReturn(false);

        assertThatThrownBy(() -> service.getSummary(BOOTH_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.BOOTH_NOT_FOUND);
    }

    @Test
    void 코멘트_있는_리뷰가_최소개수_미만이면_요약불가로_응답하고_LLM을_호출하지_않는다() {
        given(boothReviewRepository.countByBoothIdAndCommentIsNotBlank(BOOTH_ID)).willReturn(2L);
        given(boothReviewRepository.findAverageRatingByBoothId(BOOTH_ID)).willReturn(Optional.of(4.5));
        given(boothReviewRepository.countByBoothId(BOOTH_ID)).willReturn(2L);

        BoothReviewSummaryResponse response = service.getSummary(BOOTH_ID);

        assertThat(response.isAvailable()).isFalse();
        assertThat(response.getSummary()).isNull();
        verify(openAiChatClient, never()).summarize(any());
    }

    @Test
    void 캐시된_요약이_최신이면_LLM을_다시_호출하지_않고_캐시를_반환한다() {
        given(boothReviewRepository.countByBoothIdAndCommentIsNotBlank(BOOTH_ID)).willReturn(5L);
        given(boothReviewRepository.findAverageRatingByBoothId(BOOTH_ID)).willReturn(Optional.of(4.0));
        given(boothReviewRepository.countByBoothId(BOOTH_ID)).willReturn(5L);

        BoothReviewSummary cached = BoothReviewSummary.builder()
            .boothId(BOOTH_ID)
            .summary("기존 요약")
            .reviewCountAtSummary(5)
            .generatedAt(OffsetDateTime.now())
            .build();
        given(boothReviewSummaryRepository.findById(BOOTH_ID)).willReturn(Optional.of(cached));

        BoothReviewSummaryResponse response = service.getSummary(BOOTH_ID);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getSummary()).isEqualTo("기존 요약");
        assertThat(response.isStale()).isFalse();
        verify(openAiChatClient, never()).summarize(any());
    }

    @Test
    void 캐시가_없거나_리뷰수가_바뀌었으면_LLM을_호출해_새_요약을_저장한다() {
        given(boothReviewRepository.countByBoothIdAndCommentIsNotBlank(BOOTH_ID)).willReturn(5L);
        given(boothReviewRepository.findAverageRatingByBoothId(BOOTH_ID)).willReturn(Optional.of(4.0));
        given(boothReviewRepository.countByBoothId(BOOTH_ID)).willReturn(5L);
        given(boothReviewSummaryRepository.findById(BOOTH_ID)).willReturn(Optional.empty());

        BoothReview review = BoothReview.builder()
            .boothId(BOOTH_ID)
            .memberId(10L)
            .rating((short) 5)
            .comment("정말 좋았어요")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        given(boothReviewRepository.findRecentCommentedReviews(eq(BOOTH_ID), any(Pageable.class)))
            .willReturn(List.of(review));
        given(openAiChatClient.summarize(any())).willReturn("새로 생성된 요약");
        given(boothReviewSummaryRepository.save(any(BoothReviewSummary.class)))
            .willAnswer(invocation -> invocation.getArgument(0));

        BoothReviewSummaryResponse response = service.getSummary(BOOTH_ID);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getSummary()).isEqualTo("새로 생성된 요약");
        assertThat(response.isStale()).isFalse();
        verify(boothReviewSummaryRepository).save(any(BoothReviewSummary.class));
    }

    @Test
    void LLM_호출이_실패하면_기존_캐시를_stale로_반환한다() {
        given(boothReviewRepository.countByBoothIdAndCommentIsNotBlank(BOOTH_ID)).willReturn(5L);
        given(boothReviewRepository.findAverageRatingByBoothId(BOOTH_ID)).willReturn(Optional.of(4.0));
        given(boothReviewRepository.countByBoothId(BOOTH_ID)).willReturn(5L);

        BoothReviewSummary cached = BoothReviewSummary.builder()
            .boothId(BOOTH_ID)
            .summary("예전 요약")
            .reviewCountAtSummary(3)
            .generatedAt(OffsetDateTime.now().minusDays(1))
            .build();
        given(boothReviewSummaryRepository.findById(BOOTH_ID)).willReturn(Optional.of(cached));
        given(boothReviewRepository.findRecentCommentedReviews(eq(BOOTH_ID), any(Pageable.class)))
            .willReturn(List.of());
        given(openAiChatClient.summarize(any()))
            .willThrow(new BusinessException(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE));

        BoothReviewSummaryResponse response = service.getSummary(BOOTH_ID);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getSummary()).isEqualTo("예전 요약");
        assertThat(response.isStale()).isTrue();
    }

    @Test
    void LLM_호출이_실패하고_캐시도_없으면_예외를_던진다() {
        given(boothReviewRepository.countByBoothIdAndCommentIsNotBlank(BOOTH_ID)).willReturn(5L);
        given(boothReviewRepository.findAverageRatingByBoothId(BOOTH_ID)).willReturn(Optional.of(4.0));
        given(boothReviewRepository.countByBoothId(BOOTH_ID)).willReturn(5L);
        given(boothReviewSummaryRepository.findById(BOOTH_ID)).willReturn(Optional.empty());
        given(boothReviewRepository.findRecentCommentedReviews(eq(BOOTH_ID), any(Pageable.class)))
            .willReturn(List.of());
        given(openAiChatClient.summarize(any()))
            .willThrow(new BusinessException(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE));

        assertThatThrownBy(() -> service.getSummary(BOOTH_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE);
    }
}
