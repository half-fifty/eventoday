package com.min.edu.common.ai;

/** 공지 문체. 프롬프트에 넣을 지시문을 각 상수가 직접 들고 있다. */
public enum ContentAiTone {

    // 문체 지시가 추상적이면 모델이 어느 문체를 골라도 비슷한 글을 내놓는다.
    // 어미와 쓰지 말 표현까지 지정해 결과가 실제로 갈리도록 한다.
    FORMAL("""
            격식 있는 공식 안내문 문체(합쇼체).
            어미는 '~합니다', '~드립니다', '~하시기 바랍니다'를 쓴다.
            '~해요', '~할게요' 같은 해요체는 절대 쓰지 않는다."""),

    FRIENDLY("""
            부드러운 대화체(해요체). 공식 문체와 확실히 구분되게 쓴다.
            어미는 '~해요', '~드릴게요', '~해 주세요', '~예정이에요'를 쓴다.
            '~하시기 바랍니다', '~드립니다', '~하오니' 같은 공문서 표현은 쓰지 않는다.
            한자어보다 쉬운 말을 고른다 (예: '양지' 대신 '참고', '금일' 대신 '오늘').
            다만 이모지와 감탄사는 쓰지 않고 과장하지 않는다. 공지의 신뢰감은 유지한다."""),

    CONCISE("""
            짧고 명확한 문체. 한 문장을 40자 이내로 끊는다.
            수식어와 인사말을 빼고 사실만 남긴다. 항목이 둘 이상이면 목록으로 정리한다."""),

    GUIDE("""
            안내문 문체. <h3> 소제목으로 '일정', '대상', '유의사항' 등을 나누고
            각 항목을 <ul> 목록으로 정리해 훑어보기 쉽게 만든다.
            어미는 '~합니다' 체를 쓴다.""");

    private final String instruction;

    ContentAiTone(String instruction) {
        this.instruction = instruction;
    }

    public String getInstruction() {
        return instruction;
    }

    /** 미지정 시 기본 문체 — 공지사항의 기본 성격에 맞춰 공식적 문체를 쓴다 */
    public static ContentAiTone orDefault(ContentAiTone tone) {
        return tone == null ? FORMAL : tone;
    }
}
