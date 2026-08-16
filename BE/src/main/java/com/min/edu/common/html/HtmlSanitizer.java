package com.min.edu.common.html;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;
import org.jsoup.Jsoup;
import org.jsoup.nodes.Document;
import org.jsoup.nodes.Element;
import org.jsoup.safety.Safelist;

/**
 * 리치 텍스트 에디터로 작성된 HTML을 저장 전에 정제한다 (XSS 방지).
 *
 * 에디터 본문은 사용자가 입력한 HTML을 그대로 보관했다가 화면에 렌더링하므로,
 * 저장 시점에 허용 태그·속성만 남기고 나머지를 제거해야 한다.
 * 프론트엔드 검증만으로는 API를 직접 호출하는 요청을 막을 수 없어 서버에서 정제한다.
 *
 * 화이트리스트 방식이라 목록에 없는 태그·속성은 모두 제거된다.
 * script·iframe·object 같은 태그와 onclick 등 이벤트 속성,
 * javascript: 프로토콜 링크가 여기서 걸러진다.
 */
public final class HtmlSanitizer {

    private HtmlSanitizer() {}

    /**
     * style 속성에서 허용할 CSS 속성.
     * 에디터가 실제로 생성하는 것만 허용한다 (정렬·글자 크기·표 열 너비).
     * style을 통째로 허용하면 background:url(...) 같은 우회 경로가 남는다.
     */
    private static final List<String> ALLOWED_STYLE_PROPERTIES =
            List.of("text-align", "font-size", "width", "min-width");

    /**
     * style 값 허용 문자.
     * 괄호와 콜론을 막아 url(...) · expression(...) · javascript: 형태를 원천 차단한다.
     */
    private static final Pattern SAFE_STYLE_VALUE = Pattern.compile("^[a-zA-Z0-9.%#\\- ]+$");

    /**
     * 링크(href)에 허용할 URL 형식.
     *
     * 첨부 이미지 URL이 /api/v1/files/{id}/download 같은 루트 상대 경로라서 상대 경로를 허용해야 하는데,
     * Jsoup의 프로토콜 제한(addProtocols)은 상대 경로를 무조건 제거한다. 그래서 직접 검사한다.
     * 시작 문자열을 고정해 javascript: · data: 와 //evil.com 형태의 프로토콜 상대 URL을 막는다.
     */
    private static final Pattern SAFE_LINK_URL =
            Pattern.compile("^(?:https?://|mailto:|/(?!/))\\S*$", Pattern.CASE_INSENSITIVE);

    /**
     * 이미지(src)에 허용할 URL 형식 — 이 서비스의 파일 다운로드 경로만.
     *
     * 링크와 달리 이미지는 페이지를 여는 순간 브라우저가 자동으로 요청을 보낸다.
     * 외부 주소를 허용하면 공지를 읽는 방문자의 IP·User-Agent가 제3자 서버로 새어 나간다.
     * 에디터도 업로드한 파일만 삽입하므로 실제 사용에는 제약이 없다.
     *
     * 앞부분을 느슨하게 둔 이유: 배포 환경에 따라 API 주소가 /api 같은 상대 경로일 수도,
     * https://호스트/api 같은 절대 주소일 수도 있어 양쪽을 모두 받아야 한다.
     * 외부 CDN 이미지가 필요해지면 여기에 도메인 허용 목록을 추가한다.
     */
    private static final Pattern SAFE_IMAGE_URL =
            Pattern.compile("^(?:https?://[^/\\s]+)?(?:/\\S*)?/v1/files/\\d+/download$",
                    Pattern.CASE_INSENSITIVE);

    private static final Safelist SAFELIST = buildSafelist();

