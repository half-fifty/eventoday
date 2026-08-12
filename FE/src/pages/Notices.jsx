import { useEffect, useState } from "react";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import { platformNoticeApi } from "../api/platformNoticeApi.js";

// 공지사항 페이지
// 플랫폼 관리자가 관리자센터에서 등록한 "사이트 공지"만 표시한다.
// 행사별 공지·자료는 각 행사 상세 페이지에서 확인한다.
export default function Notices() {
  const [notices, setNotices] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [expandedId, setExpandedId] = useState(null);

  // 서버가 pinned DESC, publishedAt DESC로 정렬해 내려주므로 그대로 사용한다
  useEffect(() => {
    let cancelled = false;
    platformNoticeApi.list()
      .then((response) => {
        if (!cancelled) setNotices(response?.data || []);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message || "공지사항을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, []);

  return (
    <div className="min-h-screen bg-surface-pearl flex flex-col">
      <TopNav active="notices" />
      <main className="flex-1 w-full max-w-[900px] mx-auto px-lg pt-[76px] pb-3xl">
        <h1 className="font-display-lg text-[28px] mb-xs">공지사항</h1>
        <p className="text-caption text-ink-muted mb-lg">EVENTODAY 서비스 운영 공지를 확인하세요.</p>

        {loading && <p className="text-caption text-ink-muted py-xl text-center">공지사항을 불러오는 중...</p>}
        {error && <p className="text-caption text-error bg-error/10 rounded-lg p-md">{error}</p>}

        {!loading && !error && notices.length === 0 && (
          <p className="text-caption text-ink-muted py-xl text-center">등록된 공지사항이 없습니다.</p>
        )}

        {!loading && notices.length > 0 && (
          <div className="bg-white border border-hairline rounded-2xl divide-y divide-divider-soft overflow-hidden">
            {notices.map((notice) => (
              <div key={notice.noticeId}>
                <button
                  onClick={() => setExpandedId(expandedId === notice.noticeId ? null : notice.noticeId)}
                  className="w-full flex items-center gap-sm p-lg text-left hover:bg-surface-pearl/50 transition-colors"
                >
                  <Icon name="campaign" className="text-[18px] text-ink-muted flex-shrink-0" />
                  <div className="flex-1 min-w-0">
                    <p className="font-body-strong text-[14px] truncate">
                      {/* 상단 고정 공지는 아이콘으로 구분한다 */}
                      {notice.pinned && <Icon name="push_pin" className="text-[13px] text-primary mr-1" />}
                      {notice.title}
                    </p>
                    <p className="text-caption text-ink-muted">
                      {notice.publishedAt ? new Date(notice.publishedAt).toLocaleDateString("ko-KR") : ""}
                    </p>
                  </div>
                  <Icon
                    name={expandedId === notice.noticeId ? "expand_less" : "expand_more"}
                    className="text-ink-muted text-[18px]"
                  />
                </button>
                {expandedId === notice.noticeId && notice.content && (
                  <div className="px-lg pb-lg">
                    <p className="text-caption whitespace-pre-line bg-surface-pearl rounded-lg p-md">{notice.content}</p>
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
