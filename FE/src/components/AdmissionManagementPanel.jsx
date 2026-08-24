import { useCallback, useEffect, useRef, useState } from "react";
import { BrowserQRCodeReader } from "@zxing/browser";
import Icon from "./Icon.jsx";
import { admissionApi } from "../api/admissionApi.js";

const ticketStatuses = ["", "ISSUED", "USED", "CANCELLED", "EXPIRED"];
const actions = ["", "CHECK_IN", "CHECK_IN_CANCEL"];
const results = ["", "SUCCESS", "DUPLICATE", "INVALID"];

const ticketStatusLabel = {
  ISSUED: "입장 가능",
  USED: "입장 완료",
  CANCELLED: "취소",
  EXPIRED: "만료",
};

const actionLabel = {
  CHECK_IN: "입장 처리",
  CHECK_IN_CANCEL: "입장 처리 취소",
};

const resultLabel = {
  SUCCESS: "성공",
  DUPLICATE: "중복",
  INVALID: "실패",
};

const formatDateTime = (value) =>
  value ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-";

const pageContent = (result) => result?.data?.content || [];
const pageInfo = (result) => ({
  number: result?.data?.number || 0,
  totalPages: result?.data?.totalPages || 1,
});

const checkInMessage = (error) => {
  const byCode = {
    ADMISSION_409_016: "이미 입장 처리된 티켓입니다.",
    ADMISSION_409_015: "현재 상태에서는 입장 처리할 수 없습니다.",
    ADMISSION_403_003: "이 행사의 입장 QR이 아닙니다.",
    ADMISSION_404_004: "입장 티켓을 찾을 수 없습니다.",
    ADMISSION_409_018: "입장 처리가 진행 중입니다. 잠시 후 다시 시도하세요.",
    COMMON_400: "QR 값이 올바르지 않습니다.",
    COMMON_403: "현장 입장 처리 권한이 없습니다.",
  };
  return byCode[error?.code] || error?.message || "입장 처리에 실패했습니다.";
};

function statusPillClass(status) {
  if (status === "SUCCESS" || status === "USED" || status === "ISSUED") return "bg-status-available/10 text-status-available";
  if (status === "DUPLICATE") return "bg-status-pending/10 text-status-pending";
  if (status === "INVALID" || status === "CANCELLED" || status === "EXPIRED") return "bg-status-visited/10 text-status-visited";
  return "bg-surface-container text-ink-muted";
}

function CameraScanner({ active, disabled, onDetected }) {
  const videoRef = useRef(null);
  const controlsRef = useRef(null);
  const disabledRef = useRef(disabled);
  const onDetectedRef = useRef(onDetected);
  const [cameraState, setCameraState] = useState("idle");
  const [cameraError, setCameraError] = useState("");

  useEffect(() => {
    disabledRef.current = disabled;
    onDetectedRef.current = onDetected;
  }, [disabled, onDetected]);

  useEffect(() => {
    if (!active) return undefined;
    let stopped = false;

    const stopCamera = () => {
      if (controlsRef.current) {
        controlsRef.current.stop();
        controlsRef.current = null;
      }
      if (videoRef.current) videoRef.current.srcObject = null;
    };

    const start = async () => {
      setCameraState("loading");
      setCameraError("");
      try {
        const reader = new BrowserQRCodeReader();
        const controls = await reader.decodeFromConstraints(
          {
            video: { facingMode: { ideal: "environment" } },
            audio: false,
          },
          videoRef.current,
          (result) => {
            if (stopped || disabledRef.current) return;
            const value = result?.getText()?.trim();
            if (value) onDetectedRef.current(value);
          }
        );
        if (stopped) {
          controls.stop();
          return;
        }
        controlsRef.current = controls;
        setCameraState("scanning");
      } catch (error) {
        if (stopped) return;
        setCameraState("error");
        setCameraError(error?.name === "NotAllowedError"
          ? "카메라 권한이 거부되었습니다. 직접 입력을 사용하세요."
          : "카메라를 사용할 수 없습니다. 직접 입력을 사용하세요.");
      }
    };

    start();

    return () => {
      stopped = true;
      stopCamera();
    };
  }, [active]);

  return (
    <div className="rounded-xl border border-hairline bg-black p-sm text-white">
      <div className="aspect-video overflow-hidden rounded-lg bg-black">
        <video ref={videoRef} muted playsInline className="h-full w-full object-cover" />
      </div>
      <p className="mt-sm text-caption text-white/70">
        {cameraState === "loading" && "카메라를 준비하고 있습니다."}
        {cameraState === "scanning" && (disabled ? "입장 처리 중입니다." : "QR을 스캔하고 있습니다.")}
        {(cameraState === "error" || cameraState === "unsupported") && cameraError}
        {cameraState === "idle" && "카메라를 시작합니다."}
      </p>
    </div>
  );
}

