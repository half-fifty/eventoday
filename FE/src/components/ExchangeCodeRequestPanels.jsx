import { useCallback, useEffect, useRef, useState } from "react";
import Icon from "./Icon.jsx";
import { exchangeCodeApi } from "../api/exchangeCodeApi.js";

const statusOptions = ["REQUESTED", "APPROVED", "ISSUED", "REJECTED"];

const statusLabel = {
  REQUESTED: "요청",
  APPROVED: "승인",
  REJECTED: "반려",
  ISSUED: "발급 완료",
};

const formatDateTime = (value) =>
  value ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-";

const statusClass = (status) => {
  if (status === "APPROVED" || status === "ISSUED") return "bg-status-available/10 text-status-available";
  if (status === "REJECTED") return "bg-status-visited/10 text-status-visited";
  return "bg-status-pending/10 text-status-pending";
};

const normalizePageContent = (result) => result?.data?.content || [];
const pageInfo = (result) => ({
  number: result?.data?.number || 0,
  totalPages: result?.data?.totalPages || 1,
});

function RequestRow({ request, onSelect }) {
  return (
    <button
      type="button"
      onClick={() => onSelect(request.requestId)}
      className="flex w-full items-center gap-md p-lg text-left transition-colors hover:bg-surface-container-low"
    >
      <div className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-lg bg-surface-container">
        <Icon name="key" className="text-[18px] text-ink-muted" />
      </div>
      <div className="min-w-0 flex-1">
        <p className="font-body-strong truncate">{request.eventName}</p>
        <p className="text-caption text-ink-muted">
          외부 판매 {request.requestedQuantity}매 · {request.requesterNickname || `회원 #${request.requestedBy}`}
        </p>
        <p className="text-[11px] text-ink-muted">요청 {formatDateTime(request.createdAt)}</p>
      </div>
      <span className={`rounded-full px-sm py-1 text-[11px] font-bold ${statusClass(request.status)}`}>
        {statusLabel[request.status] || request.status}
      </span>
    </button>
  );
}

function RequestDetail({ request, admin, actionBusy, rejectionReason, onRejectionReasonChange, onApprove, onReject, onIssue, onResend }) {
  if (!request) return null;
  const canReview = admin && request.status === "REQUESTED";
  const canIssue = admin && request.status === "APPROVED";
  const canResend = admin && request.status === "ISSUED" && !request.emailedAt;

  return (
    <div className="rounded-xl border border-hairline bg-white p-lg">
      <div className="mb-md flex flex-wrap items-start justify-between gap-md">
        <div>
          <p className="font-body-strong">{request.eventName}</p>
        </div>
        <span className={`rounded-full px-sm py-1 text-[11px] font-bold ${statusClass(request.status)}`}>
          {statusLabel[request.status] || request.status}
        </span>
      </div>
      <div className="grid gap-sm text-caption sm:grid-cols-2">
        <Info label="외부 판매 티켓 매수" value={`${request.requestedQuantity}매`} />
        <Info label="요청자" value={request.requesterNickname || `회원 #${request.requestedBy}`} />
        <Info label="요청 시각" value={formatDateTime(request.createdAt)} />
        <Info label="검토 시각" value={formatDateTime(request.reviewedAt)} />
        <Info label="검토자" value={request.reviewedBy ? `회원 #${request.reviewedBy}` : "-"} />
        <Info label="이메일 발송" value={request.emailedAt ? formatDateTime(request.emailedAt) : "미발송"} />
        <div className="sm:col-span-2">
          <Info label="외부 예매처 및 요청 사유" value={request.purpose} />
        </div>
        {request.rejectionReason && (
          <div className="sm:col-span-2">
            <Info label="반려 사유" value={request.rejectionReason} danger />
          </div>
        )}
      </div>

      {canReview && (
        <div className="mt-md space-y-sm rounded-xl bg-surface-container p-md">
          <textarea
            value={rejectionReason}
            onChange={(event) => onRejectionReasonChange(event.target.value)}
            maxLength={2000}
            placeholder="반려 시 사유를 입력하세요."
            className="min-h-[88px] w-full rounded-lg border border-hairline px-sm py-sm text-caption outline-none focus:border-primary-focus"
          />
          <div className="flex flex-wrap gap-sm">
            <button
              type="button"
              onClick={onApprove}
              disabled={actionBusy}
              className="rounded-full bg-status-available px-lg py-sm text-caption font-body-strong text-white disabled:opacity-50"
            >
              승인
            </button>
            <button
              type="button"
              onClick={onReject}
              disabled={actionBusy}
              className="rounded-full border border-hairline px-lg py-sm text-caption font-body-strong text-error disabled:opacity-50"
            >
              반려
            </button>
          </div>
        </div>
      )}

      {canIssue && (
        <div className="mt-md rounded-xl bg-surface-container p-md">
          <p className="mb-sm text-caption text-ink-muted">승인된 외부 판매 티켓 {request.requestedQuantity}매에 대한 입장 등록 코드를 발급할 수 있습니다.</p>
          <button
            type="button"
            onClick={onIssue}
            disabled={actionBusy}
            className="rounded-full bg-black px-lg py-sm text-caption font-body-strong text-white disabled:opacity-50"
          >
            입장 등록 코드 발급
          </button>
        </div>
      )}

      {canResend && (
        <div className="mt-md rounded-xl bg-surface-container p-md">
          <p className="mb-sm text-caption text-ink-muted">코드는 발급되었지만 이메일 발송 시각이 없습니다.</p>
          <button
            type="button"
            onClick={onResend}
            disabled={actionBusy}
            className="rounded-full border border-hairline px-lg py-sm text-caption font-body-strong disabled:opacity-50"
          >
            이메일 재전송
          </button>
        </div>
      )}
    </div>
  );
}

