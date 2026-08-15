import { useCallback, useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import { platformNoticeApi } from "../api/platformNoticeApi.js";

// 공지사항 목록 페이지
// 플랫폼 관리자가 관리자센터에서 등록한 "사이트 공지"만 표시한다.
// 행사별 공지·자료는 각 행사 상세 페이지에서 확인한다.
//
// 페이지 번호와 검색어를 쿼리스트링에 두어 새로고침·뒤로가기에도 목록 상태가 유지되게 한다.
// 상세 페이지에서 목록으로 돌아올 때 보던 페이지가 그대로 열리는 것도 같은 이유다.

const PAGE_SIZE = 15;

const formatDate = (value) =>
  value ? new Date(value).toLocaleDateString("ko-KR", { dateStyle: "medium" }) : "";

export default function Notices() {
  const [searchParams, setSearchParams] = useSearchParams();
  const page = Number(searchParams.get("page")) || 0;
  const keyword = searchParams.get("keyword") || "";

  // 입력 중인 검색어는 확정(제출) 전까지 URL에 반영하지 않는다
  const [keywordInput, setKeywordInput] = useState(keyword);
  const [notices, setNotices] = useState([]);
  const [totalPages, setTotalPages] = useState(0);
  const [totalElements, setTotalElements] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // 늦게 도착한 이전 조회가 최신 목록을 덮어쓰지 않도록 한다 (EventList.load와 동일한 방식)
  const requestSequence = useRef(0);

  useEffect(() => { setKeywordInput(keyword); }, [keyword]);

  const load = useCallback(async () => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    try {
      const response = await platformNoticeApi.list({ page, size: PAGE_SIZE, keyword });
      if (sequence !== requestSequence.current) return;
      const data = response?.data;
      setNotices(data?.content || []);
      setTotalPages(data?.totalPages || 0);
      setTotalElements(data?.totalElements || 0);
      setError("");
    } catch (requestError) {
      if (sequence !== requestSequence.current) return;
      setError(requestError.message || "공지사항을 불러오지 못했습니다.");
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  }, [page, keyword]);

  useEffect(() => { load(); }, [load]);

  // 검색어가 바뀌면 첫 페이지부터 다시 본다
  const handleSearch = (event) => {
    event.preventDefault();
    const nextKeyword = keywordInput.trim();
    setSearchParams(nextKeyword ? { keyword: nextKeyword } : {});
  };

  const handleReset = () => {
    setKeywordInput("");
    setSearchParams({});
  };

  const goToPage = (nextPage) => {
    const params = {};
    if (keyword) params.keyword = keyword;
    if (nextPage > 0) params.page = String(nextPage);
    setSearchParams(params);
    window.scrollTo({ top: 0, behavior: "smooth" });
  };

  // 상세 페이지에서 목록으로 돌아올 때 현재 페이지·검색어를 유지하기 위해 함께 넘긴다
  const detailSearch = searchParams.toString();
  const detailPath = (noticeId) =>
    `/notices/${noticeId}${detailSearch ? `?${detailSearch}` : ""}`;

  return (
    <div className="min-h-screen bg-surface-pearl flex flex-col">
      <TopNav active="notices" />
      <main className="flex-1 w-full max-w-[900px] mx-auto px-lg pt-[76px] pb-3xl">
        <h1 className="font-display-lg text-[28px] mb-xs">공지사항</h1>
        <p className="text-caption text-ink-muted">EVENTODAY 서비스 운영 공지를 확인하세요.</p>

        {/* 검색 */}
        <form onSubmit={handleSearch} className="flex gap-xs mt-lg mb-md">
          <div className="relative flex-1">
            <Icon
              name="search"
              className="absolute left-sm top-1/2 -translate-y-1/2 text-[18px] text-ink-muted pointer-events-none"
            />
            <input
              type="search"
              value={keywordInput}
              onChange={(event) => setKeywordInput(event.target.value)}
              placeholder="제목 또는 내용으로 검색"
              aria-label="공지사항 검색"
              className="w-full h-[42px] pl-[38px] pr-md rounded-xl border border-hairline bg-white text-caption focus:outline-none focus:border-primary-focus transition-colors"
            />
          </div>
          <button
            type="submit"
            className="px-lg h-[42px] rounded-xl bg-primary-focus text-white text-caption font-body-strong hover:opacity-90 transition-opacity"
          >
            검색
          </button>
        </form>

        {keyword && !loading && !error && (
          <p className="text-caption text-ink-muted mb-sm">
            &lsquo;{keyword}&rsquo; 검색 결과 {totalElements}건
            <button type="button" onClick={handleReset} className="ml-sm text-primary-focus underline">
              전체 보기
            </button>
          </p>
        )}

        {loading && <p className="text-caption text-ink-muted py-xxl text-center">공지사항을 불러오는 중...</p>}
        {error && <p className="text-caption text-error bg-error/10 rounded-lg p-md">{error}</p>}

        {/* 빈 상태 */}
        {!loading && !error && notices.length === 0 && (
          <div className="py-xxl flex flex-col items-center gap-sm text-center">
            <Icon name="campaign" className="text-[40px] text-ink-muted opacity-40" />
            <p className="text-caption text-ink-muted">
              {keyword ? "검색 결과가 없습니다." : "등록된 공지사항이 없습니다."}
            </p>
            {keyword && (
              <button type="button" onClick={handleReset} className="text-caption text-primary-focus underline">
                전체 공지사항 보기
              </button>
            )}
          </div>
        )}

        {/* 게시판형 목록 */}
        {!loading && !error && notices.length > 0 && (
          <div className="bg-white border border-hairline rounded-2xl overflow-hidden">
            {/* 표 머리글 — 좁은 화면에서는 숨긴다 */}
            <div className="hidden sm:flex items-center gap-md px-lg py-sm bg-surface-pearl border-b border-hairline text-caption text-ink-muted">
              <span className="flex-1">제목</span>
              <span className="w-[100px] text-right">작성일</span>
            </div>

            <ul className="divide-y divide-divider-soft">
              {notices.map((notice) => (
                <li key={notice.noticeId}>
                  <Link
                    to={detailPath(notice.noticeId)}
                    className="flex flex-col sm:flex-row sm:items-center gap-xxs sm:gap-md px-lg py-md hover:bg-surface-pearl/60 transition-colors"
                  >
                    <span className="flex-1 min-w-0 flex items-center gap-xs">
                      {/* 상단 고정 공지는 배지로 구분한다 */}
                      {notice.pinned && (
                        <span className="flex-shrink-0 inline-flex items-center gap-xxs px-xs py-xxs rounded-full bg-primary-focus/10 text-primary-focus text-[11px] font-body-strong">
                          <Icon name="push_pin" className="text-[12px]" />
                          공지
                        </span>
                      )}
                      <span className="truncate text-[15px]">{notice.title}</span>
                    </span>
                    <span className="w-[100px] text-caption text-ink-muted sm:text-right">
                      {formatDate(notice.publishedAt)}
                    </span>
                  </Link>
                </li>
              ))}
            </ul>
          </div>
        )}

        {/* 페이지 이동 (EventList와 동일한 형태) */}
        {!loading && totalPages > 1 && (
          <div className="flex justify-center items-center gap-sm mt-xl">
            <button
              type="button"
              disabled={page === 0}
              onClick={() => goToPage(page - 1)}
              className="px-md py-sm border border-hairline rounded-full disabled:opacity-40 text-caption"
            >
              이전
            </button>
            <span className="text-caption text-ink-muted">{page + 1} / {totalPages}</span>
            <button
              type="button"
              disabled={page + 1 >= totalPages}
              onClick={() => goToPage(page + 1)}
              className="px-md py-sm border border-hairline rounded-full disabled:opacity-40 text-caption"
            >
              다음
            </button>
          </div>
        )}
      </main>
      <Footer />
    </div>
  );
}
