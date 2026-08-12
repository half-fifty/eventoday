import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import FileDownloadLink from "../components/FileDownloadLink.jsx";
import { listAllContents } from "../api/contentApi.js";
import { platformNoticeApi } from "../api/platformNoticeApi.js";

// 전체 공지사항 페이지
// CONTENT-API-006(전체 공지 목록)으로 공개 행사의 공지·자료를 페이지 단위로 조회한다.
// (기존: 행사 목록 조회 후 행사별 N번 병렬 호출 → BE API 추가로 대체)
const PAGE_SIZE = 20;

// 응답 항목 { eventName, content: {...} } → 렌더링 편의를 위해 평탄화
const flatten = (list) => (list || []).map((item) => ({ ...item.content, eventName: item.eventName }));

export default function Notices() {
  const [items, setItems] = useState([]); // { ...content, eventName }
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [expandedId, setExpandedId] = useState(null);
  const [typeFilter, setTypeFilter] = useState(""); // "" | "NOTICE" | "RESOURCE"

  // 사이트 전체 공지 (플랫폼 관리자 등록) - 행사 공지와 출처가 달라 별도 상태로 관리한다
  const [siteNotices, setSiteNotices] = useState([]);
  const [expandedSiteId, setExpandedSiteId] = useState(null);

  // 페이지네이션 - 필터가 바뀌면 첫 페이지부터 다시 조회
  const [page, setPage] = useState(0);
  const [hasMore, setHasMore] = useState(false);
  const [loadingMore, setLoadingMore] = useState(false);

  // 필터 변경 시 진행 중인 loadMore 결과를 무효화하기 위한 버전 카운터
  // typeFilter가 바뀌면 ref 값이 증가하고, 이전 요청은 버전 불일치로 상태를 덮어쓰지 않는다
  const requestVersionRef = useRef(0);

  useEffect(() => {
    let cancelled = false;
    const requestVersion = ++requestVersionRef.current; // 이 필터 호출의 버전
    const load = async () => {
      setLoading(true);
      setError("");
      setPage(0);
      setHasMore(false);
      setExpandedId(null);
      try {
        // 유형 필터는 BE에서 처리 (전체를 받아와 클라이언트에서 거르지 않는다)
        const data = await listAllContents({
          contentType: typeFilter || undefined,
          page: 0,
          size: PAGE_SIZE,
        });
        if (cancelled) return;
        // 더 최신 필터 요청이 시작됐으면 이 결과는 무시
        if (requestVersion !== requestVersionRef.current) return;
        // BE가 pinned DESC, publishedAt DESC로 정렬해 내려준다
        setItems(flatten(data?.content));
        setHasMore(data ? !data.last : false);
      } catch (requestError) {
        if (!cancelled) setError(requestError.message || "공지사항을 불러오지 못했습니다.");
      } finally {
        if (!cancelled) setLoading(false);
      }
    };
    load();
    return () => { cancelled = true; };
  }, [typeFilter]);

  // 사이트 공지는 유형 필터와 무관하므로 최초 1회만 조회한다.
  // 실패해도 행사 공지 목록 표시를 막지 않도록 에러는 무시하고 빈 목록으로 둔다.
  useEffect(() => {
    let cancelled = false;
    platformNoticeApi.list()
      .then((response) => {
        if (!cancelled) setSiteNotices(response?.data || []);
      })
      .catch(() => {
        if (!cancelled) setSiteNotices([]);
      });
    return () => { cancelled = true; };
  }, []);

  // 다음 페이지 이어붙이기
  const loadMore = async () => {
    if (loadingMore || !hasMore) return;
    const nextPage = page + 1;
    // 요청 시점의 필터 버전 캡처 — 응답 도착 전 필터가 바뀌었으면 결과를 버린다
    const requestVersion = requestVersionRef.current;
    setLoadingMore(true);
    try {
      const data = await listAllContents({
        contentType: typeFilter || undefined,
        page: nextPage,
        size: PAGE_SIZE,
      });
      if (requestVersion !== requestVersionRef.current) return;
      setItems((prev) => [...prev, ...flatten(data?.content)]);
      setHasMore(data ? !data.last : false);
      setPage(nextPage);
    } catch (requestError) {
      setError(requestError.message || "공지사항을 더 불러오지 못했습니다.");
    } finally {
      setLoadingMore(false);
    }
  };

  // 필터링은 BE에서 처리하므로 그대로 사용
  const filtered = items;

  return (
    <div className="min-h-screen bg-surface-pearl flex flex-col">
      <TopNav active="notices" />
      <main className="flex-1 w-full max-w-[900px] mx-auto px-lg pt-[76px] pb-3xl">
        <h1 className="font-display-lg text-[28px] mb-xs">공지사항</h1>
        <p className="text-caption text-ink-muted mb-lg">진행 중인 행사들의 공지와 자료를 한곳에서 확인하세요.</p>

        {/* 사이트 공지: 플랫폼 관리자가 등록한 전체 공지.
            자료만 보는 중일 때는 성격이 달라 숨긴다. */}
        {typeFilter !== "RESOURCE" && siteNotices.length > 0 && (
          <section className="mb-lg">
            <h2 className="mb-sm flex items-center gap-xs font-body-strong text-[15px]">
              <Icon name="campaign" className="text-[18px] text-primary" /> 사이트 공지
            </h2>
            <div className="overflow-hidden rounded-2xl border border-primary/20 bg-primary/5 divide-y divide-primary/10">
              {siteNotices.map((notice) => (
                <div key={notice.noticeId}>
                  <button
                    onClick={() => setExpandedSiteId(expandedSiteId === notice.noticeId ? null : notice.noticeId)}
                    className="flex w-full items-center gap-sm p-lg text-left transition-colors hover:bg-primary/5"
                  >
                    <div className="min-w-0 flex-1">
                      <p className="truncate font-body-strong text-[14px]">
                        {notice.pinned && <Icon name="push_pin" className="mr-1 text-[13px] text-primary" />}
                        {notice.title}
                      </p>
                      <p className="text-caption text-ink-muted">
                        {notice.publishedAt ? new Date(notice.publishedAt).toLocaleDateString("ko-KR") : ""}
                      </p>
                    </div>
                    <Icon
                      name={expandedSiteId === notice.noticeId ? "expand_less" : "expand_more"}
                      className="text-[18px] text-ink-muted"
                    />
                  </button>
                  {expandedSiteId === notice.noticeId && notice.content && (
                    <div className="px-lg pb-lg">
                      <p className="whitespace-pre-line rounded-lg bg-white p-md text-caption">{notice.content}</p>
                    </div>
                  )}
                </div>
              ))}
            </div>
          </section>
        )}

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
            {/* 다음 페이지 이어붙이기 (서버 응답의 last 기준) */}
            {hasMore && (
              <button
                type="button"
                onClick={loadMore}
                disabled={loadingMore}
                className="w-full p-md text-caption text-primary hover:bg-surface-pearl/50 transition-colors disabled:opacity-50"
              >
                {loadingMore ? "불러오는 중..." : "더 보기"}
              </button>
            )}
          </div>
        )}
      </main>
      <Footer />
    </div>
  );
}
