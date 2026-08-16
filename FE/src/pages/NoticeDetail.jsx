import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import RichTextViewer from "../components/RichTextViewer.jsx";
import { ApiError } from "../api/apiClient.js";
import { platformNoticeApi } from "../api/platformNoticeApi.js";

// 공지사항 상세 페이지
// 본문은 관리자가 리치 텍스트 에디터로 작성한 HTML이라 RichTextViewer로 렌더링한다.

const formatDate = (value) =>
  value ? new Date(value).toLocaleDateString("ko-KR", { dateStyle: "long" }) : "";

// 주소를 직접 고쳐 /notices/abc 로 들어올 수 있다.
// 서버로 보내면 400 오류 화면이 뜨므로, 숫자가 아니면 요청하지 않고 없는 공지로 처리한다.
const isValidNoticeId = (value) => /^\d+$/.test(value || "");

export default function NoticeDetail() {
  const { noticeId } = useParams();
  const [searchParams] = useSearchParams();

  const [notice, setNotice] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  // 목록에서 넘어올 때 실어 보낸 페이지·검색어를 그대로 돌려준다
  const listSearch = searchParams.toString();
  const listPath = `/notices${listSearch ? `?${listSearch}` : ""}`;

  useEffect(() => {
    let cancelled = false;

    if (!isValidNoticeId(noticeId)) {
      setNotice(null);
      setError("존재하지 않거나 삭제된 공지사항입니다.");
      setLoading(false);
      return undefined;
    }

    setLoading(true);
    platformNoticeApi.detail(noticeId)
      .then((response) => {
        if (!cancelled) {
          setNotice(response?.data || null);
          setError("");
        }
      })
      .catch((requestError) => {
        if (cancelled) return;
        // 삭제되었거나 없는 공지는 별도 안내를 보여준다
        setError(
          requestError instanceof ApiError && requestError.status === 404
            ? "존재하지 않거나 삭제된 공지사항입니다."
            : (requestError.message || "공지사항을 불러오지 못했습니다."),
        );
      })
      .finally(() => { if (!cancelled) setLoading(false); });
    return () => { cancelled = true; };
  }, [noticeId]);

  return (
    <div className="min-h-screen bg-surface-pearl flex flex-col">
      <TopNav active="notices" />
      <main className="flex-1 w-full max-w-[900px] mx-auto px-lg pt-[76px] pb-3xl">
        <Link
          to={listPath}
          className="inline-flex items-center gap-xxs text-caption text-ink-muted hover:text-primary-focus transition-colors mb-md"
        >
          <Icon name="arrow_back" className="text-[16px]" />
          공지사항
        </Link>

        {loading && <p className="text-caption text-ink-muted py-xxl text-center">공지사항을 불러오는 중...</p>}

        {!loading && error && (
          <div className="py-xxl flex flex-col items-center gap-md text-center">
            <Icon name="error" className="text-[40px] text-ink-muted opacity-40" />
            <p className="text-caption text-ink-muted">{error}</p>
            <Link
              to={listPath}
              className="px-lg py-sm rounded-xl border border-hairline bg-white text-caption hover:bg-surface-pearl transition-colors"
            >
              목록으로
            </Link>
          </div>
        )}

        {!loading && !error && notice && (
          <>
            <article className="bg-white border border-hairline rounded-2xl overflow-hidden">
              <header className="px-lg py-lg border-b border-hairline">
                {notice.pinned && (
                  <span className="inline-flex items-center gap-xxs px-xs py-xxs mb-xs rounded-full bg-primary-focus/10 text-primary-focus text-[11px] font-body-strong">
                    <Icon name="push_pin" className="text-[12px]" />
                    공지
                  </span>
                )}
                <h1 className="text-[22px] font-body-strong leading-snug break-words">{notice.title}</h1>
                <p className="text-caption text-ink-muted mt-xs">
                  {formatDate(notice.publishedAt)}
                  {/* 수정된 공지는 최초 게시일과 구분해 표시한다 */}
                  {notice.updatedAt && notice.updatedAt !== notice.publishedAt && (
                    <span className="ml-sm">({formatDate(notice.updatedAt)} 수정)</span>
                  )}
                </p>
              </header>

              <div className="px-lg py-lg">
                {notice.content
                  ? <RichTextViewer html={notice.content} />
                  : <p className="text-caption text-ink-muted">내용이 없습니다.</p>}
              </div>
            </article>

            <div className="flex justify-center mt-lg">
              <Link
                to={listPath}
                className="px-xl py-sm rounded-xl border border-hairline bg-white text-caption hover:bg-surface-pearl transition-colors"
              >
                목록
              </Link>
            </div>
          </>
        )}
      </main>
      <Footer />
    </div>
  );
}
