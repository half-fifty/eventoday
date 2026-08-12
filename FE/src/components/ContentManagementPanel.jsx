import { useEffect, useRef, useState } from "react";
import FileDownloadLink from "./FileDownloadLink.jsx";
import Icon from "./Icon.jsx";
import { ApiError } from "../api/apiClient.js";
import { createContent, deleteContent, listContents, updateContent } from "../api/contentApi.js";

// CONTENT-API-001/003/004/005 기반 공지·자료 관리 패널
// 관련 요구사항: CONTENT-001~006
// - 응답 DTO(EventContentDtos.Summary) 식별자는 contentId, 게시 시각은 publishedAt
// - 등록·수정 요청은 contentType·audience·title 이 서버 필수값(@NotNull/@NotBlank)

const TABS = [
  { key: "NOTICE", label: "공지사항", icon: "campaign" },   // CONTENT-001
  { key: "RESOURCE", label: "자료 보관실", icon: "folder" }, // CONTENT-002
];

// CONTENT-003 공개 대상 (EventContentAudience enum과 값이 일치해야 한다)
const AUDIENCE_LABEL = { ALL: "전체", EXHIBITOR: "참가기업", VISITOR: "관람객" };
const AUDIENCE_OPTIONS = Object.keys(AUDIENCE_LABEL);

// CONTENT-004 자료 분류 (resourceType, DB VARCHAR(30) / @Size(max=30))
const RESOURCE_TYPE_OPTIONS = ["견적서", "신청서", "운영자료", "홍보자료", "안내문"];

// 탭 전환·재조회 시 매번 초기화하는 폼 기본값
const EMPTY_FORM = {
  title: "",
  content: "",
  audience: "ALL",
  resourceType: "",
  version: "",
  pinned: false,
};

const formatDateTime = (value) =>
  value ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-";

// 서버 정렬(pinned DESC, publishedAt DESC)과 동일한 기준으로 낙관적 갱신 후 재정렬한다
const sortContents = (list) =>
  (list || []).slice().sort((a, b) => {
    if (b.pinned !== a.pinned) return b.pinned ? 1 : -1;
    return new Date(b.publishedAt) - new Date(a.publishedAt);
  });

// FloorplanManagementPanel과 동일한 에러 메시지 규칙
const toErrorMessage = (err, fallback) =>
  err instanceof ApiError ? `${err.code}: ${err.message}` : (err?.message || fallback);

