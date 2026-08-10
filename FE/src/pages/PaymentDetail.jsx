import { useCallback, useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { paymentApi } from "../api/paymentApi.js";
import TopNav from "../components/TopNav.jsx";

const formatDateTime = (value) =>
  value ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-";

const formatMoney = (value) =>
  `${Number(value || 0).toLocaleString("ko-KR")}원`;

const statusLabel = {
  READY: "결제 대기",
  DONE: "결제 완료",
  PAID: "결제 완료",
  FAILED: "결제 실패",
  CANCELLED: "취소",
  CANCELED: "취소",
  PARTIAL_CANCELED: "부분 취소",
  REFUNDED: "환불 완료",
};

const refundStatusLabel = {
  REQUESTED: "환불 요청",
  COMPLETED: "환불 완료",
  FAILED: "환불 실패",
  REJECTED: "환불 거절",
};

export default function PaymentDetail() {
  const { paymentId } = useParams();
  const [params] = useSearchParams();
  const orderNo = params.get("orderNo");
  const orderAccessToken = orderNo ? sessionStorage.getItem(`ticket-order-token:${orderNo}`) : null;
  const [payment, setPayment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [reason, setReason] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [refundMessage, setRefundMessage] = useState("");
  const [refundError, setRefundError] = useState("");
  const [createdRefund, setCreatedRefund] = useState(null);

  const loadPayment = useCallback(() => {
    setLoading(true);
    setError("");
    return paymentApi.getPayment(paymentId, orderAccessToken)
      .then((result) => setPayment(result?.data || null))
      .catch((requestError) => setError(requestError.message || "결제 상세를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [paymentId, orderAccessToken]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    paymentApi.getPayment(paymentId, orderAccessToken)
      .then((result) => {
        if (!cancelled) setPayment(result?.data || null);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message || "결제 상세를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [paymentId, orderAccessToken]);

  const canShowRefundForm =
    payment?.paymentStatus === "PAID" && payment?.ticketOrderStatus === "CONFIRMED";

  const submitRefund = async (event) => {
    event.preventDefault();
    if (submitting) return;
    const trimmedReason = reason.trim();
    if (!trimmedReason) {
      setRefundError("환불 사유를 입력해 주세요.");
      return;
    }
    if (!window.confirm("해당 결제 전체를 환불 신청하시겠습니까?")) return;

    setSubmitting(true);
    setRefundError("");
    setRefundMessage("");
    try {
      const result = await paymentApi.requestRefund(paymentId, { reason: trimmedReason }, orderAccessToken);
      const refund = result?.data || null;
      setCreatedRefund(refund);
      setRefundMessage("환불 신청이 처리되었습니다.");
      setReason("");
      await loadPayment();
    } catch (requestError) {
      setRefundError(requestError.message || "환불 신청에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface-container-low text-on-surface">
      <TopNav active="mypage" />
      <main className="mx-auto max-w-[760px] px-lg pb-xxl pt-[96px]">
        <div className="mb-lg flex items-center justify-between gap-md">
          <div>
            <p className="text-caption text-primary">PAYMENT</p>
            <h1 className="font-display-lg text-[30px]">결제 상세</h1>
          </div>
          {payment?.orderNo && (
            <Link to={`/tickets/orders/${payment.orderNo}`} className="rounded-full border border-hairline px-lg py-sm text-caption font-body-strong">
              주문 상세
            </Link>
          )}
        </div>

        {loading && <div className="rounded-2xl border border-hairline bg-white p-xl text-center text-ink-muted">결제 정보를 불러오는 중입니다.</div>}
        {error && <div className="rounded-2xl border border-error/20 bg-error/10 p-lg text-error">{error}</div>}
        {!loading && !error && !payment && (
          <div className="rounded-2xl border border-hairline bg-white p-xl text-center text-ink-muted">결제 정보를 찾을 수 없습니다.</div>
        )}

        {payment && (
          <section className="space-y-lg">
            <div className="rounded-2xl border border-hairline bg-white p-xl">
              <div className="mb-lg flex flex-wrap items-start justify-between gap-md">
                <div>
                  <h2 className="font-display-md text-[24px]">{payment.eventName}</h2>
                  <p className="mt-xs text-caption text-ink-muted">결제 ID {payment.paymentId}</p>
                </div>
                <span className="rounded-full bg-primary/10 px-md py-1 text-caption font-body-strong text-primary">
                  {statusLabel[payment.paymentStatus] || payment.paymentStatus}
                </span>
              </div>
              <div className="grid gap-md sm:grid-cols-2">
                <Info label="주문번호" value={payment.orderNo} />
                <Info label="결제 금액" value={formatMoney(payment.amount)} />
                <Info label="결제 수단" value={payment.method || "-"} />
                <Info label="PG사" value={payment.pgProvider || "-"} />
                <Info label="요청 시각" value={formatDateTime(payment.requestedAt)} />
                <Info label="승인 시각" value={formatDateTime(payment.approvedAt)} />
              </div>
            </div>

            <div className="rounded-2xl border border-hairline bg-white p-xl">
              <h2 className="mb-md font-display-md text-[22px]">환불</h2>
              {canShowRefundForm ? (
                <form onSubmit={submitRefund} className="space-y-md">
                  <div className="rounded-xl bg-surface-container p-md">
                    <p className="text-caption text-ink-muted">환불 대상</p>
                    <p className="font-body-strong">{payment.eventName} · {formatMoney(payment.amount)}</p>
                    <p className="mt-xs text-[11px] text-ink-muted">결제 전체 금액 환불만 지원됩니다.</p>
                  </div>
                  <label className="block">
                    <span className="text-caption text-ink-muted">환불 사유</span>
                    <textarea
                      required
                      maxLength={200}
                      value={reason}
                      onChange={(event) => setReason(event.target.value)}
                      className="mt-xs min-h-24 w-full rounded-lg border border-hairline px-md py-sm outline-none focus:border-primary-focus"
                      placeholder="환불 사유를 입력해 주세요."
                    />
                  </label>
                  {refundError && <p className="rounded-lg bg-error/10 p-sm text-caption text-error">{refundError}</p>}
                  {refundMessage && (
                    <div className="rounded-lg bg-primary/10 p-sm text-caption text-primary">
                      <p>{refundMessage}</p>
                      {createdRefund?.refundId && (
                        <Link to={`/refunds/${createdRefund.refundId}?orderNo=${encodeURIComponent(payment.orderNo || "")}`} className="mt-xs inline-block font-body-strong underline">
                          환불 상세 보기
                        </Link>
                      )}
                    </div>
                  )}
                  <button disabled={submitting} className="w-full rounded-full bg-primary px-lg py-sm text-white disabled:opacity-50">
                    {submitting ? "환불 신청 중..." : "환불 신청"}
                  </button>
                </form>
              ) : (
                <p className="text-caption text-ink-muted">
                  {payment.paymentStatus === "REFUNDED" || payment.ticketOrderStatus === "REFUNDED"
                    ? "이미 환불 처리된 결제입니다."
                    : "현재 상태에서는 환불 신청 버튼을 표시하지 않습니다."}
                </p>
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
      <p className="mt-1 font-body-strong">{value || "-"}</p>
    </div>
  );
}
