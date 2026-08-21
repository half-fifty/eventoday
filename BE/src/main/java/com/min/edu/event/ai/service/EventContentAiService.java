package com.min.edu.event.ai.service;

import com.min.edu.auth.dto.AuthenticatedMemberDto;
import com.min.edu.common.ai.ContentAiAction;
import com.min.edu.common.ai.ContentAiChatClient;
import com.min.edu.common.ai.ContentAiPrompts;
import com.min.edu.common.ai.ContentAiResultParser;
import com.min.edu.common.ai.ContentAiTone;
import com.min.edu.common.exception.BusinessException;
import com.min.edu.common.exception.GlobalErrorCode;
import com.min.edu.event.ai.dto.EventContentAiDtos;
import com.min.edu.event.domain.Event;
import com.min.edu.event.domain.EventContentAudience;
import com.min.edu.event.domain.EventContentType;
import com.min.edu.event.domain.EventRole;
import com.min.edu.event.repository.EventMemberRepository;
import com.min.edu.event.repository.EventRepository;
import com.min.edu.member.domain.PlatformRole;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * 행사 공지·자료 AI 작성 보조 서비스.
 *
 * 사이트 공지(NoticeAiService)와 응답 형식·정제 규칙은 같고, 두 가지가 다르다.
 * 1) 권한 — 해당 행사의 EVENT_MANAGER 또는 PLATFORM_ADMIN (EventContentService.create와 동일)
 * 2) 프롬프트 — 행사명·기간·장소와 공개 대상을 넣어주고, 공지와 자료를 다르게 다룬다
 */
@Service
@Transactional(readOnly = true)
public class EventContentAiService {

    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy년 M월 d일");

    private final ContentAiChatClient contentAiChatClient;
    private final ContentAiResultParser resultParser;
    private final EventRepository eventRepository;
    private final EventMemberRepository eventMemberRepository;

    public EventContentAiService(
            ContentAiChatClient contentAiChatClient,
            ContentAiResultParser resultParser,
            EventRepository eventRepository,
            EventMemberRepository eventMemberRepository) {
        this.contentAiChatClient = contentAiChatClient;
        this.resultParser = resultParser;
        this.eventRepository = eventRepository;
        this.eventMemberRepository = eventMemberRepository;
    }

    public EventContentAiDtos.GenerateResponse generate(
            Long eventId, EventContentAiDtos.GenerateRequest request, AuthenticatedMemberDto actor) {

        Event event = eventRepository.findById(eventId)
                .orElseThrow(() -> new BusinessException(GlobalErrorCode.ENTITY_NOT_FOUND));
        requireEventManager(eventId, actor);
        validate(request);

        String rawResponse = contentAiChatClient.chat(
                systemPrompt(request.contentType()), buildUserPrompt(event, request));

        if (request.action().isTitleSuggestion()) {
            ContentAiResultParser.Result result = resultParser.parseTitleSuggestions(rawResponse);
            return EventContentAiDtos.GenerateResponse.ofTitles(result.titleSuggestions());
        }
        ContentAiResultParser.Result result = resultParser.parseContent(rawResponse);
        return EventContentAiDtos.GenerateResponse.ofContent(result.title(), result.content());
    }

    /** 공지와 자료는 글의 목적이 달라 역할 문장을 나눈다 */
    private String systemPrompt(EventContentType contentType) {
        String role = contentType == EventContentType.RESOURCE
                ? """
                  너는 박람회·전시회 개최자가 참가자에게 배포하는 "자료"의 설명글 작성을 돕는 어시스턴트다.
                  자료는 알림이 아니라 안내서다. 무엇에 대한 자료인지, 누가 언제 어떻게 쓰는지를 설명한다.
                  첨부파일이 따로 있을 수 있으므로 본문은 그 파일을 여는 사람이 먼저 읽는 설명이라고 생각하고 쓴다."""
                : """
                  너는 박람회·전시회 개최자의 행사 공지사항 작성을 돕는 어시스턴트다.
                  플랫폼 전체 공지가 아니라 특정 행사의 참가자에게 전달하는 공지를 다룬다.""";

        return role + "\n\n" + ContentAiPrompts.COMMON_RULES + "\n";
    }