export default function ContentManagementPanel({ eventId }) {
  const [tab, setTab] = useState("NOTICE");
  const [items, setItems] = useState([]);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  // 등록·수정 폼
  const [formOpen, setFormOpen] = useState(false);
  const [editTarget, setEditTarget] = useState(null); // null이면 신규 등록
  const [form, setForm] = useState(EMPTY_FORM);
  const [file, setFile] = useState(null);
  const [submitting, setSubmitting] = useState(false);
  const [formError, setFormError] = useState("");
  const fileInputRef = useRef(null);

  const [deleteTarget, setDeleteTarget] = useState(null);

  // 탭 또는 행사가 바뀌면 목록을 다시 조회한다.
  // contentType 필터는 서버(CONTENT-API-001)에서 처리한다 — 전체를 받아 클라이언트에서 거르지 않는다.
  useEffect(() => {
    setFormOpen(false);
    setMessage("");
    setError("");
    setItems([]);
    if (!eventId) return;

    let active = true;
    setLoading(true);
    listContents(eventId, tab)
      .then((data) => {
        if (active) setItems(sortContents(data));
      })
      .catch((err) => {
        if (active) setError(toErrorMessage(err, "공지·자료를 불러오지 못했습니다."));
      })
      .finally(() => {
        if (active) setLoading(false);
      });
    return () => { active = false; };
  }, [eventId, tab]);

  const resetFileInput = () => {
    setFile(null);
    if (fileInputRef.current) fileInputRef.current.value = "";
  };

  const openCreate = () => {
    setEditTarget(null);
    setForm(EMPTY_FORM);
    resetFileInput();
    setFormError("");
    setFormOpen(true);
  };

  const openEdit = (item) => {
    setEditTarget(item);
    setForm({
      title: item.title || "",
      content: item.content || "",
      audience: item.audience || "ALL",
      resourceType: item.resourceType || "",
      version: item.version || "",
      pinned: item.pinned ?? false,
    });
    resetFileInput();
    setFormError("");
    setFormOpen(true);
  };

  const handleSubmit = async (event) => {
    event.preventDefault();
    if (!form.title.trim()) {
      setFormError("제목을 입력하세요.");
      return;
    }

    // 서버 DTO(CreateRequest/UpdateRequest)와 동일한 형태로 구성한다.
    // contentType·audience는 수정 시에도 필수값이므로 항상 함께 보낸다.
    const payload = {
      contentType: tab,
      audience: form.audience,
      title: form.title.trim(),
      content: form.content.trim() || null,
      // 자료 분류·버전은 자료 보관실에서만 사용한다
      resourceType: tab === "RESOURCE" ? (form.resourceType || null) : null,
      version: tab === "RESOURCE" ? (form.version.trim() || null) : null,
      pinned: form.pinned,
    };
    // 첨부파일은 자료 보관실에서만 전송한다
    const attachment = tab === "RESOURCE" ? (file || undefined) : undefined;

    setSubmitting(true);
    setFormError("");
    try {
      if (editTarget) {
        const updated = await updateContent(editTarget.contentId, payload, attachment);
        setItems((prev) =>
          sortContents(prev.map((item) => (item.contentId === editTarget.contentId ? updated : item)))
        );
        setMessage("수정되었습니다.");
      } else {
        const created = await createContent(eventId, payload, attachment);
        setItems((prev) => sortContents([created, ...prev]));
        setMessage("등록되었습니다.");
      }
      setFormOpen(false);
      resetFileInput();
    } catch (err) {
      setFormError(toErrorMessage(err, "저장에 실패했습니다."));
    } finally {
      setSubmitting(false);
    }
  };

  const handleDelete = async () => {
    if (!deleteTarget) return;
    try {
      await deleteContent(deleteTarget.contentId);
      setItems((prev) => prev.filter((item) => item.contentId !== deleteTarget.contentId));
      setMessage("삭제되었습니다.");
    } catch (err) {
      setError(toErrorMessage(err, "삭제에 실패했습니다."));
    } finally {
      setDeleteTarget(null);
    }
  };

  const tabLabel = tab === "NOTICE" ? "공지" : "자료";

  return (
    <section className="space-y-lg">
      <div className="flex flex-wrap items-center justify-between gap-sm">
        <div>
          <h1 className="font-display-lg text-[26px]">공지·자료 관리</h1>
          <p className="mt-xs text-caption text-ink-muted">
            행사 공지사항과 운영 자료를 등록하고 공개 대상을 관리합니다.
          </p>
        </div>
        <button
          type="button"
          onClick={openCreate}
          disabled={!eventId}
          className="inline-flex items-center gap-xs rounded-full bg-primary px-lg py-sm font-body-strong text-caption text-white disabled:opacity-40"
        >
          <Icon name="add" /> {tabLabel} 등록
        </button>
      </div>

      {/* 공지사항(CONTENT-001) / 자료 보관실(CONTENT-002) 탭 */}
      <div className="flex gap-lg border-b border-hairline">
        {TABS.map(({ key, label, icon }) => (
          <button
            key={key}
            type="button"
            onClick={() => setTab(key)}
            className={`inline-flex items-center gap-xs border-b-2 pb-sm text-caption font-body-strong transition-colors ${
              tab === key
                ? "border-primary text-primary"
                : "border-transparent text-ink-muted hover:text-on-surface"
            }`}
          >
            <Icon name={icon} className="text-[16px]" /> {label}
          </button>
        ))}
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
            <h2 className="font-body-strong">{tabLabel} {editTarget ? "수정" : "등록"}</h2>
            <button type="button" onClick={() => setFormOpen(false)} aria-label="폼 닫기">
              <Icon name="close" />
            </button>
          </div>

          <form onSubmit={handleSubmit} className="space-y-md">
            {formError && <p className="text-caption text-error">{formError}</p>}

            {/* 제목 + 공개 대상(CONTENT-003)을 한 줄에 배치 (좁은 화면에서는 세로로 쌓임) */}
            <div className="grid gap-md sm:grid-cols-2">
              <div>
                <label htmlFor="content-title" className="mb-1 block text-caption text-ink-muted">제목 *</label>
                <input
                  id="content-title"
                  maxLength={200}
                  className="w-full rounded-lg border border-hairline bg-white px-md py-sm text-caption focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                  value={form.title}
                  onChange={(e) => setForm((p) => ({ ...p, title: e.target.value }))}
                  placeholder="제목을 입력하세요"
                />
              </div>
              <div>
                <label htmlFor="content-audience" className="mb-1 block text-caption text-ink-muted">공개 대상 *</label>
                <select
                  id="content-audience"
                  value={form.audience}
                  onChange={(e) => setForm((p) => ({ ...p, audience: e.target.value }))}
                  className="w-full rounded-lg border border-hairline bg-white px-md py-sm text-caption"
                >
                  {AUDIENCE_OPTIONS.map((value) => (
                    <option key={value} value={value}>{AUDIENCE_LABEL[value]}</option>
                  ))}
                </select>
              </div>
            </div>

            <div>
              <label htmlFor="content-body" className="mb-1 block text-caption text-ink-muted">내용</label>
              <textarea
                id="content-body"
                className="min-h-[120px] w-full resize-y rounded-lg border border-hairline bg-white px-md py-sm text-caption focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                value={form.content}
                onChange={(e) => setForm((p) => ({ ...p, content: e.target.value }))}
                placeholder="내용을 입력하세요"
              />
            </div>

            {/* CONTENT-004·CONTENT-006: 자료 분류·버전·첨부파일은 자료 보관실에서만 사용 */}
            {tab === "RESOURCE" && (
              <>
                <div className="grid gap-md sm:grid-cols-2">
                  <div>
                    <label htmlFor="content-resource-type" className="mb-1 block text-caption text-ink-muted">자료 분류</label>
                    <select
                      id="content-resource-type"
                      value={form.resourceType}
                      onChange={(e) => setForm((p) => ({ ...p, resourceType: e.target.value }))}
                      className="w-full rounded-lg border border-hairline bg-white px-md py-sm text-caption"
                    >
                      <option value="">선택 안 함</option>
                      {RESOURCE_TYPE_OPTIONS.map((value) => (
                        <option key={value} value={value}>{value}</option>
                      ))}
                    </select>
                  </div>
                  <div>
                    <label htmlFor="content-version" className="mb-1 block text-caption text-ink-muted">자료 버전</label>
                    <input
                      id="content-version"
                      maxLength={20}
                      value={form.version}
                      onChange={(e) => setForm((p) => ({ ...p, version: e.target.value }))}
                      className="w-full rounded-lg border border-hairline bg-white px-md py-sm text-caption focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary"
                      placeholder="예: 1.0"
                    />
                  </div>
                </div>

                <div>
                  <label className="mb-1 block text-caption text-ink-muted">첨부파일 (선택)</label>
                  <input
                    ref={fileInputRef}
                    type="file"
                    className="hidden"
                    onChange={(e) => setFile(e.target.files?.[0] || null)}
                  />
                  <div className="flex items-center gap-sm">
                    <button
                      type="button"
                      onClick={() => fileInputRef.current?.click()}
                      className="inline-flex items-center gap-xs rounded-lg border border-hairline bg-white px-md py-sm text-caption hover:bg-surface-container"
                    >
                      <Icon name="attach_file" className="text-[16px]" />
                      <span className="max-w-[220px] truncate">{file ? file.name : "파일 선택"}</span>
                    </button>
                    {file && (
                      <button type="button" onClick={resetFileInput} aria-label="첨부파일 제거" className="text-error">
                        <Icon name="close" className="text-[16px]" />
                      </button>
                    )}
                  </div>
                  {/* 수정 시 파일을 고르지 않으면 기존 첨부가 그대로 유지된다 */}
                  {editTarget?.fileId && !file && (
                    <p className="mt-1 text-[11px] text-ink-muted">
                      새 파일을 선택하지 않으면 기존 첨부파일이 유지됩니다.
                    </p>
                  )}
                </div>
              </>
            )}

            {/* CONTENT-005: 상단 고정 */}
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

      {/* 목록 (CONTENT-API-001) */}
      {loading && <p className="py-xl text-center text-caption text-ink-muted">불러오는 중입니다.</p>}
      {!loading && items.length === 0 && (
        <p className="py-xl text-center text-caption text-ink-muted">등록된 {tabLabel}가 없습니다.</p>
      )}
      {!loading && items.length > 0 && (
        <div className="divide-y divide-divider-soft overflow-hidden rounded-xl border border-hairline bg-white">
          {items.map((item) => (
            <div key={item.contentId} className="flex items-start gap-md p-lg">
              <div className="min-w-0 flex-1">
                {/* 배지: 상단 고정 · 공개 대상 · 자료 분류 · 버전 */}
                <div className="mb-1 flex flex-wrap items-center gap-xs">
                  {item.pinned && (
                    <span className="inline-flex items-center gap-xs rounded-full bg-primary/10 px-sm py-[2px] text-[11px] text-primary">
                      <Icon name="push_pin" className="text-[12px]" /> 고정
                    </span>
                  )}
                  <span className="rounded-full bg-surface-container px-sm py-[2px] text-[11px] text-ink-muted">
                    {AUDIENCE_LABEL[item.audience] || item.audience}
                  </span>
                  {item.resourceType && (
                    <span className="rounded-full bg-surface-container px-sm py-[2px] text-[11px] text-ink-muted">
                      {item.resourceType}
                    </span>
                  )}
                  {item.version && (
                    <span className="text-[11px] text-ink-muted">v{item.version}</span>
                  )}
                </div>

                <p className="truncate font-body-strong text-[14px]">{item.title}</p>
                {item.content && (
                  <p className="mt-1 line-clamp-2 whitespace-pre-line text-caption text-ink-muted">{item.content}</p>
                )}

                {/* CONTENT-006/007: 첨부파일 다운로드 (권한 검증은 BE FileService에서 수행) */}
                {item.fileId && (
                  <div className="mt-sm">
                    <FileDownloadLink
                      fileId={item.fileId}
                      fileName={item.fileName || "첨부파일"}
                      fileSize={item.fileSize}
                    />
                  </div>
                )}

                <p className="mt-1 text-[11px] text-ink-muted">{formatDateTime(item.publishedAt)}</p>
              </div>

              <div className="flex shrink-0 gap-sm">
                <button
                  type="button"
                  onClick={() => openEdit(item)}
                  className="rounded-lg border border-hairline px-sm py-xs text-caption hover:bg-surface-container"
                >수정</button>
                <button
                  type="button"
                  onClick={() => setDeleteTarget(item)}
                  className="rounded-lg border border-error/30 px-sm py-xs text-caption text-error hover:bg-error/10"
                >삭제</button>
              </div>
            </div>
          ))}
        </div>
      )}

      {/* 삭제 확인 모달 (CONTENT-API-005) */}
      {deleteTarget && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40">
          <div className="w-full max-w-sm rounded-2xl bg-white p-xl shadow-2xl">
            <h3 className="font-body-strong text-[16px]">삭제 확인</h3>
            <p className="mt-sm text-caption text-ink-muted">
              <span className="font-body-strong text-on-surface">&quot;{deleteTarget.title}&quot;</span>을(를) 삭제하시겠습니까?
            </p>
            <div className="mt-lg flex justify-end gap-sm">
              <button
                type="button"
                onClick={() => setDeleteTarget(null)}
                className="rounded-full border border-hairline px-lg py-sm text-caption"
              >취소</button>
              <button
                type="button"
                onClick={handleDelete}
                className="rounded-full bg-error px-lg py-sm font-body-strong text-caption text-white"
              >삭제</button>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
