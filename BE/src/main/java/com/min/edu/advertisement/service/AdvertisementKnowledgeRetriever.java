package com.min.edu.advertisement.service;

import com.min.edu.event.domain.Event;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import org.jsoup.Jsoup;
import org.springframework.stereotype.Component;

@Component
public class AdvertisementKnowledgeRetriever {
    private static final int DEFAULT_LIMIT = 6;
    private static final List<KnowledgeDocument> POLICY_DOCUMENTS = List.of(
            new KnowledgeDocument("policy-facts", "광고 정책 · 사실성",
                    "행사 정보에서 확인되지 않은 할인, 혜택, 수치, 프로그램을 광고 문구에 추가하지 않는다.", 20),
            new KnowledgeDocument("policy-length", "광고 정책 · 문구 길이",
                    "광고 문구는 300자 이내로 작성하고 방문객이 핵심 정보를 한눈에 이해할 수 있어야 한다.", 18),
            new KnowledgeDocument("policy-claims", "광고 정책 · 과장 표현",
                    "객관적인 근거가 없는 최고, 최대, 유일, 완전 보장 등의 최상급 및 보장 표현을 사용하지 않는다.", 19),
            new KnowledgeDocument("policy-privacy", "광고 정책 · 개인정보",
                    "연락처나 민감정보를 광고 문구에 노출하지 않고 행사 페이지의 공개 정보만 사용한다.", 17)
    );

    public RetrievalResult retrieve(Event event, String draft, String tone) {
        List<KnowledgeDocument> corpus = new ArrayList<>(eventDocuments(event));
        corpus.addAll(POLICY_DOCUMENTS);
        String query = String.join(" ", safe(event.getName()), safe(event.getEventType()),
                safe(draft), safe(tone));
        Set<String> terms = tokenize(query);

        List<ScoredDocument> ranked = corpus.stream()
                .map(document -> new ScoredDocument(document, score(document, terms)))
                .sorted(Comparator.comparingInt(ScoredDocument::score).reversed()
                        .thenComparing(item -> item.document().id()))
                .limit(DEFAULT_LIMIT)
                .toList();

        // 사실성·과장 정책은 초안 내용과 관계없이 항상 생성 근거에 포함한다.
        LinkedHashSet<KnowledgeDocument> selected = new LinkedHashSet<>();
        selected.add(POLICY_DOCUMENTS.get(0));
        selected.add(POLICY_DOCUMENTS.get(2));
        ranked.forEach(item -> selected.add(item.document()));
        List<KnowledgeDocument> documents = selected.stream().limit(DEFAULT_LIMIT).toList();
        return new RetrievalResult(documents, documents.stream().map(KnowledgeDocument::title).toList());
    }

    private List<KnowledgeDocument> eventDocuments(Event event) {
        List<KnowledgeDocument> documents = new ArrayList<>();
        add(documents, "event-summary", "행사 기본 정보",
                "행사명: " + safe(event.getName()) + ", 유형: " + safe(event.getEventType())
                        + ", 한 줄 소개: " + plain(event.getShortDescription()), 14);
        add(documents, "event-description", "행사 상세 소개", plain(event.getDescription()), 12);
        add(documents, "event-schedule", "행사 일정",
                safe(event.getStartAt()) + "부터 " + safe(event.getEndAt()) + "까지", 13);
        add(documents, "event-venue", "행사 장소",
                safe(event.getVenueName()) + " · " + safe(event.getAddress()) + " " + safe(event.getAddressDetail()), 11);
        BigDecimal price = event.getTicketPrice();
        add(documents, "event-price", "관람 가격",
                price == null ? "가격 정보 없음" : price.signum() == 0 ? "무료 입장" : price.toPlainString() + "원", 10);
        return documents;
    }

    private void add(List<KnowledgeDocument> documents, String id, String title, String content, int priority) {
        if (content != null && !content.isBlank() && !"정보 없음".equals(content.trim())) {
            documents.add(new KnowledgeDocument(id, title, content, priority));
        }
    }

    private int score(KnowledgeDocument document, Set<String> terms) {
        String haystack = normalize(document.title() + " " + document.content());
        int matches = terms.stream()
                .mapToInt(term -> haystack.contains(term) ? Math.min(8, term.length()) * 5 : 0)
                .sum();
        return document.priority() + matches;
    }

    private Set<String> tokenize(String value) {
        LinkedHashSet<String> terms = new LinkedHashSet<>();
        for (String token : normalize(value).split("[^0-9a-z가-힣]+")) {
            if (token.length() >= 2) terms.add(token);
        }
        return terms;
    }

    private String plain(Object value) {
        if (value == null) return "정보 없음";
        return Jsoup.parse(value.toString()).text();
    }

    private String safe(Object value) { return value == null ? "정보 없음" : value.toString(); }
    private String normalize(String value) { return safe(value).toLowerCase(Locale.ROOT).trim(); }

    public record KnowledgeDocument(String id, String title, String content, int priority) {}
    public record RetrievalResult(List<KnowledgeDocument> documents, List<String> sources) {
        public String asPromptContext() {
            StringBuilder context = new StringBuilder();
            for (int index = 0; index < documents.size(); index++) {
                KnowledgeDocument document = documents.get(index);
                context.append("[").append(index + 1).append("] ").append(document.title())
                        .append("\n").append(document.content()).append("\n");
            }
            return context.toString();
        }

        public String asEventFactContext() {
            return documents.stream()
                    .filter(document -> document.id().startsWith("event-"))
                    .map(KnowledgeDocument::content)
                    .reduce("", (left, right) -> left + "\n" + right);
        }
    }
    private record ScoredDocument(KnowledgeDocument document, int score) {}
}