function Info({ label, value, danger = false }) {
  return (
    <div className="rounded-lg bg-surface-container-low p-sm">
      <p className="text-[11px] text-ink-muted">{label}</p>
      <p className={`mt-1 break-words font-body-strong ${danger ? "text-error" : ""}`}>{value || "-"}</p>
    </div>
  );
}

export function OrganizerExchangeCodeRequestPanel({ eventId }) {
  const [requests, setRequests] = useState([]);
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [quantity, setQuantity] = useState("");
  const [purpose, setPurpose] = useState("");
  const [page, setPage] = useState(0);
  const [requestPageInfo, setRequestPageInfo] = useState({ number: 0, totalPages: 1 });
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState("");
  const listRequestSeqRef = useRef(0);
  const previousEventIdRef = useRef(eventId);
  const latestRequestIdRef = useRef(null);

  const loadRequests = useCallback(() => {
    const requestSeq = listRequestSeqRef.current + 1;
    listRequestSeqRef.current = requestSeq;
    const eventChanged = previousEventIdRef.current !== eventId;
    previousEventIdRef.current = eventId;

    if (eventChanged) {
      setPage(0);
      setRequests([]);
      setSelectedRequest(null);
      setRequestPageInfo({ number: 0, totalPages: 1 });
      setError("");
      setDetailLoading(false);
      latestRequestIdRef.current = null;
    }

    if (!eventId) {
      setRequests([]);
      setSelectedRequest(null);
      setRequestPageInfo({ number: 0, totalPages: 1 });
      setError("");
      setLoading(false);
      return Promise.resolve();
    }

    if (eventChanged && page !== 0) {
      setLoading(false);
      return Promise.resolve();
    }

    setLoading(true);
    setError("");
    return exchangeCodeApi.getEventExchangeCodeRequests(eventId, { page, size: 20 })
      .then((result) => {
        if (requestSeq !== listRequestSeqRef.current) return;
        setRequests(normalizePageContent(result));
        setRequestPageInfo(pageInfo(result));
      })
      .catch((requestError) => {
        if (requestSeq !== listRequestSeqRef.current) return;
        setError(requestError.message || "외부 예매 티켓 연동 요청을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (requestSeq === listRequestSeqRef.current) setLoading(false);
      });
  }, [eventId, page]);

  useEffect(() => {
    loadRequests();
  }, [loadRequests]);

  const selectRequest = async (requestId) => {
    latestRequestIdRef.current = requestId;
    setDetailLoading(true);
    setError("");
    try {
      const result = await exchangeCodeApi.getExchangeCodeRequest(requestId);
      if (latestRequestIdRef.current === requestId) setSelectedRequest(result?.data || null);
    } catch (requestError) {
      if (latestRequestIdRef.current === requestId) setError(requestError.message || "외부 예매 티켓 연동 요청 상세를 불러오지 못했습니다.");
    } finally {
      if (latestRequestIdRef.current === requestId) setDetailLoading(false);
    }
  };

  const submitRequest = async () => {
    const requestedQuantity = Number(quantity);
    const trimmedPurpose = purpose.trim();
    setMessage(null);
    setError("");
    if (!eventId) return setError("행사를 먼저 선택하세요.");
    if (!Number.isInteger(requestedQuantity) || requestedQuantity < 1 || requestedQuantity > 1000) {
      return setError("외부 판매 티켓 매수는 1매 이상 1000매 이하로 입력하세요.");
    }
    if (!trimmedPurpose) return setError("외부 예매처와 요청 사유를 입력하세요.");
    if (trimmedPurpose.length > 500) return setError("외부 예매처와 요청 사유는 500자 이하로 입력하세요.");
    setSubmitting(true);
    try {
      await exchangeCodeApi.createExchangeCodeRequest(eventId, {
        requestedQuantity,
        purpose: trimmedPurpose,
      });
      setQuantity("");
      setPurpose("");
      setMessage({ ok: true, text: "외부 예매 티켓 연동 요청이 접수되었습니다." });
      await loadRequests();
    } catch (requestError) {
      setMessage({ ok: false, text: requestError.message || "외부 예매 티켓 연동 요청에 실패했습니다." });
    } finally {
      setSubmitting(false);
    }
  };

  if (!eventId) {
    return (
      <section className="rounded-xl border border-hairline bg-white p-lg text-caption text-ink-muted">
        외부 예매 티켓을 연동할 행사를 선택하세요.
      </section>
    );
  }

  return (
    <section className="space-y-lg">
      <div>
        <h1 className="font-display-lg text-[26px]">외부 예매 티켓 연동</h1>
        <p className="mt-xs max-w-[760px] text-caption leading-relaxed text-ink-muted">
          네이버 등 외부 예매처에서 판매한 티켓을 EvenToday 입장 QR로 전환하기 위한 기능입니다. 외부 판매 매수만큼 입장 등록 코드 발급을 요청한 뒤 구매자에게 전달하세요.
        </p>
      </div>
      <div className="rounded-xl border border-hairline bg-white p-lg">
        <div className="grid gap-sm sm:grid-cols-[160px_1fr_auto]">
          <input
            type="text"
            inputMode="numeric"
            pattern="[0-9]*"
            value={quantity}
            onChange={(event) => setQuantity(event.target.value.replace(/\D/g, "").slice(0, 4))}
            placeholder="외부 판매 티켓 매수"
            aria-label="외부 판매 티켓 매수"
            className="h-[44px] w-full rounded-lg border border-hairline bg-white px-md text-caption text-ink outline-none transition-colors placeholder:text-ink-muted focus:border-primary-focus focus:ring-1 focus:ring-primary-focus/20"
          />
          <input
            type="text"
            maxLength={500}
            value={purpose}
            onChange={(event) => setPurpose(event.target.value)}
            placeholder="외부 예매처 및 요청 사유 (예: 네이버 예매 판매분)"
            className="h-[44px] w-full rounded-lg border border-hairline bg-white px-md text-caption text-ink outline-none transition-colors placeholder:text-ink-muted focus:border-primary-focus focus:ring-1 focus:ring-primary-focus/20"
          />
          <button
            type="button"
            onClick={submitRequest}
            disabled={submitting}
            className="h-[44px] rounded-lg bg-primary px-xl text-caption font-body-strong text-white transition-colors hover:bg-primary-focus disabled:cursor-not-allowed disabled:opacity-50"
          >
            {submitting ? "요청 중" : "요청"}
          </button>
        </div>
        {message && <p className={`mt-sm text-caption ${message.ok ? "text-status-available" : "text-error"}`}>{message.text}</p>}
        {error && <p className="mt-sm text-caption text-error">{error}</p>}
      </div>
      <div className="grid gap-lg lg:grid-cols-[1fr_420px]">
        <div className="rounded-xl border border-hairline bg-white divide-y divide-divider-soft">
          <div className="p-lg font-body-strong">요청 내역</div>
          {loading && <p className="p-lg text-caption text-ink-muted">요청 목록을 불러오는 중입니다.</p>}
          {!loading && !error && requests.length === 0 && <p className="p-lg text-caption text-ink-muted">외부 예매 티켓 연동 요청 내역이 없습니다.</p>}
          {!loading && requests.map((request) => (
            <RequestRow key={request.requestId} request={request} onSelect={selectRequest} />
          ))}
          {!error && (
            <Pagination
              pageInfo={requestPageInfo}
              loading={loading}
              onPrev={() => setPage((current) => Math.max(0, current - 1))}
              onNext={() => setPage((current) => Math.min(requestPageInfo.totalPages - 1, current + 1))}
            />
          )}
        </div>
        <div>
          {detailLoading ? (
            <div className="rounded-xl border border-hairline bg-white p-lg text-caption text-ink-muted">상세를 불러오는 중입니다.</div>
          ) : selectedRequest ? (
            <RequestDetail request={selectedRequest} />
          ) : (
            <div className="rounded-xl border border-hairline bg-white p-lg text-caption text-ink-muted">요청을 선택하면 상세가 표시됩니다.</div>
          )}
        </div>
      </div>
    </section>
  );
}

export function AdminExchangeCodeRequestPanel() {
  const [status, setStatus] = useState("REQUESTED");
  const [requests, setRequests] = useState([]);
  const [selectedRequest, setSelectedRequest] = useState(null);
  const [page, setPage] = useState(0);
  const [requestPageInfo, setRequestPageInfo] = useState({ number: 0, totalPages: 1 });
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);
  const [rejectionReason, setRejectionReason] = useState("");
  const [message, setMessage] = useState(null);
  const [error, setError] = useState("");
  const listRequestSeqRef = useRef(0);
  const latestRequestIdRef = useRef(null);

  const loadRequests = useCallback(() => {
    const requestSeq = listRequestSeqRef.current + 1;
    listRequestSeqRef.current = requestSeq;
    setLoading(true);
    setError("");
    return exchangeCodeApi.getAdminExchangeCodeRequests({ status, page, size: 20 })
      .then((result) => {
        if (requestSeq !== listRequestSeqRef.current) return;
        setRequests(normalizePageContent(result));
        setRequestPageInfo(pageInfo(result));
      })
      .catch((requestError) => {
        if (requestSeq !== listRequestSeqRef.current) return;
        setError(requestError.message || "외부 예매 티켓 연동 요청을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (requestSeq === listRequestSeqRef.current) setLoading(false);
      });
  }, [page, status]);

  const loadDetail = useCallback((requestId) => {
    latestRequestIdRef.current = requestId;
    setDetailLoading(true);
    setError("");
    return exchangeCodeApi.getExchangeCodeRequest(requestId)
      .then((result) => {
        if (latestRequestIdRef.current === requestId) {
          setSelectedRequest(result?.data || null);
          setRejectionReason("");
        }
      })
      .catch((requestError) => {
        if (latestRequestIdRef.current === requestId) setError(requestError.message || "외부 예매 티켓 연동 요청 상세를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (latestRequestIdRef.current === requestId) setDetailLoading(false);
      });
  }, []);

  useEffect(() => {
    setSelectedRequest(null);
    loadRequests();
  }, [loadRequests]);

  useEffect(() => {
    setPage(0);
  }, [status]);

  const refreshAfterAction = async (requestId) => {
    await Promise.all([loadRequests(), loadDetail(requestId)]);
  };

  const approve = async () => {
    if (!selectedRequest || actionBusy) return;
    setActionBusy(true);
    setMessage(null);
    setError("");
    try {
      await exchangeCodeApi.approveExchangeCodeRequest(selectedRequest.requestId);
      setMessage({ ok: true, text: "외부 예매 티켓 연동 요청을 승인했습니다." });
      await refreshAfterAction(selectedRequest.requestId);
    } catch (requestError) {
      setMessage({ ok: false, text: requestError.message || "승인에 실패했습니다." });
    } finally {
      setActionBusy(false);
    }
  };

  const reject = async () => {
    if (!selectedRequest || actionBusy) return;
    const trimmed = rejectionReason.trim();
    setMessage(null);
    setError("");
    if (!trimmed) return setMessage({ ok: false, text: "반려 사유를 입력하세요." });
    if (trimmed.length > 2000) return setMessage({ ok: false, text: "반려 사유는 2000자 이하로 입력하세요." });
    setActionBusy(true);
    try {
      await exchangeCodeApi.rejectExchangeCodeRequest(selectedRequest.requestId, trimmed);
      setMessage({ ok: true, text: "외부 예매 티켓 연동 요청을 반려했습니다." });
      await refreshAfterAction(selectedRequest.requestId);
    } catch (requestError) {
      setMessage({ ok: false, text: requestError.message || "반려에 실패했습니다." });
    } finally {
      setActionBusy(false);
    }
  };

  const issue = async () => {
    if (!selectedRequest || actionBusy) return;
    const confirmed = window.confirm(`외부 판매 티켓 ${selectedRequest.requestedQuantity}매에 대한 입장 등록 코드를 발급하시겠습니까?`);
    if (!confirmed) return;
    setActionBusy(true);
    setMessage(null);
    setError("");
    try {
      const result = await exchangeCodeApi.issueExchangeCodes(selectedRequest.requestId);
      setMessage({ ok: true, text: `입장 등록 코드 ${result?.data?.generatedQuantity || selectedRequest.requestedQuantity}개를 발급했습니다.` });
      await refreshAfterAction(selectedRequest.requestId);
    } catch (requestError) {
      setMessage({ ok: false, text: requestError.message || "교환 코드 발급에 실패했습니다." });
    } finally {
      setActionBusy(false);
    }
  };

  const resend = async () => {
    if (!selectedRequest || actionBusy) return;
    setActionBusy(true);
    setMessage(null);
    setError("");
    try {
      await exchangeCodeApi.resendExchangeCodeEmail(selectedRequest.requestId);
      setMessage({ ok: true, text: "입장 등록 코드 이메일을 재전송했습니다." });
      await refreshAfterAction(selectedRequest.requestId);
    } catch (requestError) {
      setMessage({ ok: false, text: requestError.message || "이메일 재전송에 실패했습니다." });
    } finally {
      setActionBusy(false);
    }
  };

  return (
    <section className="space-y-lg">
      <div className="flex flex-wrap items-center justify-between gap-md">
        <div>
          <h1 className="font-display-lg text-[26px]">외부 예매 티켓 관리</h1>
          <p className="mt-xs max-w-[760px] text-caption leading-relaxed text-ink-muted">외부 예매처 판매분을 EvenToday 입장 QR로 전환하기 위한 요청을 검토하고, 구매자에게 전달할 입장 등록 코드를 발급합니다.</p>
        </div>
        <select
          value={status}
          onChange={(event) => {
            setPage(0);
            setStatus(event.target.value);
          }}
          className="rounded-lg border border-hairline bg-white px-md py-sm text-caption"
        >
          {statusOptions.map((option) => (
            <option key={option} value={option}>{statusLabel[option]}</option>
          ))}
        </select>
      </div>
      {message && <div className={`rounded-xl border p-lg text-caption ${message.ok ? "border-status-available/20 bg-status-available/10 text-status-available" : "border-error/20 bg-error/10 text-error"}`}>{message.text}</div>}
      {error && <div className="rounded-xl border border-error/20 bg-error/10 p-lg text-caption text-error">{error}</div>}
      <div className="grid gap-lg lg:grid-cols-[1fr_460px]">
        <div className="rounded-xl border border-hairline bg-white divide-y divide-divider-soft">
          <div className="p-lg font-body-strong">요청 목록</div>
          {loading && <p className="p-lg text-caption text-ink-muted">요청 목록을 불러오는 중입니다.</p>}
          {!loading && !error && requests.length === 0 && <p className="p-lg text-caption text-ink-muted">해당 상태의 요청이 없습니다.</p>}
          {!loading && requests.map((request) => (
            <RequestRow key={request.requestId} request={request} onSelect={loadDetail} />
          ))}
          {!error && (
            <Pagination
              pageInfo={requestPageInfo}
              loading={loading}
              onPrev={() => setPage((current) => Math.max(0, current - 1))}
              onNext={() => setPage((current) => Math.min(requestPageInfo.totalPages - 1, current + 1))}
            />
          )}
        </div>
        <div>
          {detailLoading ? (
            <div className="rounded-xl border border-hairline bg-white p-lg text-caption text-ink-muted">상세를 불러오는 중입니다.</div>
          ) : selectedRequest ? (
            <RequestDetail
              request={selectedRequest}
              admin
              actionBusy={actionBusy}
              rejectionReason={rejectionReason}
              onRejectionReasonChange={setRejectionReason}
              onApprove={approve}
              onReject={reject}
              onIssue={issue}
              onResend={resend}
            />
          ) : (
            <div className="rounded-xl border border-hairline bg-white p-lg text-caption text-ink-muted">요청을 선택하면 상세와 처리 버튼이 표시됩니다.</div>
          )}
        </div>
      </div>
    </section>
  );
}

function Pagination({ pageInfo, loading, onPrev, onNext }) {
  const current = pageInfo.number + 1;
  const total = Math.max(1, pageInfo.totalPages);
  return (
    <div className="flex items-center justify-center gap-sm border-t border-divider-soft p-md text-caption">
      <button
        type="button"
        onClick={onPrev}
        disabled={loading || pageInfo.number <= 0}
        className="rounded-full border border-hairline px-md py-1 disabled:opacity-40"
      >
        이전
      </button>
      <span className="text-ink-muted">{current} / {total}</span>
      <button
        type="button"
        onClick={onNext}
        disabled={loading || current >= total}
        className="rounded-full border border-hairline px-md py-1 disabled:opacity-40"
      >
        다음
      </button>
    </div>
  );
}
