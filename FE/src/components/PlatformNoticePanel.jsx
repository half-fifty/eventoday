import { useCallback, useEffect, useRef, useState } from "react";
import Icon from "./Icon.jsx";
import RichTextEditor from "./RichTextEditor.jsx";
import { ApiError } from "../api/apiClient.js";
import { platformNoticeApi } from "../api/platformNoticeApi.js";
import useModalFocusTrap from "../hooks/useModalFocusTrap.js";

// 플랫폼 관리자센터 - 사이트 전체 공지 관리 패널
// 행사별 공지(ContentManagementPanel)와 달리 행사에 소속되지 않는 사이트 공지를 다룬다.
// PlatformAdmin.jsx가 이미 길어 다른 관리 패널(AdminExchangeCodeRequestPanel)처럼 별도 컴포넌트로 분리한다.
//
// 본문은 리치 텍스트 에디터로 작성하며 HTML로 저장된다. 저장 시 서버에서 정제한다.

const EMPTY_FORM = { title: "", content: "", pinned: false };

const PAGE_SIZE = 10;

const formatDateTime = (value) =>
  value ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-";

const toErrorMessage = (err, fallback) =>
  err instanceof ApiError ? `${err.code}: ${err.message}` : (err?.message || fallback);

// 목록 미리보기용 — 본문이 HTML이라 태그를 걷어내고 글자만 남긴다.
// DOMParser는 문서를 파싱만 하고 스크립트를 실행하지 않는다.
const toPreviewText = (html) => {
  if (!html) return "";
  const text = new DOMParser().parseFromString(html, "text/html").body.textContent || "";
  return text.replace(/\s+/g, " ").trim();
};

