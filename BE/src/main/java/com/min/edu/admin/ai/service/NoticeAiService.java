package com.min.edu.admin.ai.service;

import com.min.edu.admin.ai.dto.NoticeAiDtos;
import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.ai.ContentAiAction;
import com.min.edu.common.ai.ContentAiChatClient;
import com.min.edu.common.ai.ContentAiPrompts;
import com.min.edu.common.ai.ContentAiResultParser;
import com.min.edu.common.ai.ContentAiTone;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.member.domain.PlatformRole;
import org.springframework.stereotype.Service;

/**
 * 사이트 공지 AI 작성 보조 서비스.
 *
 * AI는 초안을 만들 뿐이고 저장은 하지 않는다. 결과를 응답으로만 돌려주고,
 * 최종 등록 여부는 관리자가 화면에서 결정한다.
 *
 * 응답 파싱·HTML 정제는 ContentAiResultParser가, 공통 프롬프트 규칙은 ContentAiPrompts가 담당한다.
 * 이 서비스는 권한 검증과 사이트 공지에 맞는 프롬프트 조립만 맡는다.
 */
@Service
public class NoticeAiService {

    private static final String SYSTEM_PROMPT = """
            너는 행사 플랫폼 EVENTODAY의 사이트 공지사항 작성을 돕는 어시스턴트다.
            특정 행사가 아니라 서비스 전체에 걸친 공지(점검·약관 변경·정책 안내 등)를 다룬다.

            %s
            """.formatted(ContentAiPrompts.COMMON_RULES);

    private final ContentAiChatClient contentAiChatClient;
    private final ContentAiResultParser resultParser;

    public NoticeAiService(ContentAiChatClient contentAiChatClient, ContentAiResultParser resultParser) {
        this.contentAiChatClient = contentAiChatClient;
        this.resultParser = resultParser;
    }

    /**
     * AI 작성 보조 실행 (PLATFORM_ADMIN 전용)
     *
     * 실패는 모두 BusinessException으로 던진다. 프론트엔드는 이 응답을 받아도
     * 작성 중인 내용을 건드리지 않고 안내만 표시한다.
     */
    public NoticeAiDtos.GenerateResponse generate(
            NoticeAiDtos.GenerateRequest request, AuthenticatedMemberDto actor) {

        requireAdmin(actor);
        validate(request);

        ContentAiAction action = request.action();
        String rawResponse = contentAiChatClient.chat(SYSTEM_PROMPT, buildUserPrompt(request));

        if (action.isTitleSuggestion()) {
            ContentAiResultParser.Result result = resultParser.parseTitleSuggestions(rawResponse);
            return NoticeAiDtos.GenerateResponse.ofTitles(result.titleSuggestions());
        }
        ContentAiResultParser.Result result = resultParser.parseContent(rawResponse);
        return NoticeAiDtos.GenerateResponse.ofContent(result.title(), result.content());
    }

