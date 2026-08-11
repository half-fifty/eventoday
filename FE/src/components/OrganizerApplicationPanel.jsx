import { useEffect, useRef, useState } from "react";
import { ApiError } from "../api/apiClient.js";
import {
  approveApplication,
  getApplicationDetail,
  getApplicationFiles,
  getEventApplications,
  rejectApplication,
  startApplicationReview,
} from "../api/boothApplicationApi.js";
import Icon from "./Icon.jsx";

const PAGE_SIZE = 20;
const EMPTY_FILTERS = { status: "", teamName: "", boothCode: "" };
const STATUS_LABEL = {
  SUBMITTED: "검토 대기",
  UNDER_REVIEW: "검토 중",
  APPROVED: "승인 완료",
  REJECTED: "반려",
  CANCELLED: "신청 취소",
};

const statusClass = (status) => {
  if (status === "APPROVED") return "bg-status-available/10 text-status-available";
  if (status === "REJECTED" || status === "CANCELLED") return "bg-status-visited/10 text-status-visited";
  return "bg-status-pending/10 text-status-pending";
};

const formatDate = (value) => value
  ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" })
  : "-";

export default function OrganizerApplicationPanel({ eventId, onDataChanged }) {
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [pageResult, setPageResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [selected, setSelected] = useState(null);
  const [files, setFiles] = useState([]);
  const [detailLoading, setDetailLoading] = useState(false);
  const [actionLoading, setActionLoading] = useState(false);
  const [rejectMode, setRejectMode] = useState(false);
  const [rejectionReason, setRejectionReason] = useState("");
  const [modalError, setModalError] = useState("");
  const listRequestRef = useRef(0);

  const loadApplications = async (
    id = eventId,
    requestedPage = page,
    requestedFilters = filters
  ) => {
    const requestId = ++listRequestRef.current;
    if (!id) {
      setPageResult(null);
      setLoading(false);
      return;
    }
    setLoading(true);
    setError("");
    try {
      const result = await getEventApplications(id, {
        ...requestedFilters,
        page: requestedPage,
        size: PAGE_SIZE,
      });
      if (requestId !== listRequestRef.current) return;
      setPageResult(result);
    } catch (requestError) {
      if (requestId !== listRequestRef.current) return;
      setPageResult(null);
      setError(requestError instanceof ApiError
        ? `${requestError.code}: ${requestError.message}`
        : "부스 신청서를 불러오지 못했습니다.");
    } finally {
      if (requestId === listRequestRef.current) setLoading(false);
    }
  };

  useEffect(() => {
    setFilters(EMPTY_FILTERS);
    setPage(0);
    setPageResult(null);
    setSelected(null);
    setFiles([]);
    setRejectMode(false);
    setRejectionReason("");
    setModalError("");
    if (eventId) loadApplications(eventId, 0, EMPTY_FILTERS);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  const handleSearch = (event) => {
    event.preventDefault();
    setPage(0);
    loadApplications(eventId, 0, filters);
  };

  const changePage = (nextPage) => {
    setPage(nextPage);
    loadApplications(eventId, nextPage, filters);
  };

  const openDetail = async (applicationId) => {
    setDetailLoading(true);
    setError("");
    setFiles([]);
    setRejectMode(false);
    setRejectionReason("");
    setModalError("");
    try {
      const [detail, attachments] = await Promise.all([
        getApplicationDetail(applicationId),
        getApplicationFiles(applicationId),
      ]);
      setSelected(detail);
      setFiles(attachments || []);
    } catch (requestError) {
      setError(requestError.message || "신청서 상세정보를 불러오지 못했습니다.");
    } finally {
      setDetailLoading(false);
    }
  };

  const finishAction = async (nextStatus) => {
    setSelected((previous) => previous ? { ...previous, status: nextStatus } : previous);
    setRejectMode(false);
    setRejectionReason("");
    setModalError("");
    await loadApplications();
    onDataChanged?.();
  };

  const closeDetail = () => {
    if (actionLoading) return;
    setSelected(null);
    setRejectMode(false);
    setRejectionReason("");
    setModalError("");
  };

  const startReview = async () => {
    if (!selected || actionLoading) return;
    setActionLoading(true);
    setModalError("");
    try {
      await startApplicationReview(selected.id);
      await finishAction("UNDER_REVIEW");
    } catch (requestError) {
      setModalError(requestError.message || "검토를 시작하지 못했습니다.");
    } finally {
      setActionLoading(false);
    }
  };

  const approve = async () => {
    if (!selected || actionLoading) return;
    setActionLoading(true);
    setModalError("");
    try {
      await approveApplication(selected.id);
      await finishAction("APPROVED");
    } catch (requestError) {
      setModalError(requestError.message || "신청을 승인하지 못했습니다.");
    } finally {
      setActionLoading(false);
    }
  };

  const reject = async () => {
    if (!selected || actionLoading) return;
    if (!rejectionReason.trim()) {
      setModalError("반려 사유를 입력해야 합니다.");
      return;
    }
    setActionLoading(true);
    setModalError("");
    try {
      await rejectApplication(selected.id, rejectionReason.trim());
      await finishAction("REJECTED");
    } catch (requestError) {
      setModalError(requestError.message || "신청을 반려하지 못했습니다.");
    } finally {
      setActionLoading(false);
    }
  };

  if (!eventId) return null;

  const applications = pageResult?.content || [];

  return (
    <section className="space-y-lg">
      <div>
        <h1 className="font-display-lg text-[26px]">부스 신청서 검토</h1>
        <p className="mt-xs text-caption text-ink-muted">
          신청서를 검토 중으로 전환한 뒤 승인하거나 반려할 수 있습니다.
        </p>
      </div>

      <form onSubmit={handleSearch} className="grid gap-sm rounded-xl border border-hairline bg-white p-lg md:grid-cols-4">
        <select
          value={filters.status}
          onChange={(event) => setFilters({ ...filters, status: event.target.value })}
          className="rounded-lg border border-hairline bg-white px-md py-sm text-caption"
        >
          <option value="">전체 상태</option>
          {Object.entries(STATUS_LABEL).map(([value, label]) => (
            <option key={value} value={value}>{label}</option>
          ))}
        </select>
        <input
          value={filters.teamName}
          onChange={(event) => setFilters({ ...filters, teamName: event.target.value })}
          placeholder="참가 조직명"
          className="rounded-lg border border-hairline px-md py-sm text-caption"
        />
        <input
          value={filters.boothCode}
          onChange={(event) => setFilters({ ...filters, boothCode: event.target.value })}
          placeholder="부스 번호"
          className="rounded-lg border border-hairline px-md py-sm text-caption"
        />
        <button disabled={loading} className="rounded-full bg-primary px-lg py-sm text-caption font-body-strong text-white disabled:opacity-40">
          검색
        </button>
      </form>

      {error && <p className="rounded-xl border border-error/20 bg-error/10 p-md text-caption text-error">{error}</p>}
      {loading && <p className="text-caption text-ink-muted">신청서를 불러오는 중입니다.</p>}

      {!loading && applications.length === 0 ? (
        <div className="rounded-xl border border-hairline bg-white p-xl text-center text-caption text-ink-muted">
          조건에 맞는 부스 신청서가 없습니다.
        </div>
      ) : (
        <div className="overflow-hidden rounded-xl border border-hairline bg-white divide-y divide-divider-soft">
          {applications.map((application) => (
            <button
              type="button"
              key={application.id}
              onClick={() => openDetail(application.id)}
              className="flex w-full items-center gap-sm p-lg text-left transition hover:bg-surface-container-lowest"
            >
              <span className="grid h-10 w-10 flex-shrink-0 place-items-center rounded-lg bg-surface-container">
                <Icon name="description" className="text-[18px] text-ink-muted" />
              </span>
              <span className="min-w-0 flex-1">
                <span className="block truncate font-body-strong text-[14px]">{application.teamName}</span>
                <span className="block text-caption text-ink-muted">
                  신청번호 {application.applicationNo} · 부스 #{application.boothId} · {formatDate(application.submittedAt)}
                </span>
              </span>
              <span className={`rounded-full px-sm py-1 text-[11px] font-bold ${statusClass(application.status)}`}>
                {STATUS_LABEL[application.status] || application.status}
              </span>
              <Icon name="chevron_right" className="text-ink-muted" />
            </button>
          ))}
        </div>
      )}

      {pageResult && pageResult.totalPages > 1 && (
        <div className="flex items-center justify-center gap-sm">
          <button type="button" disabled={pageResult.first || loading} onClick={() => changePage(page - 1)} className="rounded-full border border-hairline px-md py-xs text-caption disabled:opacity-30">이전</button>
          <span className="text-caption text-ink-muted">{page + 1} / {pageResult.totalPages}</span>
          <button type="button" disabled={pageResult.last || loading} onClick={() => changePage(page + 1)} className="rounded-full border border-hairline px-md py-xs text-caption disabled:opacity-30">다음</button>
        </div>
      )}

      {detailLoading && (
        <div className="fixed inset-0 z-[80] grid place-items-center bg-black/40 p-lg">
          <div className="rounded-2xl bg-white p-xl text-caption">신청서를 불러오는 중입니다.</div>
        </div>
      )}

      {selected && !detailLoading && (
        <div className="fixed inset-0 z-[80] flex items-center justify-center bg-black/50 p-lg" onMouseDown={closeDetail}>
          <div className="max-h-[90vh] w-full max-w-2xl overflow-y-auto rounded-2xl bg-white p-lg shadow-xl" onMouseDown={(event) => event.stopPropagation()}>
            <div className="flex items-start justify-between gap-md border-b border-hairline pb-md">
              <div>
                <p className="text-caption text-primary">{selected.applicationNo}</p>
                <h2 className="font-display-md text-[22px]">{selected.teamName}</h2>
              </div>
              <button type="button" aria-label="닫기" disabled={actionLoading} onClick={closeDetail}><Icon name="close" /></button>
            </div>

            <div className="grid gap-md py-lg text-caption md:grid-cols-2">
              <p><span className="block text-ink-muted">담당자</span>{selected.contactName}</p>
              <p><span className="block text-ink-muted">연락처</span>{selected.contactPhone}</p>
              <p><span className="block text-ink-muted">이메일</span>{selected.contactEmail}</p>
              <p><span className="block text-ink-muted">희망 부스</span>#{selected.boothId}</p>
              <p className="md:col-span-2"><span className="block text-ink-muted">활동 설명</span>{selected.activityDescription || "-"}</p>
              <p className="md:col-span-2"><span className="block text-ink-muted">전시 내용</span>{selected.exhibitionContent || "-"}</p>
              <p className="md:col-span-2"><span className="block text-ink-muted">신청 사유</span>{selected.applicationReason || "-"}</p>
              <p><span className="block text-ink-muted">예상 방문객</span>{selected.expectedVisitors?.toLocaleString?.() || selected.expectedVisitors || "-"}명</p>
              <p><span className="block text-ink-muted">필요 설비</span>{[
                selected.electricityRequired && "전기",
                selected.waterRequired && "급수",
                selected.drainageRequired && "배수",
                selected.internetRequired && "인터넷",
              ].filter(Boolean).join(", ") || "없음"}</p>
            </div>

            <div className="border-t border-hairline py-md">
              <p className="mb-sm font-body-strong">첨부파일</p>
              {files.length === 0 ? <p className="text-caption text-ink-muted">첨부파일이 없습니다.</p> : (
                <div className="space-y-xs">
                  {files.map((file) => (
                    <a key={file.fileId} href={file.downloadUrl} target="_blank" rel="noreferrer" className="flex items-center gap-xs text-caption text-primary hover:underline">
                      <Icon name="attach_file" className="text-[16px]" />{file.originalName}
                    </a>
                  ))}
                </div>
              )}
            </div>

            {rejectMode && (
              <div className="space-y-sm border-t border-hairline py-md">
                <label htmlFor="application-rejection-reason" className="font-body-strong">반려 사유</label>
                <textarea
                  id="application-rejection-reason"
                  value={rejectionReason}
                  onChange={(event) => setRejectionReason(event.target.value)}
                  maxLength={1000}
                  rows={4}
                  placeholder="참가 조직이 확인할 수 있도록 구체적인 사유를 입력해 주세요."
                  className="w-full rounded-lg border border-hairline p-md text-caption outline-none focus:border-primary"
                />
                <p className="text-right text-[11px] text-ink-muted">{rejectionReason.length}/1000</p>
              </div>
            )}

            {modalError && <p className="mb-md rounded-lg bg-error/10 p-md text-caption text-error">{modalError}</p>}

            <div className="flex flex-wrap items-center justify-between gap-sm border-t border-hairline pt-md">
              <span className={`rounded-full px-sm py-1 text-caption font-bold ${statusClass(selected.status)}`}>
                {STATUS_LABEL[selected.status] || selected.status}
              </span>
              <div className="flex gap-sm">
                {selected.status === "SUBMITTED" && (
                  <button type="button" disabled={actionLoading} onClick={startReview} className="rounded-full bg-primary px-lg py-sm text-caption font-body-strong text-white disabled:opacity-40">검토 시작</button>
                )}
                {selected.status === "UNDER_REVIEW" && (
                  <>
                    {rejectMode ? (
                      <>
                        <button type="button" disabled={actionLoading} onClick={() => { setRejectMode(false); setRejectionReason(""); setModalError(""); }} className="rounded-full border border-hairline px-lg py-sm text-caption disabled:opacity-40">취소</button>
                        <button type="button" disabled={actionLoading || !rejectionReason.trim()} onClick={reject} className="rounded-full bg-error px-lg py-sm text-caption font-body-strong text-white disabled:opacity-40">{actionLoading ? "처리 중..." : "반려 확정"}</button>
                      </>
                    ) : (
                      <>
                        <button type="button" disabled={actionLoading} onClick={() => { setRejectMode(true); setModalError(""); }} className="rounded-full border border-error/40 px-lg py-sm text-caption text-error disabled:opacity-40">반려</button>
                        <button type="button" disabled={actionLoading} onClick={approve} className="rounded-full bg-primary px-lg py-sm text-caption font-body-strong text-white disabled:opacity-40">{actionLoading ? "처리 중..." : "승인"}</button>
                      </>
                    )}
                  </>
                )}
              </div>
            </div>
          </div>
        </div>
      )}
    </section>
  );
}
