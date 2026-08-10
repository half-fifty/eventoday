import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import FileDownloadLink from "../components/FileDownloadLink.jsx";
import { eventApi } from "../api/eventApi.js";
import { listContents } from "../api/contentApi.js";

// 전체 공지사항 페이지
// BE에 전체 공지 API가 없으므로 행사 목록을 불러온 뒤
// 행사별 공지·자료 API(CONTENT-API-001)를 병렬 호출해서 모아 보여준다.
export default function Notices() {
  const [items, setItems] = useState([]); // { ...content, eventId, eventName }
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
        // 공개된 행사 목록 조회 (최신 시작일 순, 최대 50개)
        const result = await eventApi.list({ page: 0, size: 50, sort: "startAt,desc" });
        const events = result?.data?.content || [];

        // 행사별 공지·자료를 병렬 조회 (실패한 행사는 빈 배열 처리)
        // 행사 목록 응답의 식별자 필드는 id (EventList.jsx 참고)
        const contentsPerEvent = await Promise.all(
          events.map((event) =>
            listContents(event.id)
              .then((list) => (list || []).map((c) => ({ ...c, eventId: event.id, eventName: event.name })))
              .catch(() => [])
          )
        );

        if (cancelled) return;
        const merged = contentsPerEvent.flat();
        // 고정 공지 우선, 그 다음 최신 게시일 순 정렬
        merged.sort((a, b) => (b.pinned - a.pinned) || new Date(b.publishedAt || 0) - new Date(a.publishedAt || 0));
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
                    {content.fileId && <FileDownloadLink fileId={content.fileId} fileName="첨부파일 다운로드" />}
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