export default function AdmissionManagementPanel({ eventId }) {
  const [scannerActive, setScannerActive] = useState(false);
  const [gateName, setGateName] = useState("");
  const [manualToken, setManualToken] = useState("");
  const [processing, setProcessing] = useState(false);
  const [checkInResult, setCheckInResult] = useState(null);
  const [checkInError, setCheckInError] = useState("");
  const [tickets, setTickets] = useState([]);
  const [ticketStatus, setTicketStatus] = useState("");
  const [ticketPage, setTicketPage] = useState(0);
  const [ticketPageInfo, setTicketPageInfo] = useState({ number: 0, totalPages: 1 });
  const [ticketsLoading, setTicketsLoading] = useState(false);
  const [ticketsError, setTicketsError] = useState("");
  const [logs, setLogs] = useState([]);
  const [logAction, setLogAction] = useState("");
  const [logResult, setLogResult] = useState("");
  const [logPage, setLogPage] = useState(0);
  const [logPageInfo, setLogPageInfo] = useState({ number: 0, totalPages: 1 });
  const [logsLoading, setLogsLoading] = useState(false);
  const [logsError, setLogsError] = useState("");
  const [auxiliaryAccessDenied, setAuxiliaryAccessDenied] = useState(false);
  const [cancellingId, setCancellingId] = useState(null);
  const scanLockRef = useRef(false);
  const ticketRequestGenerationRef = useRef(0);
  const logRequestGenerationRef = useRef(0);
  const checkInRequestGenerationRef = useRef(0);
  const activeEventIdRef = useRef(eventId);
  activeEventIdRef.current = eventId;

  const loadTickets = useCallback(() => {
    if (!eventId || auxiliaryAccessDenied) return Promise.resolve();
    const generation = ++ticketRequestGenerationRef.current;
    const requestingEventId = eventId;
    setTicketsLoading(true);
    setTicketsError("");
    const params = { page: ticketPage, size: 20 };
    if (ticketStatus) params.status = ticketStatus;
    return admissionApi.getEventAdmissionTickets(eventId, params)
      .then((result) => {
        if (generation !== ticketRequestGenerationRef.current || requestingEventId !== activeEventIdRef.current) return;
        setTickets(pageContent(result));
        setTicketPageInfo(pageInfo(result));
      })
      .catch((error) => {
        if (generation !== ticketRequestGenerationRef.current || requestingEventId !== activeEventIdRef.current) return;
        setTickets([]);
        if (error.status === 403) {
          setAuxiliaryAccessDenied(true);
          setTicketsError("");
          return;
        }
        setTicketsError(error.message || "입장 티켓 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (generation === ticketRequestGenerationRef.current && requestingEventId === activeEventIdRef.current) {
          setTicketsLoading(false);
        }
      });
  }, [auxiliaryAccessDenied, eventId, ticketPage, ticketStatus]);

  const loadLogs = useCallback(() => {
    if (!eventId || auxiliaryAccessDenied) return Promise.resolve();
    const generation = ++logRequestGenerationRef.current;
    const requestingEventId = eventId;
    setLogsLoading(true);
    setLogsError("");
    const params = { page: logPage, size: 20 };
    if (logAction) params.action = logAction;
    if (logResult) params.result = logResult;
    return admissionApi.getAdmissionLogs(eventId, params)
      .then((result) => {
        if (generation !== logRequestGenerationRef.current || requestingEventId !== activeEventIdRef.current) return;
        setLogs(pageContent(result));
        setLogPageInfo(pageInfo(result));
      })
      .catch((error) => {
        if (generation !== logRequestGenerationRef.current || requestingEventId !== activeEventIdRef.current) return;
        setLogs([]);
        if (error.status === 403) {
          setAuxiliaryAccessDenied(true);
          setLogsError("");
          return;
        }
        setLogsError(error.message || "입장 로그를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (generation === logRequestGenerationRef.current && requestingEventId === activeEventIdRef.current) {
          setLogsLoading(false);
        }
      });
  }, [auxiliaryAccessDenied, eventId, logAction, logPage, logResult]);

  useEffect(() => {
    ticketRequestGenerationRef.current += 1;
    logRequestGenerationRef.current += 1;
    checkInRequestGenerationRef.current += 1;
    setAuxiliaryAccessDenied(false);
    setTicketPage(0);
    setLogPage(0);
    setTickets([]);
    setTicketPageInfo({ number: 0, totalPages: 1 });
    setTicketsError("");
    setTicketsLoading(false);
    setLogs([]);
    setLogPageInfo({ number: 0, totalPages: 1 });
    setLogsError("");
    setLogsLoading(false);
    setCheckInResult(null);
    setCheckInError("");
    setProcessing(false);
    scanLockRef.current = false;
  }, [eventId]);

  useEffect(() => {
    setTicketPage(0);
  }, [ticketStatus]);

  useEffect(() => {
    setLogPage(0);
  }, [logAction, logResult]);

  useEffect(() => {
    loadTickets();
  }, [loadTickets]);

  useEffect(() => {
    loadLogs();
  }, [loadLogs]);

  const clearForNext = () => {
    setCheckInResult(null);
    setCheckInError("");
    setManualToken("");
    scanLockRef.current = false;
  };

  const submitCheckIn = useCallback(async (rawToken) => {
    const qrToken = rawToken.trim();
    const normalizedGateName = gateName.trim();
    if (!qrToken) {
      setCheckInError("QR 값을 입력하세요.");
      return;
    }
    if (normalizedGateName.length > 100) {
      setCheckInError("게이트명은 100자 이하로 입력하세요.");
      return;
    }
    if (processing || scanLockRef.current) return;
    const generation = ++checkInRequestGenerationRef.current;
    const submittingEventId = eventId;
    scanLockRef.current = true;
    setProcessing(true);
    setCheckInResult(null);
    setCheckInError("");
    setManualToken("");
    try {
      const payload = {
        qrToken,
        gateName: normalizedGateName || null,
      };
      const result = await admissionApi.checkIn(eventId, payload);
      if (generation !== checkInRequestGenerationRef.current || submittingEventId !== activeEventIdRef.current) return;
      setCheckInResult(result?.data || null);
      if (!auxiliaryAccessDenied) await Promise.all([loadTickets(), loadLogs()]);
    } catch (error) {
      if (generation !== checkInRequestGenerationRef.current || submittingEventId !== activeEventIdRef.current) return;
      setCheckInError(checkInMessage(error));
      if (!auxiliaryAccessDenied) await loadLogs();
    } finally {
      if (generation === checkInRequestGenerationRef.current && submittingEventId === activeEventIdRef.current) {
        setProcessing(false);
      }
    }
  }, [auxiliaryAccessDenied, eventId, gateName, loadLogs, loadTickets, processing]);

  const handleDetected = useCallback((value) => {
    submitCheckIn(value);
  }, [submitCheckIn]);

  const submitManual = () => {
    submitCheckIn(manualToken);
  };

  const cancelCheckIn = async (admissionTicketId) => {
    if (!window.confirm("이 입장 처리를 취소하시겠습니까?")) return;
    setCancellingId(admissionTicketId);
    setTicketsError("");
    try {
      await admissionApi.cancelCheckIn(eventId, admissionTicketId);
      await Promise.all([loadTickets(), loadLogs()]);
    } catch (error) {
      setTicketsError(error.message || "입장 처리 취소에 실패했습니다.");
    } finally {
      setCancellingId(null);
    }
  };

  if (!eventId) {
    return (
      <section className="rounded-xl border border-hairline bg-white p-lg text-caption text-ink-muted">
        현장 입장을 관리할 행사를 선택하세요.
      </section>
    );
  }

  return (
    <section className="space-y-lg">
      <div>
        <h1 className="font-display-lg text-[26px]">현장 입장</h1>
        <p className="mt-xs text-caption text-ink-muted">QR 스캔 또는 직접 입력으로 입장 처리를 진행합니다.</p>
      </div>

      <div className="grid gap-lg lg:grid-cols-[420px_1fr]">
        <div className="space-y-lg">
          <div className="rounded-xl border border-hairline bg-white p-lg space-y-md">
            <div className="flex items-center justify-between">
              <h2 className="font-body-strong">QR 스캔</h2>
              <button
                type="button"
                onClick={() => setScannerActive((active) => !active)}
                className="rounded-full border border-hairline px-md py-1.5 text-caption font-body-strong"
              >
                {scannerActive ? "카메라 끄기" : "카메라 켜기"}
              </button>
            </div>
            {scannerActive && (
              <CameraScanner active={scannerActive} disabled={processing} onDetected={handleDetected} />
            )}
            <input
              type="text"
              value={gateName}
              onChange={(event) => setGateName(event.target.value)}
              maxLength={100}
              placeholder="게이트명 선택 입력"
              className="h-[42px] w-full rounded-lg border border-hairline px-sm text-caption outline-none focus:border-primary-focus"
            />
            <div className="flex gap-sm">
              <input
                type="password"
                value={manualToken}
                onChange={(event) => setManualToken(event.target.value)}
                placeholder="QR 코드 직접 입력"
                className="h-[42px] min-w-0 flex-1 rounded-lg border border-hairline px-sm text-caption outline-none focus:border-primary-focus"
              />
              <button
                type="button"
                onClick={submitManual}
                disabled={processing}
                className="h-[42px] rounded-lg bg-primary px-lg text-caption font-body-strong text-white disabled:opacity-50"
              >
                입장 확인
              </button>
            </div>
            {(checkInResult || checkInError) && (
              <div className={`rounded-xl p-md ${checkInResult ? "bg-status-available/10 text-status-available" : "bg-error/10 text-error"}`}>
                {checkInResult ? (
                  <>
                    <p className="font-body-strong">입장 처리 완료</p>
                    <p className="text-caption">
                      {checkInResult.eventName} · {formatDateTime(checkInResult.processedAt)}
                    </p>
                  </>
                ) : (
                  <p className="text-caption font-body-strong">{checkInError}</p>
                )}
                <button
                  type="button"
                  onClick={clearForNext}
                  className="mt-sm rounded-full border border-current px-md py-1.5 text-caption font-body-strong"
                >
                  다음 입장 처리
                </button>
              </div>
            )}
            {processing && <p className="text-caption text-ink-muted">입장 처리 중입니다.</p>}
          </div>
        </div>

        <div className="space-y-lg">
          {auxiliaryAccessDenied ? (
            <div className="rounded-xl border border-hairline bg-white p-lg">
              <div className="flex items-start gap-sm">
                <Icon name="badge" className="text-[20px] text-primary" />
                <div>
                  <h2 className="font-body-strong">입장 처리 전용 권한</h2>
                  <p className="mt-xs text-caption text-ink-muted">
                    현재 계정은 QR 스캔과 직접 입력을 통한 입장 처리를 사용할 수 있습니다.
                    행사 입장 티켓 목록과 입장 로그는 행사 관리자 권한에서 확인할 수 있습니다.
                  </p>
                </div>
              </div>
            </div>
          ) : (
            <>
          <div className="rounded-xl border border-hairline bg-white">
            <div className="flex flex-wrap items-center justify-between gap-sm border-b border-hairline p-lg">
              <h2 className="font-body-strong">행사 입장 티켓</h2>
              <select
                value={ticketStatus}
                onChange={(event) => {
                  setTicketPage(0);
                  setTicketStatus(event.target.value);
                }}
                className="rounded-lg border border-hairline bg-white px-sm py-1.5 text-caption"
              >
                {ticketStatuses.map((status) => (
                  <option key={status || "ALL"} value={status}>{status ? ticketStatusLabel[status] : "전체"}</option>
                ))}
              </select>
            </div>
            {ticketsLoading && <p className="p-lg text-caption text-ink-muted">입장 티켓 목록을 불러오는 중입니다.</p>}
            {ticketsError && <p className="p-lg text-caption text-error">{ticketsError}</p>}
            {!ticketsLoading && !ticketsError && tickets.length === 0 && (
              <p className="p-lg text-caption text-ink-muted">입장 티켓이 없습니다.</p>
            )}
            {!ticketsLoading && !ticketsError && tickets.map((ticket) => (
              <div key={ticket.admissionTicketId} className="flex items-center gap-md border-b border-divider-soft p-lg last:border-b-0">
                <div className="min-w-0 flex-1">
                  <p className="font-body-strong truncate">{ticket.memberNickname || "회원 정보 없음"}</p>
                  <p className="text-caption text-ink-muted">{ticket.eventName}</p>
                  <p className="text-[11px] text-ink-muted">
                    발급 {formatDateTime(ticket.issuedAt)}
                    {ticket.usedAt ? ` · 입장 ${formatDateTime(ticket.usedAt)}` : ""}
                  </p>
                </div>
                <span className={`rounded-full px-sm py-1 text-[11px] font-bold ${statusPillClass(ticket.status)}`}>
                  {ticketStatusLabel[ticket.status] || ticket.status}
                </span>
                {ticket.status === "USED" && (
                  <button
                    type="button"
                    onClick={() => cancelCheckIn(ticket.admissionTicketId)}
                    disabled={cancellingId === ticket.admissionTicketId}
                    className="rounded-full border border-hairline px-md py-1.5 text-caption font-body-strong disabled:opacity-50"
                  >
                    {cancellingId === ticket.admissionTicketId ? "처리 중" : "입장 취소"}
                  </button>
                )}
              </div>
            ))}
            {!ticketsError && (
              <Pagination
                pageInfo={ticketPageInfo}
                loading={ticketsLoading}
                onPrev={() => setTicketPage((page) => Math.max(0, page - 1))}
                onNext={() => setTicketPage((page) => Math.min(ticketPageInfo.totalPages - 1, page + 1))}
              />
            )}
          </div>

          <div className="rounded-xl border border-hairline bg-white">
            <div className="flex flex-wrap items-center justify-between gap-sm border-b border-hairline p-lg">
              <h2 className="font-body-strong">입장 로그</h2>
              <div className="flex gap-xs">
                <select
                  value={logAction}
                  onChange={(event) => {
                    setLogPage(0);
                    setLogAction(event.target.value);
                  }}
                  className="rounded-lg border border-hairline bg-white px-sm py-1.5 text-caption"
                >
                  {actions.map((action) => (
                    <option key={action || "ALL"} value={action}>{action ? actionLabel[action] : "Action 전체"}</option>
                  ))}
                </select>
                <select
                  value={logResult}
                  onChange={(event) => {
                    setLogPage(0);
                    setLogResult(event.target.value);
                  }}
                  className="rounded-lg border border-hairline bg-white px-sm py-1.5 text-caption"
                >
                  {results.map((result) => (
                    <option key={result || "ALL"} value={result}>{result ? resultLabel[result] : "Result 전체"}</option>
                  ))}
                </select>
              </div>
            </div>
            {logsLoading && <p className="p-lg text-caption text-ink-muted">입장 로그를 불러오는 중입니다.</p>}
            {logsError && <p className="p-lg text-caption text-error">{logsError}</p>}
            {!logsLoading && !logsError && logs.length === 0 && (
              <p className="p-lg text-caption text-ink-muted">입장 로그가 없습니다.</p>
            )}
            {!logsLoading && !logsError && logs.map((log) => (
              <div key={log.admissionLogId} className="flex items-center gap-md border-b border-divider-soft p-lg last:border-b-0">
                <div className="min-w-0 flex-1">
                  <p className="font-body-strong">{actionLabel[log.action] || log.action}</p>
                  <p className="text-caption text-ink-muted">
                    {log.staffNickname || "스태프"}
                  </p>
                  <p className="text-[11px] text-ink-muted">
                    {formatDateTime(log.processedAt)}{log.gateName ? ` · ${log.gateName}` : ""}
                  </p>
                </div>
                <span className={`rounded-full px-sm py-1 text-[11px] font-bold ${statusPillClass(log.result)}`}>
                  {resultLabel[log.result] || log.result}
                </span>
              </div>
            ))}
            {!logsError && (
              <Pagination
                pageInfo={logPageInfo}
                loading={logsLoading}
                onPrev={() => setLogPage((page) => Math.max(0, page - 1))}
                onNext={() => setLogPage((page) => Math.min(logPageInfo.totalPages - 1, page + 1))}
              />
            )}
          </div>
            </>
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
