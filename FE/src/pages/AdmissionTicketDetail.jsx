import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { admissionApi } from "../api/admissionApi.js";
import TopNav from "../components/TopNav.jsx";
import { shareOrDownloadAdmissionTicketImage } from "../utils/admissionTicketImage.js";

const formatDateTime = (value) =>
  value ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-";

const admissionStatusLabel = {
  ISSUED: "사용 전",
  USED: "입장 완료",
  CANCELLED: "취소",
  EXPIRED: "만료",
};

const exchangeCodeStatusLabel = {
  ISSUED: "사용 전",
  REDEEMED: "사용 완료",
  CANCELLED: "취소",
  EXPIRED: "만료",
};

export default function AdmissionTicketDetail() {
  const { admissionTicketId } = useParams();
  const [ticket, setTicket] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [qrUrl, setQrUrl] = useState("");
  const [qrLoading, setQrLoading] = useState(false);
  const [qrError, setQrError] = useState("");
  const [savingImage, setSavingImage] = useState(false);
  const [saveMessage, setSaveMessage] = useState("");
  const [saveError, setSaveError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    setTicket(null);
    admissionApi.getAdmissionTicket(admissionTicketId)
      .then((result) => {
        if (!cancelled) setTicket(result?.data || null);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message || "입장 티켓 상세를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [admissionTicketId]);

  const saveTicketImage = async () => {
    if (!ticket || !qrUrl || savingImage) return;
    setSavingImage(true);
    setSaveMessage("");
    setSaveError("");
    try {
      const result = await shareOrDownloadAdmissionTicketImage({
        ticket,
        qrImageUrl: qrUrl,
      });
      if (result.action === "downloaded" || result.action === "shared") {
        setSaveMessage("입장권 이미지가 저장되었습니다.");
      }
    } catch {
      setSaveError("입장권 이미지를 저장하지 못했습니다.");
    } finally {
      setSavingImage(false);
    }
  };

  useEffect(() => {
    if (!ticket?.qrAvailable) {
      setQrUrl("");
      setQrError("");
      setQrLoading(false);
      return undefined;
    }

    let cancelled = false;
    let objectUrl = "";
    setQrLoading(true);
    setQrError("");
    setQrUrl("");
    admissionApi.getAdmissionTicketQr(ticket.admissionTicketId)
      .then((blob) => {
        if (cancelled) return;
        objectUrl = URL.createObjectURL(blob);
        setQrUrl(objectUrl);
      })
      .catch((requestError) => {
        if (!cancelled) setQrError(requestError.message || "입장 QR을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setQrLoading(false);
      });

    return () => {
      cancelled = true;
      if (objectUrl) URL.revokeObjectURL(objectUrl);
    };
  }, [ticket?.admissionTicketId, ticket?.qrAvailable]);

  return (
    <div className="min-h-screen bg-surface-container-low text-on-surface">
      <TopNav active="mypage" />
      <main className="mx-auto max-w-[760px] px-lg pb-xxl pt-[96px]">
        <div className="mb-lg flex items-center justify-between gap-md">
          <div>
            <p className="text-caption text-primary">ADMISSION TICKET</p>
            <h1 className="font-display-lg text-[30px]">입장 티켓 상세</h1>
          </div>
          <Link to="/mypage" className="rounded-full border border-hairline px-lg py-sm text-caption font-body-strong">
            마이페이지
          </Link>
        </div>

        {loading && <div className="rounded-2xl border border-hairline bg-white p-xl text-center text-ink-muted">입장 티켓을 불러오는 중입니다.</div>}
        {error && <div className="rounded-2xl border border-error/20 bg-error/10 p-lg text-error">{error}</div>}
        {!loading && !error && !ticket && (
          <div className="rounded-2xl border border-hairline bg-white p-xl text-center text-ink-muted">입장 티켓을 찾을 수 없습니다.</div>
        )}

        {ticket && (
          <section className="space-y-lg">
            <div className="rounded-2xl border border-hairline bg-white p-xl">
              <div className="mb-lg flex flex-wrap items-start justify-between gap-md">
                <div>
                  <h2 className="font-display-md text-[24px]">{ticket.eventName}</h2>
                  <p className="mt-xs text-caption text-ink-muted">입장 티켓 ID {ticket.admissionTicketId}</p>
                </div>
                <span className="rounded-full bg-primary/10 px-md py-1 text-caption font-body-strong text-primary">
                  {admissionStatusLabel[ticket.admissionTicketStatus] || ticket.admissionTicketStatus}
                </span>
              </div>
              <div className="grid gap-md sm:grid-cols-2">
                <Info label="행사 ID" value={ticket.eventId} />
                <Info label="교환 코드 상태" value={exchangeCodeStatusLabel[ticket.exchangeCodeStatus] || ticket.exchangeCodeStatus} />
                <Info label="발급 시각" value={formatDateTime(ticket.issuedAt)} />
                <Info label="사용 시각" value={formatDateTime(ticket.usedAt)} />
                <Info label="취소 시각" value={formatDateTime(ticket.cancelledAt)} />
                <Info label="QR 조회 가능" value={ticket.qrAvailable ? "가능" : "불가"} />
              </div>
            </div>

            <div className="rounded-2xl border border-hairline bg-white p-xl text-center">
              <h2 className="mb-md font-display-md text-[22px]">입장 QR</h2>
              {!ticket.qrAvailable && (
                <p className="text-caption text-ink-muted">현재 상태에서는 입장 QR을 조회할 수 없습니다.</p>
              )}
              {ticket.qrAvailable && qrLoading && (
                <p className="text-caption text-ink-muted">입장 QR을 불러오는 중입니다.</p>
              )}
              {ticket.qrAvailable && qrError && (
                <p className="rounded-lg bg-error/10 p-sm text-caption text-error">{qrError}</p>
              )}
              {ticket.qrAvailable && qrUrl && !qrLoading && !qrError && (
                <>
                  <img src={qrUrl} alt={`${ticket.eventName} 입장 QR`} className="mx-auto h-56 w-56 rounded-xl border border-hairline bg-white p-sm" />
                  <div className="mt-lg flex flex-wrap justify-center gap-sm">
                    <button
                      type="button"
                      onClick={saveTicketImage}
                      disabled={savingImage}
                      className="flex w-fit items-center gap-xs rounded-full border border-primary px-xl py-md text-caption font-body-strong text-primary transition-colors hover:bg-primary/5 disabled:opacity-50"
                    >
                      {savingImage ? "저장 중..." : "이미지 저장"}
                    </button>
                  </div>
                  {saveMessage && <p className="mt-sm text-caption text-primary">{saveMessage}</p>}
                  {saveError && <p className="mt-sm rounded-lg bg-error/10 p-sm text-caption text-error">{saveError}</p>}
                  <Link
                    to={`/events/${ticket.eventId}/ongoing`}
                    className="mx-auto mt-lg flex w-fit items-center gap-xs rounded-full bg-primary px-xl py-md text-caption font-body-strong text-white transition-colors hover:bg-primary-focus"
                  >
                    행사 화면으로 이동
                  </Link>
                </>
              )}
            </div>
          </section>
        )}
      </main>
    </div>
  );
}

function Info({ label, value }) {
  return (
    <div className="rounded-xl bg-surface-container p-md">
      <p className="text-[11px] text-ink-muted">{label}</p>
      <p className="mt-1 font-body-strong break-words">{value || "-"}</p>
    </div>
  );
}