    /**
     * HTML을 정제해 반환한다.
     * 내용이 없으면 null을 반환해 "본문 없음"을 한 가지 값으로 표현한다.
     */
    public static String sanitize(String rawHtml) {
        if (rawHtml == null || rawHtml.isBlank()) {
            return null;
        }

        // 1차: 허용 태그·속성만 남긴다
        Document document = Jsoup.parseBodyFragment(Jsoup.clean(rawHtml, "", SAFELIST));

        // 2차: 살아남은 속성의 값을 검사한다 (Safelist는 속성 존재 여부만 보고 값은 검사하지 못한다)
        filterStyleAttributes(document);
        filterUrlAttributes(document);

        String sanitized = document.body().html().trim();
        return sanitized.isBlank() ? null : sanitized;
    }

    private static Safelist buildSafelist() {
        return Safelist.none()
                // 블록·인라인 서식
                .addTags("h1", "h2", "h3", "h4", "p", "br", "hr", "div", "span",
                        "strong", "b", "em", "i", "u", "s", "code", "pre", "blockquote")
                // 목록
                .addTags("ul", "ol", "li")
                // 링크·이미지
                .addTags("a", "img")
                // 표
                .addTags("table", "thead", "tbody", "tr", "th", "td", "colgroup", "col")
                .addAttributes("a", "href", "title", "target")
                .addAttributes("img", "src", "alt", "width", "height")
                .addAttributes("table", "style")
                .addAttributes("col", "style")
                .addAttributes("td", "colspan", "rowspan", "colwidth", "style")
                .addAttributes("th", "colspan", "rowspan", "colwidth", "style")
                // 정렬·글자 크기는 style로 표현되므로 해당 태그에만 허용한다
                .addAttributes("p", "style")
                .addAttributes("span", "style")
                .addAttributes("h1", "style")
                .addAttributes("h2", "style")
                .addAttributes("h3", "style")
                .addAttributes("h4", "style")
                // href·src 값 검사는 상대 경로를 살리기 위해 filterUrlAttributes에서 직접 수행한다
                // target="_blank" 링크의 탭 탈취(reverse tabnabbing)를 막는다
                .addEnforcedAttribute("a", "rel", "noopener noreferrer nofollow");
    }

    /**
     * href·src 값을 검사한다. 이미지는 링크보다 엄격한 기준을 쓴다 (SAFE_IMAGE_URL 주석 참고).
     * 링크는 주소만 지우고 글자는 남기지만, 주소가 없는 이미지는 의미가 없어 요소째로 제거한다.
     */
    private static void filterUrlAttributes(Document document) {
        for (Element anchor : document.select("a[href]")) {
            if (!SAFE_LINK_URL.matcher(anchor.attr("href")).matches()) {
                anchor.removeAttr("href");
            }
        }
        for (Element image : document.select("img")) {
            if (!SAFE_IMAGE_URL.matcher(image.attr("src")).matches()) {
                image.remove();
            }
        }
    }

    private static void filterStyleAttributes(Document document) {
        for (Element element : document.select("[style]")) {
            String filtered = filterStyleValue(element.attr("style"));
            if (filtered.isEmpty()) {
                element.removeAttr("style");
            } else {
                element.attr("style", filtered);
            }
        }
    }

    /** style 속성 문자열에서 허용 목록에 있고 값 형식이 안전한 선언만 남긴다 */
    private static String filterStyleValue(String rawStyle) {
        StringBuilder builder = new StringBuilder();
        for (String declaration : rawStyle.split(";")) {
            int separatorIndex = declaration.indexOf(':');
            if (separatorIndex < 0) {
                continue;
            }
            String property = declaration.substring(0, separatorIndex).trim().toLowerCase(Locale.ROOT);
            String value = declaration.substring(separatorIndex + 1).trim();

            if (!ALLOWED_STYLE_PROPERTIES.contains(property)) {
                continue;
            }
            if (!SAFE_STYLE_VALUE.matcher(value).matches()) {
                continue;
            }
            builder.append(property).append(':').append(value).append(';');
        }
        return builder.toString();
    }
}