    /** 동작별 필수 입력 검증 — 빈 요청으로 AI를 호출해 비용만 쓰는 것을 막는다 */
    private void validate(NoticeAiDtos.GenerateRequest request) {
        ContentAiAction action = request.action();
        if (action.isPromptRequired() && isBlank(request.prompt())) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        if (action.isContentRequired() && isBlank(request.content())) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String buildUserPrompt(NoticeAiDtos.GenerateRequest request) {
        ContentAiAction action = request.action();
        ContentAiTone tone = ContentAiTone.orDefault(request.tone());

        StringBuilder prompt = new StringBuilder();
        prompt.append("작업: ").append(taskInstruction(action)).append("\n\n");

        // 맞춤법 교정은 표현을 그대로 둬야 하고 제목 추천은 본문을 쓰지 않으므로 문체 지시를 넣지 않는다.
        // (넣으면 "문체를 반드시 따르라"는 지시와 충돌해 원문을 고쳐 쓴다)
        if (action != ContentAiAction.PROOFREAD && action != ContentAiAction.TITLE_SUGGEST) {
            prompt.append("문체:\n").append(tone.getInstruction()).append("\n\n");
        }

        if (!isBlank(request.prompt())) {
            prompt.append("<request>\n").append(request.prompt().trim()).append("\n</request>\n\n");
        }
        if (!isBlank(request.title())) {
            prompt.append("<current_title>\n").append(request.title().trim()).append("\n</current_title>\n\n");
        }
        if (!isBlank(request.content())) {
            prompt.append("<current_content>\n").append(request.content().trim()).append("\n</current_content>\n");
        }
        return prompt.toString();
    }

    private String taskInstruction(ContentAiAction action) {
        return switch (action) {
            case GENERATE -> """
                    <request>의 내용을 바탕으로 사이트 공지사항 제목과 본문을 새로 작성한다.
                    title과 content를 모두 채운다. 제목은 40자 이내로 만든다.

                    아래를 빠짐없이 다룬다. 단, <request>에 근거가 없는 항목은 생략한다.
                    - 공지 목적
                    - 일정 (날짜·시간)
                    - 대상
                    - 변경 사항
                    - 주의사항
                    - 이용자가 해야 할 행동
                    - 문의 방법

                    공지의 배경 설명, 유의사항, 이용자 안내 문구는 문맥에 맞게 채워 넣어도 된다.""";
            case TITLE_SUGGEST -> """
                    <current_content>를 읽고 제목 후보를 %d개 이내로 제안한다.
                    후보끼리 서로 다른 관점을 잡는다. 예: 일정 중심 / 대상 중심 / 조치 중심.
                    표현만 조금 바꾼 비슷한 제목을 여러 개 내놓지 않는다. 각 제목은 40자 이내로 만든다.
                    titleSuggestions 배열에만 담고 title과 content는 빈 값으로 둔다."""
                    .formatted(ContentAiResultParser.MAX_TITLE_SUGGESTIONS);
            case POLISH -> """
                    <current_content>의 문장을 자연스럽고 읽기 좋게 다듬는다.
                    의미와 문단·목록 구조는 그대로 두고 표현만 고친다.
                    원본에 없는 내용을 새로 더하지 않는다. content에 결과를 담는다.""";
            case SUMMARIZE -> """
                    <current_content>를 원본의 3분의 1 수준으로 줄인다.
                    일정·대상·이용자가 해야 할 행동은 반드시 남기고, 배경 설명과 인사말은 덜어낸다.
                    원본에 없는 내용을 새로 더하지 않는다. content에 결과를 담는다.""";
            case EXPAND -> """
                    <current_content>를 원본보다 확실히 길고 충실한 공지로 다시 쓴다.
                    분량은 원본의 1.5~2배를 목표로 한다. 원본과 비슷한 길이로 끝내면 실패한 작업이다.

                    아래 항목을 보강한다. 원본에 근거가 없는 항목은 넣지 않는다.
                    - 이 공지를 하는 배경과 이유
                    - 대상별로 달라지는 안내
                    - 진행 중 · 이후에 유의할 점
                    - 이용자가 미리 해두면 좋은 준비
                    - 문의 방법

                    공지의 배경 설명, 유의사항, 이용자 안내 문구는 문맥에 맞게 채워 넣어도 된다.
                    다만 같은 말을 바꿔 쓰거나 '양해 부탁드립니다' 같은 상투적인 문장으로 분량을 채우지 않는다.
                    내용이 길어지므로 문단을 나누고, 항목이 여러 개면 <ul>로 정리한다.
                    content에 결과를 담는다.""";
            case PROOFREAD -> """
                    <current_content>의 맞춤법·띄어쓰기·문법 오류만 고친다.
                    문장 구조나 표현, 문체는 바꾸지 않는다. 원본에 없는 내용을 더하지 않는다.
                    고칠 오류가 없으면 원문을 그대로 반환한다. 억지로 바꾸지 않는다.
                    content에 결과를 담는다.""";
            case TONE -> """
                    <current_content>를 지정된 문체로 다시 쓴다.
                    담긴 정보와 분량은 유지하고 어미·표현만 바꾼다.
                    원본에 없는 내용을 새로 더하지 않는다. content에 결과를 담는다.""";
        };
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** PlatformNoticeService.requireAdmin과 동일한 검증 — 공지 작성 권한과 AI 사용 권한을 일치시킨다 */
    private void requireAdmin(AuthenticatedMemberDto actor) {
        if (actor == null || actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }
}