    private void validate(EventContentAiDtos.GenerateRequest request) {
        ContentAiAction action = request.action();
        if (action.isPromptRequired() && isBlank(request.prompt())) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
        if (action.isContentRequired() && isBlank(request.content())) {
            throw new BusinessException(GlobalErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String buildUserPrompt(Event event, EventContentAiDtos.GenerateRequest request) {
        ContentAiAction action = request.action();
        ContentAiTone tone = ContentAiTone.orDefault(request.tone());

        StringBuilder prompt = new StringBuilder();
        prompt.append("작업: ").append(taskInstruction(request)).append("\n\n");

        if (action != ContentAiAction.PROOFREAD && action != ContentAiAction.TITLE_SUGGEST) {
            prompt.append("문체:\n").append(tone.getInstruction()).append("\n\n");
        }

        // 행사 맥락 — 이 정보가 있어야 "어느 행사의 공지인지" 드러나는 글이 나온다.
        // 다만 아래 값도 사용자가 입력한 데이터이므로 지시가 아니라 참고 자료로 못박는다.
        prompt.append("<event_info>\n");
        prompt.append("행사명: ").append(event.getName()).append('\n');
        if (event.getStartAt() != null) {
            prompt.append("행사 기간: ").append(formatDate(event.getStartAt()))
                    .append(" ~ ").append(formatDate(event.getEndAt())).append('\n');
        }
        if (event.getVenueName() != null && !event.getVenueName().isBlank()) {
            prompt.append("장소: ").append(event.getVenueName()).append('\n');
        }
        prompt.append("읽는 사람: ").append(audienceLabel(request.audience())).append('\n');
        if (!isBlank(request.resourceType())) {
            prompt.append("자료 분류: ").append(request.resourceType().trim()).append('\n');
        }
        prompt.append("</event_info>\n\n");

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

    /** 공개 대상을 사람이 읽는 말로 바꿔 프롬프트에 넣는다 */
    private String audienceLabel(EventContentAudience audience) {
        if (audience == null) {
            return "행사 참가자 전체";
        }
        return switch (audience) {
            case EXHIBITOR -> "이 행사에 부스를 낸 참가기업 담당자";
            case VISITOR -> "이 행사에 방문하는 관람객";
            case ALL -> "행사 참가자 전체 (참가기업과 관람객 모두)";
        };
    }

    private String taskInstruction(EventContentAiDtos.GenerateRequest request) {
        boolean isResource = request.contentType() == EventContentType.RESOURCE;

        return switch (request.action()) {
            case GENERATE -> isResource ? """
                    <request>의 내용을 바탕으로 자료 제목과 설명을 새로 작성한다.
                    title과 content를 모두 채운다. 제목은 40자 이내로 만든다.

                    아래를 다룬다. <request>와 <event_info>에 근거가 없는 항목은 생략한다.
                    - 이 자료가 무엇인지
                    - 누가 언제 활용하는지
                    - 자료에 담긴 주요 내용
                    - 활용 시 유의할 점
                    - 문의 방법""" : """
                    <request>의 내용을 바탕으로 행사 공지사항 제목과 본문을 새로 작성한다.
                    title과 content를 모두 채운다. 제목은 40자 이내로 만든다.

                    아래를 다룬다. <request>와 <event_info>에 근거가 없는 항목은 생략한다.
                    - 공지 목적
                    - 일정 (날짜·시간)
                    - 대상
                    - 변경 사항
                    - 주의사항
                    - 읽는 사람이 해야 할 행동
                    - 문의 방법

                    배경 설명과 유의사항, 안내 문구는 문맥에 맞게 채워 넣어도 된다.""";
            case TITLE_SUGGEST -> """
                    <current_content>를 읽고 제목 후보를 %d개 이내로 제안한다.
                    후보끼리 서로 다른 관점을 잡는다. 예: 일정 중심 / 대상 중심 / 조치 중심.
                    표현만 조금 바꾼 비슷한 제목을 여러 개 내놓지 않는다. 각 제목은 40자 이내로 만든다.
                    행사명을 제목에 그대로 넣지 않는다. 목록에서 이미 행사가 구분되기 때문이다.
                    titleSuggestions 배열에만 담고 title과 content는 빈 값으로 둔다."""
                    .formatted(ContentAiResultParser.MAX_TITLE_SUGGESTIONS);
            case POLISH -> """
                    <current_content>의 문장을 자연스럽고 읽기 좋게 다듬는다.
                    의미와 문단·목록 구조는 그대로 두고 표현만 고친다.
                    원본에 없는 내용을 새로 더하지 않는다. content에 결과를 담는다.""";
            case SUMMARIZE -> """
                    <current_content>를 원본의 3분의 1 수준으로 줄인다.
                    일정·대상·읽는 사람이 해야 할 행동은 반드시 남기고, 배경 설명과 인사말은 덜어낸다.
                    원본에 없는 내용을 새로 더하지 않는다. content에 결과를 담는다.""";
            case EXPAND -> """
                    <current_content>를 원본보다 확실히 길고 충실하게 다시 쓴다.
                    분량은 원본의 1.5~2배를 목표로 한다. 원본과 비슷한 길이로 끝내면 실패한 작업이다.

                    아래 항목을 보강한다. 원본과 <event_info>에 근거가 없는 항목은 넣지 않는다.
                    - 이 글을 쓰는 배경과 이유
                    - 읽는 사람이 처한 상황에 맞춘 안내
                    - 진행 중 · 이후에 유의할 점
                    - 미리 준비해두면 좋은 것
                    - 문의 방법

                    배경 설명과 안내 문구는 문맥에 맞게 채워도 되지만,
                    같은 말을 바꿔 쓰거나 '양해 부탁드립니다' 같은 상투적인 문장으로 분량을 채우지 않는다.
                    문단을 나누고, 항목이 여러 개면 <ul>로 정리한다. content에 결과를 담는다.""";
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

    private String formatDate(OffsetDateTime value) {
        return value == null ? "미정" : value.format(DATE_FORMAT);
    }

    private boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /**
     * EventContentService.create와 동일한 권한 검증.
     * 공지·자료를 등록할 수 있는 사람만 AI를 쓸 수 있어야, 남의 행사에 비용을 발생시키지 못한다.
     */
    private void requireEventManager(Long eventId, AuthenticatedMemberDto actor) {
        if (actor == null) {
            throw new BusinessException(GlobalErrorCode.UNAUTHORIZED);
        }
        if (actor.getPlatformRole() != PlatformRole.PLATFORM_ADMIN
                && !eventMemberRepository.existsByEventIdAndMemberIdAndEventRoleAndActiveTrue(
                        eventId, actor.getMemberId(), EventRole.EVENT_MANAGER)) {
            throw new BusinessException(GlobalErrorCode.FORBIDDEN);
        }
    }
}
