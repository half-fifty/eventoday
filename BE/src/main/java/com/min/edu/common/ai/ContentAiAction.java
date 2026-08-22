package com.min.edu.common.ai;

/**
 * AI 작성 보조 동작.
 * GENERATE·TITLE_SUGGEST 외에는 모두 "현재 본문을 다시 쓰는" 동작이라 본문이 필수다.
 */
public enum ContentAiAction {

    /** 간단한 설명으로 공지 전체(제목+본문) 초안 작성 */
    GENERATE(false, true),

    /** 본문을 분석해 제목 후보 추천 */
    TITLE_SUGGEST(true, false),

    /** 문장을 자연스럽게 다듬기 */
    POLISH(true, false),

    /** 핵심 위주로 요약 */
    SUMMARIZE(true, false),

    /** 짧은 내용을 자연스럽게 확장 */
    EXPAND(true, false),

    /** 맞춤법·문법 교정 */
    PROOFREAD(true, false),

    /** 지정한 문체로 변경 */
    TONE(true, false);

    private final boolean contentRequired;
    private final boolean promptRequired;

    ContentAiAction(boolean contentRequired, boolean promptRequired) {
        this.contentRequired = contentRequired;
        this.promptRequired = promptRequired;
    }

    /** 현재 작성 중인 본문이 있어야 실행할 수 있는 동작인지 */
    public boolean isContentRequired() {
        return contentRequired;
    }

    /** 관리자가 입력한 설명이 있어야 실행할 수 있는 동작인지 */
    public boolean isPromptRequired() {
        return promptRequired;
    }

    /** 제목 후보를 여러 개 돌려주는 동작인지 */
    public boolean isTitleSuggestion() {
        return this == TITLE_SUGGEST;
    }
}
