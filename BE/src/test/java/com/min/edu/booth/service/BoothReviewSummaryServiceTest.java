package com.min.edu.booth.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

import com.min.edu.booth.ai.OpenAiChatClient;
import com.min.edu.booth.domain.BoothReview;
import com.min.edu.booth.domain.BoothReviewSummary;
import com.min.edu.booth.domain.BoothReviewSummaryBatch;
import com.min.edu.booth.dto.BoothReviewSummaryResponse;
import com.min.edu.booth.repository.BoothRepository;
import com.min.edu.booth.repository.BoothReviewRepository;
import com.min.edu.booth.repository.BoothReviewSummaryBatchRepository;
import com.min.edu.booth.repository.BoothReviewSummaryRepository;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;

@ExtendWith(MockitoExtension.class)
class BoothReviewSummaryServiceTest {

    @Mock private BoothReviewRepository boothReviewRepository;
    @Mock private BoothReviewSummaryRepository boothReviewSummaryRepository;
    @Mock private BoothReviewSummaryBatchRepository boothReviewSummaryBatchRepository;
    @Mock private BoothRepository boothRepository;
    @Mock private OpenAiChatClient openAiChatClient;
    @Mock private BoothReviewSummaryWriter boothReviewSummaryWriter;
    @Mock private StringRedisTemplate stringRedisTemplate;
    @Mock private ValueOperations<String, String> valueOperations;

    private BoothReviewSummaryService service;

    private static final Long BOOTH_ID = 1L;

    @BeforeEach
    void setUp() {
        service = new BoothReviewSummaryService(
            boothReviewRepository,
            boothReviewSummaryRepository,
            boothReviewSummaryBatchRepository,
            boothRepository,
            openAiChatClient,
            boothReviewSummaryWriter,
            stringRedisTemplate
        );
        given(boothRepository.existsById(BOOTH_ID)).willReturn(true);
    }

