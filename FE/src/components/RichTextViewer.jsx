import { useMemo } from "react";
import DOMPurify from "dompurify";

// 리치 텍스트 본문 렌더링 컴포넌트
//
// 본문은 서버(HtmlSanitizer)에서 이미 정제해 저장하지만, 화면에 넣기 직전에 한 번 더 정제한다.
// 정제 전에 저장된 데이터나 다른 경로로 들어온 값이 그대로 렌더링되는 것을 막기 위한 이중 방어다.
// 스타일은 index.css의 .rich-text 규칙을 따른다.

// 서버 Safelist와 동일한 허용 목록 (BE/common/html/HtmlSanitizer.java)
const ALLOWED_TAGS = [
  "h1", "h2", "h3", "h4", "p", "br", "hr", "div", "span",
  "strong", "b", "em", "i", "u", "s", "code", "pre", "blockquote",
  "ul", "ol", "li", "a", "img",
  "table", "thead", "tbody", "tr", "th", "td", "colgroup", "col",
];

const ALLOWED_ATTR = [
  "href", "title", "target", "rel",
  "src", "alt", "width", "height",
  "style", "colspan", "rowspan", "colwidth",
];

// 링크는 서버에서 rel을 붙여 저장하지만, 정제 전에 저장된 데이터에도 적용되도록 여기서 한 번 더 보정한다
DOMPurify.addHook("afterSanitizeAttributes", (node) => {
  if (node.tagName === "A" && node.getAttribute("target") === "_blank") {
    node.setAttribute("rel", "noopener noreferrer nofollow");
  }
});

// 태그가 하나라도 있으면 HTML로 취급한다
const looksLikeHtml = (value) => /<\/?[a-z][\s\S]*>/i.test(value);

export default function RichTextViewer({ html, className = "" }) {
  const isHtml = useMemo(() => looksLikeHtml(html || ""), [html]);

  const sanitized = useMemo(
    () => (html && isHtml ? DOMPurify.sanitize(html, { ALLOWED_TAGS, ALLOWED_ATTR }) : ""),
    [html, isHtml],
  );

  if (!html) return null;

  // 리치 텍스트 도입 전에 저장된 공지는 평문이라, HTML로 렌더링하면 줄바꿈이 사라진다.
  // 태그가 없으면 평문으로 보고 줄바꿈을 유지한다 (데이터 마이그레이션 없이 기존 공지를 그대로 보여주기 위함)
  if (!isHtml) {
    return <div className={`whitespace-pre-line ${className}`}>{html}</div>;
  }

  return (
    <div
      className={`rich-text ${className}`}
      // eslint-disable-next-line react/no-danger -- 위에서 DOMPurify로 정제한 값만 전달한다
      dangerouslySetInnerHTML={{ __html: sanitized }}
    />
  );
}
