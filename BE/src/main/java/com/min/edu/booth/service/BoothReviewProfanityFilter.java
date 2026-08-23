package com.min.edu.booth.service;

import java.util.Set;
import org.springframework.stereotype.Component;

// 리뷰 작성 시점에 거는 1차 방어선. 로컬 문자열 매칭이라 AI 판별(BoothReviewAiModerationService)보다
// 빠르고 항상 동작하지만(외부 API 장애 영향 없음), 우회(자모 분리, 특수문자 삽입 등)에는 약하다.
//
// 아래 목록은 동작 확인용 시작 세트일 뿐이다. 실제 운영에서는 이 목록을 DB 테이블로 옮겨
// 운영자가 직접 추가/삭제할 수 있게 하는 걸 권장한다.
@Component
public class BoothReviewProfanityFilter {

    private static final Set<String> BANNED_WORDS = Set.of(
            "시발", "씨발", "개새끼", "병신", "지랄", "좆", "꺼져", "죽어라", "미친놈", "미친년"
    );

    public boolean containsBannedWord(String text) {
        if (text == null || text.isBlank()) {
            return false;
        }
        String normalized = text.replaceAll("\\s+", "");
        return BANNED_WORDS.stream().anyMatch(normalized::contains);
    }
}
