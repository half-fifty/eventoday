import { useCallback, useEffect, useState } from "react";
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
          요청 #{request.requestId} · {request.requestedQuantity}개 · {request.requesterNickname || `회원 #${request.requestedBy}`}
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
          <p className="text-caption text-ink-muted">요청 #{request.requestId} · 행사 #{request.eventId}</p>
        </div>
        <span className={`rounded-full px-sm py-1 text-[11px] font-bold ${statusClass(request.status)}`}>
          {statusLabel[request.status] || request.status}
        </span>
      </div>
      <div className="grid gap-sm text-caption sm:grid-cols-2">
        <Info label="요청 수량" value={`${request.requestedQuantity}개`} />
        <Info label="요청자" value={request.requesterNickname || `회원 #${request.requestedBy}`} />
        <Info label="요청 시각" value={formatDateTime(request.createdAt)} />
        <Info label="검토 시각" value={formatDateTime(request.reviewedAt)} />
        <Info label="검토자" value={request.reviewedBy ? `회원 #${request.reviewedBy}` : "-"} />
        <Info label="이메일 발송" value={request.emailedAt ? formatDateTime(request.emailedAt) : "미발송"} />
        <div className="sm:col-span-2">
          <Info label="요청 목적" value={request.purpose} />
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
          <p className="mb-sm text-caption text-ink-muted">승인된 요청입니다. 교환 코드 {request.requestedQuantity}개를 발급할 수 있습니다.</p>
          <button
            type="button"
            onClick={onIssue}
            disabled={actionBusy}
            className="rounded-full bg-black px-lg py-sm text-caption font-body-strong text-white disabled:opacity-50"
          >
            교환 코드 발급
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
  const [quantity, setQuantity] = useState("1");
  const [purpose, setPurpose] = useState("");
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [message, setMessage] = useState(null);
  const [error, setError] = useState("");

  const loadRequests = useCallback(() => {
    if (!eventId) {
      setRequests([]);
      setSelectedRequest(null);
      return Promise.resolve();
    }
    setLoading(true);
    setError("");
    return exchangeCodeApi.getEventExchangeCodeRequests(eventId, { page: 0, size: 20 })
      .then((result) => setRequests(normalizePageContent(result)))
      .catch((requestError) => setError(requestError.message || "교환 코드 요청 목록을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [eventId]);

  useEffect(() => {
    loadRequests();
  }, [loadRequests]);

  const selectRequest = async (requestId) => {
    setDetailLoading(true);
    setError("");
    try {
      const result = await exchangeCodeApi.getExchangeCodeRequest(requestId);
      setSelectedRequest(result?.data || null);
    } catch (requestError) {
      setError(requestError.message || "교환 코드 요청 상세를 불러오지 못했습니다.");
    } finally {
      setDetailLoading(false);
    }
  };

  const submitRequest = async () => {
    const requestedQuantity = Number(quantity);
    const trimmedPurpose = purpose.trim();
    setMessage(null);
    setError("");
    if (!eventId) return setError("행사를 먼저 선택하세요.");
    if (!Number.isInteger(requestedQuantity) || requestedQuantity < 1 || requestedQuantity > 1000) {
      return setError("요청 수량은 1개 이상 1000개 이하로 입력하세요.");
    }
    if (!trimmedPurpose) return setError("요청 목적을 입력하세요.");
    if (trimmedPurpose.length > 500) return setError("요청 목적은 500자 이하로 입력하세요.");
    setSubmitting(true);
    try {
      const result = await exchangeCodeApi.createExchangeCodeRequest(eventId, {
        requestedQuantity,
        purpose: trimmedPurpose,
      });
      setQuantity("1");
      setPurpose("");
      setMessage({ ok: true, text: `교환 코드 발급 요청이 접수되었습니다. 요청 #${result?.data?.requestId || ""}` });
      await loadRequests();
    } catch (requestError) {
      setMessage({ ok: false, text: requestError.message || "교환 코드 발급 요청에 실패했습니다." });
    } finally {
      setSubmitting(false);
    }
  };

  if (!eventId) {
    return (
      <section className="rounded-xl border border-hairline bg-white p-lg text-caption text-ink-muted">
        교환 코드 요청을 관리할 행사를 선택하세요.
      </section>
    );
  }

  return (
    <section className="space-y-lg">
      <div>
        <h1 className="font-display-lg text-[26px]">교환 코드 발급 요청</h1>
        <p className="mt-xs text-caption text-ink-muted">행사 관리자 권한으로 교환 코드 발급을 요청합니다.</p>
      </div>
      <div className="rounded-xl border border-hairline bg-white p-lg">
        <div className="grid gap-sm sm:grid-cols-[140px_1fr_auto]">
          <input
            type="number"
            min="1"
            max="1000"
            value={quantity}
            onChange={(event) => setQuantity(event.target.value)}
            className="h-[42px] rounded-lg border border-hairline px-sm text-caption outline-none focus:border-primary-focus"
          />
          <input
            type="text"
            maxLength={500}
            value={purpose}
            onChange={(event) => setPurpose(event.target.value)}
            placeholder="요청 목적"
            className="h-[42px] rounded-lg border border-hairline px-sm text-caption outline-none focus:border-primary-focus"
          />
          <button
            type="button"
            onClick={submitRequest}
            disabled={submitting}
            className="h-[42px] rounded-lg bg-primary px-lg text-caption font-body-strong text-white disabled:opacity-50"
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
          {!loading && !error && requests.length === 0 && <p className="p-lg text-caption text-ink-muted">교환 코드 요청 내역이 없습니다.</p>}
          {!loading && requests.map((request) => (
            <RequestRow key={request.requestId} request={request} onSelect={selectRequest} />
          ))}
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
  const [loading, setLoading] = useState(false);
  const [detailLoading, setDetailLoading] = useState(false);
  const [actionBusy, setActionBusy] = useState(false);
  const [rejectionReason, setRejectionReason] = useState("");
  const [message, setMessage] = useState(null);
  const [error, setError] = useState("");

  const loadRequests = useCallback(() => {
    setLoading(true);
    setError("");
    return exchangeCodeApi.getAdminExchangeCodeRequests({ status, page: 0, size: 20 })
      .then((result) => setRequests(normalizePageContent(result)))
      .catch((requestError) => setError(requestError.message || "교환 코드 요청 목록을 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [status]);

  const loadDetail = useCallback((requestId) => {
    setDetailLoading(true);
    setError("");
    return exchangeCodeApi.getExchangeCodeRequest(requestId)
      .then((result) => {
        setSelectedRequest(result?.data || null);
        setRejectionReason("");
      })
      .catch((requestError) => setError(requestError.message || "교환 코드 요청 상세를 불러오지 못했습니다."))
      .finally(() => setDetailLoading(false));
  }, []);

  useEffect(() => {
    setSelectedRequest(null);
    loadRequests();
  }, [loadRequests]);

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
      setMessage({ ok: true, text: "교환 코드 요청을 승인했습니다." });
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
      setMessage({ ok: true, text: "교환 코드 요청을 반려했습니다." });
      await refreshAfterAction(selectedRequest.requestId);
    } catch (requestError) {
      setMessage({ ok: false, text: requestError.message || "반려에 실패했습니다." });
    } finally {
      setActionBusy(false);
    }
  };

  const issue = async () => {
    if (!selectedRequest || actionBusy) return;
    const confirmed = window.confirm(`요청 수량 ${selectedRequest.requestedQuantity}개의 교환 코드를 발급하시겠습니까?`);
    if (!confirmed) return;
    setActionBusy(true);
    setMessage(null);
    setError("");
    try {
      const result = await exchangeCodeApi.issueExchangeCodes(selectedRequest.requestId);
      setMessage({ ok: true, text: `교환 코드 ${result?.data?.generatedQuantity || selectedRequest.requestedQuantity}개를 발급했습니다.` });
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
      setMessage({ ok: true, text: "교환 코드 이메일을 재전송했습니다." });
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
          <h1 className="font-display-lg text-[26px]">교환 코드 요청 관리</h1>
          <p className="mt-xs text-caption text-ink-muted">요청 승인, 반려, 코드 발급, 이메일 재전송을 처리합니다.</p>
        </div>
        <select
          value={status}
          onChange={(event) => setStatus(event.target.value)}
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
