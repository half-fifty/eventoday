import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import FileDownloadLink from "../components/FileDownloadLink.jsx";
import { listAllContents } from "../api/contentApi.js";

// 전체 공지사항 페이지
// CONTENT-API-006(전체 공지 목록)으로 1회 호출해 공개 행사의 공지·자료를 모아 보여준다.
// (기존: 행사 목록 조회 후 행사별 N번 병렬 호출 → BE API 추가로 대체)
export default function Notices() {
  const [items, setItems] = useState([]); // { ...content, eventName }
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [expandedId, setExpandedId] = useState(null);
  const [typeFilter, setTypeFilter] = useState(""); // "" | "NOTICE" | "RESOURCE"

  useEffect(() => {
    let cancelled = false;
    const load = async () => {
      setLoading(true);
      setError("");
      try {
        // 응답 항목: { eventName, content: {...} } → 렌더링 편의를 위해 평탄화
        const list = await listAllContents();
        if (cancelled) return;
        const merged = (list || []).map((item) => ({ ...item.content, eventName: item.eventName }));
        // BE가 pinned·publishedAt 순으로 정렬해 주지만, 행사 간 병합 순서 보장을 위해 한 번 더 정렬
        // pinned가 undefined면 뺄셈 결과가 NaN이 되므로 boolean → 숫자로 정규화
        merged.sort((a, b) =>
          (Number(Boolean(b.pinned)) - Number(Boolean(a.pinned)))
          || new Date(b.publishedAt || 0) - new Date(a.publishedAt || 0));
        setItems(merged);
      } catch (requestError) {
        if (!cancelled) setError(requestError.message || "공지사항을 불러오지 못했습니다.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => { cancelled = true; };
  }, []);

  const filtered = typeFilter ? items.filter((c) => c.contentType === typeFilter) : items;

  return (
    <div className="min-h-screen bg-surface-pearl flex flex-col">
      <TopNav active="notices" />
      <main className="flex-1 w-full max-w-[900px] mx-auto px-lg pt-[76px] pb-3xl">
        <h1 className="font-display-lg text-[28px] mb-xs">공지사항</h1>
        <p className="text-caption text-ink-muted mb-lg">진행 중인 행사들의 공지와 자료를 한곳에서 확인하세요.</p>

        {/* 유형 필터: 전체 / 공지 / 자료 */}
        <div className="flex gap-sm mb-lg">
          {[["", "전체"], ["NOTICE", "공지"], ["RESOURCE", "자료"]].map(([value, label]) => (
            <button
              key={value}
              onClick={() => setTypeFilter(value)}
              className={`px-md py-xs rounded-full text-caption border transition-colors ${
                typeFilter === value
                  ? "bg-primary text-white border-primary"
                  : "bg-white text-on-surface border-hairline hover:bg-surface-pearl"
              }`}
            >
              {label}
            </button>
          ))}
        </div>

        {loading && <p className="text-caption text-ink-muted py-xl text-center">공지사항을 불러오는 중...</p>}
        {error && <p className="text-caption text-error bg-error/10 rounded-lg p-md">{error}</p>}

        {!loading && !error && filtered.length === 0 && (
          <p className="text-caption text-ink-muted py-xl text-center">등록된 공지사항이 없습니다.</p>
        )}

        {!loading && filtered.length > 0 && (
          <div className="bg-white border border-hairline rounded-2xl divide-y divide-divider-soft overflow-hidden">
            {filtered.map((content) => (
              <div key={content.contentId}>
                <button
                  onClick={() => setExpandedId(expandedId === content.contentId ? null : content.contentId)}
                  className="w-full flex items-center gap-sm p-lg text-left hover:bg-surface-pearl/50 transition-colors"
                >
                  <Icon
                    name={content.contentType === "NOTICE" ? "campaign" : "folder"}
                    className="text-[18px] text-ink-muted flex-shrink-0"
                  />
                  <div className="flex-1 min-w-0">
                    <p className="font-body-strong text-[14px] truncate">
                      {content.pinned && <Icon name="push_pin" className="text-[13px] text-primary mr-1" />}
                      {content.title}
                    </p>
                    <p className="text-caption text-ink-muted truncate">
                      {content.eventName}
                      {" · "}
                      {content.contentType === "NOTICE" ? "공지" : "자료"}
                      {content.version ? ` · v${content.version}` : ""}
                      {content.publishedAt ? ` · ${new Date(content.publishedAt).toLocaleDateString("ko-KR")}` : ""}
                    </p>
                  </div>
                  <Icon name={expandedId === content.contentId ? "expand_less" : "expand_more"} className="text-ink-muted text-[18px]" />
                </button>
                {expandedId === content.contentId && (
                  <div className="px-lg pb-lg space-y-sm">
                    {content.content && (
                      <p className="text-caption whitespace-pre-line bg-surface-pearl rounded-lg p-md">{content.content}</p>
                    )}
                    {/* fileName·fileSize: BE Summary에 포함된 원본 파일명·크기 (다운로드 파일명으로 사용) */}
                    {content.fileId && (
                      <FileDownloadLink
                        fileId={content.fileId}
                        fileName={content.fileName || "첨부파일"}
                        fileSize={content.fileSize}
                      />
                    )}
                    {/* 해당 행사 상세로 이동 */}
                    <Link
                      to={`/events/${content.eventId}`}
                      className="inline-flex items-center gap-xs text-caption text-primary hover:underline"
                    >
                      <Icon name="event" className="text-[14px]" /> 행사 상세 보기
                    </Link>
                  </div>
                )}
              </div>
            ))}
          </div>
        )}
      </main>
      <Footer />
    </div>
  );
}
