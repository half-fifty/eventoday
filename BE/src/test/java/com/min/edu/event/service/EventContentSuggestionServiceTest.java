package com.min.edu.event.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.mock;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.event.dto.EventContentSuggestionDto;
import com.min.edu.event.repository.EventOrganizationMemberRepository;
import com.min.edu.member.domain.PlatformRole;
import com.min.edu.organization.domain.OrganizationMemberStatus;
import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.Test;

class EventContentSuggestionServiceTest {
    private final EventOrganizationMemberRepository members =
            mock(EventOrganizationMemberRepository.class);
    private final EventContentSuggestionService service = new EventContentSuggestionService(
            members, "https://api.groq.com/openai/v1", "", "openai/gpt-oss-120b",
            Duration.ofSeconds(1), Duration.ofSeconds(1));

    @Test
    void apiKey가_없어도_입력사실을_검색근거로_안전하게_반환한다() {
        given(members.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                eq(6L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), anyList()))
                .willReturn(true);

        EventContentSuggestionDto.Response result = service.suggest(request(), actor());

        assertThat(result.shortDescription()).isEmpty();
        assertThat(result.description()).isEmpty();
        assertThat(result.sources()).contains("행사명", "행사 일정", "행사 장소",
                "포스터 Vision OCR", "행사 등록 사실성 정책");
        assertThat(result.notice()).contains("Groq API 키");
    }

    @Test
    void 조직관리자가_아니면_추천을_요청할_수_없다() {
        given(members.existsByOrganizationIdAndMemberIdAndStatusAndOrganizationRoleIn(
                eq(6L), eq(10L), eq(OrganizationMemberStatus.ACTIVE), anyList()))
                .willReturn(false);

        assertThatThrownBy(() -> service.suggest(request(), actor()))
                .isInstanceOf(BusinessException.class);
    }

    private EventContentSuggestionDto.Request request() {
        return new EventContentSuggestionDto.Request(6L, "2026 코리아빌드위크", "EXPO",
                "2026-08-24T10:00", "2026-08-25T18:00", "코엑스",
                "건설·건축 전시회", "2026 코리아빌드위크 코엑스",
                List.of("CONSTRUCTION_ARCHITECTURE_INTERIOR", "CULTURE_ART"));
    }
    private AuthenticatedMemberDto actor() {
        return new AuthenticatedMemberDto(10L, PlatformRole.USER);
    }
}