export default function PlatformNoticePanel() {
  const [notices, setNotices] = useState([]);
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState(null); // null이면 신규 등록
  const [form, setForm] = useState(EMPTY_FORM);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");

  const [deleteTarget, setDeleteTarget] = useState(null);
  const [deleting, setDeleting] = useState(false);

  // 목록 조회 세대 카운터 — 늦게 도착한 이전 조회가 최신 상태를 덮어쓰는 것을 막는다
  const listGenerationRef = useRef(0);

  // 목록은 서버가 pinned DESC, publishedAt DESC로 정렬해 내려주므로 그대로 사용한다.
  // 페이지 번호는 EventList.load와 동일하게 인자로 받아, 상태 변경이 조회를 다시 부르지 않게 한다.
  const loadNotices = useCallback(async (targetPage = 0, { showLoading = true } = {}) => {
    const generation = ++listGenerationRef.current;
    if (showLoading) setLoading(true);
    try {
      const response = await platformNoticeApi.list({ page: targetPage, size: PAGE_SIZE });
      if (generation !== listGenerationRef.current) return;
      const data = response?.data;
      setNotices(data?.content || []);
      setPage(data?.page ?? targetPage);
      setTotalPages(data?.totalPages || 0);
      setError("");
    } catch (err) {
      if (generation !== listGenerationRef.current) return;
      setError(toErrorMessage(err, "공지를 불러오지 못했습니다."));
    } finally {
      if (showLoading && generation === listGenerationRef.current) setLoading(false);
    }
  }, []);

  useEffect(() => { loadNotices(0); }, [loadNotices]);

  const openCreate = () => {
    setEditTarget(null);
    setForm(EMPTY_FORM);
    setFormError("");
    setFormOpen(true);
  };

  const openEdit = (notice) => {
    setEditTarget(notice);
    setForm({ title: notice.title || "", content: notice.content || "", pinned: notice.pinned ?? false });
    setFormError("");
    setFormOpen(true);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (submitting) return;
    if (!form.title.trim()) {
      setFormError("제목을 입력하세요.");
      return;
    }
    const payload = {
      title: form.title.trim(),
      content: form.content.trim() || null,
      pinned: form.pinned,
    };

    setSubmitting(true);
    setFormError("");
    try {
      if (editTarget) {
        await platformNoticeApi.update(editTarget.noticeId, payload);
        setMessage("공지를 수정했습니다.");
      } else {
        await platformNoticeApi.create(payload);
        setMessage("공지를 등록했습니다.");
      }
      setError("");
      setFormOpen(false);
      // 상단 고정 여부에 따라 순서가 바뀌므로 서버 정렬 결과를 다시 받아온다.
      // 새 공지는 첫 페이지 위쪽에 오므로 등록 후에는 첫 페이지로 돌아간다.
      await loadNotices(editTarget ? page : 0, { showLoading: false });
    } catch (err) {
      setFormError(toErrorMessage(err, "저장에 실패했습니다."));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    // 중복 클릭 시 DELETE가 두 번 나가 두 번째가 ENTITY_NOT_FOUND로 실패하는 것을 막는다
    if (!deleteTarget || deleting) return;
    setDeleting(true);
    try {
      await platformNoticeApi.remove(deleteTarget.noticeId);
      setMessage("공지를 삭제했습니다.");
      setError("");
      // 뒤 페이지의 항목이 앞으로 당겨지므로 목록을 다시 받아온다.
      // 마지막 항목을 지워 현재 페이지가 비면 이전 페이지로 이동한다.
      const isLastItemOnPage = notices.length === 1 && page > 0;
      await loadNotices(isLastItemOnPage ? page - 1 : page, { showLoading: false });
    } catch (err) {
      setError(toErrorMessage(err, "삭제에 실패했습니다."));
      setMessage("");
    } finally {
      setDeleting(false);
      setDeleteTarget(null);
    }
  };

  // 삭제 중에는 모달이 닫히지 않도록 한다 (Escape·취소 공통)
  const closeDeleteModal = useCallback(() => {
    if (!deleting) setDeleteTarget(null);
  }, [deleting]);
  const { panelRef: deleteModalRef, initialFocusRef: deleteCancelRef } =
    useModalFocusTrap(Boolean(deleteTarget), closeDeleteModal);

  return (
    <section className="space-y-lg">
      <div className="flex flex-wrap items-center justify-between gap-sm">
        <div>
          <h1 className="font-display-lg text-[26px]">공지 관리</h1>
          <p className="mt-xs text-caption text-ink-muted">
            사이트 전체에 표시되는 공지사항을 관리합니다. 행사별 공지는 개최자센터에서 등록합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={openCreate}
          className="inline-flex items-center gap-xs rounded-full bg-primary px-lg py-sm font-body-strong text-caption text-white"
        >
          <Icon name="add" /> 공지 등록
        </button>
      </div>

      {message && (
        <p className="rounded-xl border border-status-available/20 bg-status-available/10 px-md py-sm text-caption text-status-available">
          {message}
        </p>
      )}
      {error && (
        <p className="rounded-xl border border-error/20 bg-error/10 px-md py-sm text-caption text-error">
          {error}
        </p>
      )}

      {/* 등록·수정 인라인 폼 */}
      {formOpen && (
        <div className="space-y-md rounded-xl border border-primary/30 bg-primary/5 p-lg">
          <div className="flex items-center justify-between">
            <h2 className="font-body-strong">공지 {editTarget ? "수정" : "등록"}</h2>
            <button type="button" onClick={() => setFormOpen(false)} aria-label="폼 닫기">
              <Icon name="close" />
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-md">
            {formError && <p className="text-caption text-error">{formError}</p>}

            <div>
              <label htmlFor="platform-notice-title" className="mb-1 block text-caption text-ink-muted">제목 *</label>
              <input
                id="platform-notice-title"
                maxLength={200}
                value={form.title}
                onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))}
                className="w-full rounded-lg border border-hairline bg-white px-md py-sm text-caption focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                placeholder="제목을 입력하세요"
              />
            </div>

            <div>
              <span className="mb-1 block text-caption text-ink-muted">내용</span>
              {/* 서식·이미지·표가 들어간 공지를 작성할 수 있도록 리치 텍스트 에디터를 사용한다.
                  입력값은 HTML이며 저장 시 서버(HtmlSanitizer)가 허용 태그만 남긴다. */}
              <RichTextEditor
                value={form.content}
                onChange={(html) => setForm((p) => ({ ...p, content: html }))}
                disabled={submitting}
              />
            </div>

            <label className="flex cursor-pointer items-center gap-sm">
              <input
                type="checkbox"
                checked={form.pinned}
                onChange={(e) => setForm((p) => ({ ...p, pinned: e.target.checked }))}
                className="h-4 w-4 rounded border-hairline accent-primary"
              />
              <span className="text-caption">상단 고정</span>
            </label>

            <div className="flex justify-end gap-sm">
              <button
                type="button"
                onClick={() => setFormOpen(false)}
                className="rounded-full border border-hairline px-lg py-sm text-caption"
              >취소</button>
              <button
                type="submit"
                disabled={submitting}
                className="rounded-full bg-primary px-lg py-sm font-body-strong text-caption text-white disabled:opacity-40"
              >
                {submitting ? "저장 중..." : editTarget ? "수정" : "등록"}
              </button>
            </div>
          </form>
        </div>
      )}

      {/* 공지 목록 */}
      <div className="overflow-hidden rounded-xl border border-hairline bg-white">
        {loading ? (
          <p className="p-xl text-center text-caption text-ink-muted">공지를 불러오는 중입니다.</p>
        ) : notices.length === 0 ? (
          <p className="p-xl text-center text-caption text-ink-muted">등록된 사이트 공지가 없습니다.</p>
        ) : (
          <div className="divide-y divide-divider-soft">
            {notices.map((notice) => (
              <div key={notice.noticeId} className="flex items-start gap-md p-lg">
                <div className="min-w-0 flex-1">
                  {notice.pinned && (
                    <span className="mb-1 inline-flex items-center gap-xs rounded-full bg-primary/10 px-sm py-[2px] text-[11px] text-primary">
                      <Icon name="push_pin" className="text-[12px]" /> 고정
                    </span>
                  )}
                  <p className="truncate font-body-strong text-[14px]">{notice.title}</p>
                  {/* 본문이 HTML이라 태그를 걷어낸 글자만 미리보기로 보여준다 */}
                  {notice.content && (
                    <p className="mt-1 line-clamp-2 text-caption text-ink-muted">{toPreviewText(notice.content)}</p>
                  )}
                  <p className="mt-1 text-[11px] text-ink-muted">게시 {formatDateTime(notice.publishedAt)}</p>
                </div>
                <div className="flex shrink-0 gap-sm">
                  <button
                    type="button"
                    onClick={() => openEdit(notice)}
                    className="rounded-lg border border-hairline px-sm py-xs text-caption hover:bg-surface-container"
                  >수정</button>
                  <button
                    type="button"
                    onClick={() => setDeleteTarget(notice)}
                    className="rounded-lg border border-error/30 px-sm py-xs text-caption text-error hover:bg-error/10"
                  >삭제</button>
                </div>
              </div>
            ))}
          </div>
        )}
      </div>

      {/* 페이지 이동 */}
      {!loading && totalPages > 1 && (
        <div className="flex items-center justify-center gap-sm">
          <button
            type="button"
            disabled={page === 0}
            onClick={() => loadNotices(page - 1)}
            className="rounded-full border border-hairline px-md py-sm text-caption disabled:opacity-40"
          >이전</button>
          <span className="text-caption text-ink-muted">{page + 1} / {totalPages}</span>
          <button
            type="button"
            disabled={page + 1 >= totalPages}
            onClick={() => loadNotices(page + 1)}
            className="rounded-full border border-hairline px-md py-sm text-caption disabled:opacity-40"
          >다음</button>
        </div>
      )}

      {/* 삭제 확인 모달 */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-lg">
          <div
            ref={deleteModalRef}
            tabIndex={-1}
            role="dialog"
            aria-modal="true"
            aria-labelledby="platform-notice-delete-title"
            className="w-full max-w-sm rounded-2xl bg-white p-xl shadow-2xl"
          >
            <h3 id="platform-notice-delete-title" className="font-body-strong text-[16px]">삭제 확인</h3>
            <p className="mt-sm text-caption text-ink-muted">
              <span className="font-body-strong text-on-surface">&quot;{deleteTarget.title}&quot;</span>을(를) 삭제하시겠습니까?
            </p>
            <div className="mt-lg flex justify-end gap-sm">
              <button
                ref={deleteCancelRef}
                type="button"
                onClick={closeDeleteModal}
                disabled={deleting}
                className="rounded-full border border-hairline px-lg py-sm text-caption disabled:opacity-40"
              >취소</button>
              <button
                type="button"
                onClick={handleDelete}
                disabled={deleting}
                className="rounded-full bg-error px-lg py-sm font-body-strong text-caption text-white disabled:opacity-40"
              >{deleting ? "삭제 중..." : "삭제"}</button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