    // 재생성 경로(refreshSummary)를 타는 테스트에서 공통으로 필요한, 락 획득 성공 스텁
    private void givenLockAcquired() {
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any(java.time.Duration.class))).willReturn(true);
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
        verify(openAiChatClient, never()).summarize(any(), any());
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
        verify(openAiChatClient, never()).summarize(any(), any());
    }

    @Test
    void 리뷰_개수는_같아도_최신_수정시각이_바뀌었으면_요약을_다시_생성한다() {
        OffsetDateTime oldUpdatedAt = OffsetDateTime.now().minusDays(1);
        OffsetDateTime newUpdatedAt = OffsetDateTime.now();

        given(boothReviewRepository.countByBoothIdAndCommentIsNotBlank(BOOTH_ID)).willReturn(5L);
        given(boothReviewRepository.findAverageRatingByBoothId(BOOTH_ID)).willReturn(Optional.of(4.0));
        given(boothReviewRepository.countByBoothId(BOOTH_ID)).willReturn(5L);
        given(boothReviewRepository.findMaxUpdatedAtByBoothIdAndCommentIsNotBlank(BOOTH_ID))
            .willReturn(Optional.of(newUpdatedAt));

        BoothReviewSummary cached = BoothReviewSummary.builder()
            .boothId(BOOTH_ID)
            .summary("예전 요약")
            .reviewCountAtSummary(5)
            .lastReviewUpdatedAt(oldUpdatedAt)
            .generatedAt(oldUpdatedAt)
            .build();
        given(boothReviewSummaryRepository.findById(BOOTH_ID)).willReturn(Optional.of(cached));
        given(boothReviewRepository.findCommentedReviewsAfterIdOrderByIdAsc(eq(BOOTH_ID), eq(0L), any(Pageable.class)))
            .willReturn(List.of());
        givenLockAcquired();
        given(openAiChatClient.summarize(any(), any())).willReturn("갱신된 요약");

        BoothReviewSummary saved = BoothReviewSummary.builder()
            .boothId(BOOTH_ID)
            .summary("갱신된 요약")
            .reviewCountAtSummary(5)
            .lastReviewUpdatedAt(newUpdatedAt)
            .generatedAt(OffsetDateTime.now())
            .build();
        given(boothReviewSummaryWriter.save(eq(BOOTH_ID), eq("갱신된 요약"), eq(5), eq(newUpdatedAt)))
            .willReturn(saved);

        BoothReviewSummaryResponse response = service.getSummary(BOOTH_ID);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getSummary()).isEqualTo("갱신된 요약");
        assertThat(response.isStale()).isFalse();
    }

    @Test
    void 캐시가_없거나_리뷰수가_바뀌었으면_LLM을_호출해_새_요약을_저장한다() {
        given(boothReviewRepository.countByBoothIdAndCommentIsNotBlank(BOOTH_ID)).willReturn(5L);
        given(boothReviewRepository.findAverageRatingByBoothId(BOOTH_ID)).willReturn(Optional.of(4.0));
        given(boothReviewRepository.countByBoothId(BOOTH_ID)).willReturn(5L);
        given(boothReviewSummaryRepository.findById(BOOTH_ID)).willReturn(Optional.empty());
        givenLockAcquired();

        BoothReview review = BoothReview.builder()
            .boothId(BOOTH_ID)
            .memberId(10L)
            .rating((short) 5)
            .comment("정말 좋았어요")
            .createdAt(OffsetDateTime.now())
            .updatedAt(OffsetDateTime.now())
            .build();
        given(boothReviewRepository.findCommentedReviewsAfterIdOrderByIdAsc(eq(BOOTH_ID), eq(0L), any(Pageable.class)))
            .willReturn(List.of(review));
        given(openAiChatClient.summarize(any(), any())).willReturn("새로 생성된 요약");

        BoothReviewSummary saved = BoothReviewSummary.builder()
            .boothId(BOOTH_ID)
            .summary("새로 생성된 요약")
            .reviewCountAtSummary(5)
            .generatedAt(OffsetDateTime.now())
            .build();
        given(boothReviewSummaryWriter.save(eq(BOOTH_ID), eq("새로 생성된 요약"), anyInt(), any()))
            .willReturn(saved);

        BoothReviewSummaryResponse response = service.getSummary(BOOTH_ID);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getSummary()).isEqualTo("새로 생성된 요약");
        assertThat(response.isStale()).isFalse();
        verify(boothReviewSummaryWriter).save(eq(BOOTH_ID), eq("새로 생성된 요약"), anyInt(), any());
    }

    @Test
    void LLM_호출이_실패하면_기존_캐시를_stale로_반환하고_저장하지_않는다() {
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
        given(boothReviewRepository.findCommentedReviewsAfterIdOrderByIdAsc(eq(BOOTH_ID), eq(0L), any(Pageable.class)))
            .willReturn(List.of());
        givenLockAcquired();
        given(openAiChatClient.summarize(any(), any()))
            .willThrow(new BusinessException(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE));

        BoothReviewSummaryResponse response = service.getSummary(BOOTH_ID);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getSummary()).isEqualTo("예전 요약");
        assertThat(response.isStale()).isTrue();
        verify(boothReviewSummaryWriter, never()).save(any(), any(), anyInt(), any());
    }

    @Test
    void LLM_호출이_실패하고_캐시도_없으면_예외를_던진다() {
        given(boothReviewRepository.countByBoothIdAndCommentIsNotBlank(BOOTH_ID)).willReturn(5L);
        given(boothReviewRepository.findAverageRatingByBoothId(BOOTH_ID)).willReturn(Optional.of(4.0));
        given(boothReviewRepository.countByBoothId(BOOTH_ID)).willReturn(5L);
        given(boothReviewSummaryRepository.findById(BOOTH_ID)).willReturn(Optional.empty());
        given(boothReviewRepository.findCommentedReviewsAfterIdOrderByIdAsc(eq(BOOTH_ID), eq(0L), any(Pageable.class)))
            .willReturn(List.of());
        givenLockAcquired();
        given(openAiChatClient.summarize(any(), any()))
            .willThrow(new BusinessException(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE));

        assertThatThrownBy(() -> service.getSummary(BOOTH_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE);
    }

    @Test
    void 동시_요청으로_락을_못_잡으면_LLM을_호출하지_않고_캐시를_stale로_반환한다() {
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
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any(java.time.Duration.class))).willReturn(false);

        BoothReviewSummaryResponse response = service.getSummary(BOOTH_ID);

        assertThat(response.isStale()).isTrue();
        assertThat(response.getSummary()).isEqualTo("예전 요약");
        verify(openAiChatClient, never()).summarize(any(), any());
    }

    @Test
    void 동시_요청으로_락을_못_잡고_캐시도_없으면_예외를_던진다() {
        given(boothReviewRepository.countByBoothIdAndCommentIsNotBlank(BOOTH_ID)).willReturn(5L);
        given(boothReviewRepository.findAverageRatingByBoothId(BOOTH_ID)).willReturn(Optional.of(4.0));
        given(boothReviewRepository.countByBoothId(BOOTH_ID)).willReturn(5L);
        given(boothReviewSummaryRepository.findById(BOOTH_ID)).willReturn(Optional.empty());
        given(stringRedisTemplate.opsForValue()).willReturn(valueOperations);
        given(valueOperations.setIfAbsent(any(), any(), any(java.time.Duration.class))).willReturn(false);

        assertThatThrownBy(() -> service.getSummary(BOOTH_ID))
            .isInstanceOf(BusinessException.class)
            .extracting("errorCode")
            .isEqualTo(GlobalErrorCode.BOOTH_REVIEW_SUMMARY_UNAVAILABLE);
        verify(openAiChatClient, never()).summarize(any(), any());
    }

    private List<BoothReview> buildReviews(long startId, int count) {
        List<BoothReview> reviews = new ArrayList<>();
        for (int i = 0; i < count; i++) {
            reviews.add(BoothReview.builder()
                .id(startId + i)
                .boothId(BOOTH_ID)
                .memberId(100L + i)
                .rating((short) 5)
                .comment("리뷰 " + (startId + i))
                .createdAt(OffsetDateTime.now())
                .updatedAt(OffsetDateTime.now())
                .build());
        }
        return reviews;
    }

    // BATCH_SIZE(50)를 넘어가는 51번째 리뷰가 들어오면: 처음 50개는 새 배치로 "닫혀서" 배치 요약이
    // 한 번 생성되고, 아직 배치가 안 찬 나머지 1개(tail)와 함께 최종 reduce 요약이 한 번 더 생성된다.
    // 예전 방식("최신 50개만 매번 재사용")과 달리 51번째 리뷰가 들어와도 앞의 50개가 요약 재료에서
    // 사라지지 않는다는 걸 확인한다.
    @Test
    void 리뷰가_BATCH_SIZE를_넘으면_새_배치를_닫고_배치요약과_reduce_두_번만_LLM을_호출한다() {
        given(boothReviewRepository.countByBoothIdAndCommentIsNotBlank(BOOTH_ID)).willReturn(51L);
        given(boothReviewRepository.findAverageRatingByBoothId(BOOTH_ID)).willReturn(Optional.of(4.2));
        given(boothReviewRepository.countByBoothId(BOOTH_ID)).willReturn(51L);
        given(boothReviewSummaryRepository.findById(BOOTH_ID)).willReturn(Optional.empty());
        givenLockAcquired();

        List<BoothReview> firstBatch = buildReviews(1L, 50);
        List<BoothReview> tail = buildReviews(51L, 1);
        given(boothReviewRepository.findCommentedReviewsAfterIdOrderByIdAsc(eq(BOOTH_ID), eq(0L), any(Pageable.class)))
            .willReturn(firstBatch);
        given(boothReviewRepository.findCommentedReviewsAfterIdOrderByIdAsc(eq(BOOTH_ID), eq(50L), any(Pageable.class)))
            .willReturn(tail);

        given(openAiChatClient.summarize(any(), any())).willReturn("요약 결과");

        BoothReviewSummaryBatch savedBatch = BoothReviewSummaryBatch.builder()
            .id(100L).boothId(BOOTH_ID).batchIndex(0).fromReviewId(1L).toReviewId(50L)
            .reviewCount(50).summary("요약 결과").generatedAt(OffsetDateTime.now()).build();
        given(boothReviewSummaryWriter.saveBatch(any(), eq(BOOTH_ID), eq(0), eq(1L), eq(50L), eq(50), any(), any()))
            .willReturn(savedBatch);
        given(boothReviewSummaryWriter.save(eq(BOOTH_ID), eq("요약 결과"), eq(51), any()))
            .willReturn(BoothReviewSummary.builder()
                .boothId(BOOTH_ID).summary("요약 결과").reviewCountAtSummary(51).generatedAt(OffsetDateTime.now()).build());

        BoothReviewSummaryResponse response = service.getSummary(BOOTH_ID);

        assertThat(response.isAvailable()).isTrue();
        assertThat(response.getSummary()).isEqualTo("요약 결과");
        verify(openAiChatClient, times(2)).summarize(any(), any());
        verify(boothReviewSummaryWriter).saveBatch(any(), eq(BOOTH_ID), eq(0), eq(1L), eq(50L), eq(50), any(), any());
    }

    // 이미 닫힌 배치는 그 범위 안 리뷰 수·최신 수정 시각이 그대로면(=삭제/수정이 없었으면) 다시
    // 요약하지 않는다. 리뷰 총량이 50개를 훌쩍 넘겨도 재생성 1번당 LLM 호출은 최종 reduce 1번뿐이라는,
    // "지금과 비슷한 호출량 유지"를 보장하는 핵심 동작이다.
    @Test
    void 이미_닫힌_배치는_내용이_안_바뀌면_재요약없이_reduce만_호출한다() {
        given(boothReviewRepository.countByBoothIdAndCommentIsNotBlank(BOOTH_ID)).willReturn(52L);
        given(boothReviewRepository.findAverageRatingByBoothId(BOOTH_ID)).willReturn(Optional.of(4.2));
        given(boothReviewRepository.countByBoothId(BOOTH_ID)).willReturn(52L);
        given(boothReviewSummaryRepository.findById(BOOTH_ID)).willReturn(Optional.empty());
        givenLockAcquired();

        OffsetDateTime batchUpdatedAt = OffsetDateTime.now().minusDays(2);
        BoothReviewSummaryBatch existingBatch = BoothReviewSummaryBatch.builder()
            .id(1L).boothId(BOOTH_ID).batchIndex(0).fromReviewId(1L).toReviewId(50L)
            .reviewCount(50).summary("기존 배치 요약").lastReviewUpdatedAt(batchUpdatedAt)
            .generatedAt(batchUpdatedAt).build();
        given(boothReviewSummaryBatchRepository.findByBoothIdOrderByBatchIndexAsc(BOOTH_ID))
            .willReturn(List.of(existingBatch));

        given(boothReviewRepository.countCommentedReviewsInIdRange(BOOTH_ID, 1L, 50L)).willReturn(50L);
        given(boothReviewRepository.findMaxUpdatedAtInIdRange(BOOTH_ID, 1L, 50L))
            .willReturn(Optional.of(batchUpdatedAt));

        List<BoothReview> tail = buildReviews(51L, 2);
        given(boothReviewRepository.findCommentedReviewsAfterIdOrderByIdAsc(eq(BOOTH_ID), eq(50L), any(Pageable.class)))
            .willReturn(tail);

        given(openAiChatClient.summarize(any(), any())).willReturn("최종 요약");
        given(boothReviewSummaryWriter.save(eq(BOOTH_ID), eq("최종 요약"), eq(52), any()))
            .willReturn(BoothReviewSummary.builder()
                .boothId(BOOTH_ID).summary("최종 요약").reviewCountAtSummary(52).generatedAt(OffsetDateTime.now()).build());

        BoothReviewSummaryResponse response = service.getSummary(BOOTH_ID);

        assertThat(response.getSummary()).isEqualTo("최종 요약");
        verify(openAiChatClient, times(1)).summarize(any(), any());
        verify(boothReviewSummaryWriter, never())
            .saveBatch(any(), any(), anyInt(), anyLong(), anyLong(), anyInt(), any(), any());
    }
}
