import { useEffect, useState } from "react";
import { Link, useParams, useSearchParams } from "react-router-dom";
import { paymentApi } from "../api/paymentApi.js";
import TopNav from "../components/TopNav.jsx";
import useAuth from "../hooks/useAuth.js";

const formatDateTime = (value) =>
  value ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-";

const formatMoney = (value) =>
  `${Number(value || 0).toLocaleString("ko-KR")}원`;

const refundStatusLabel = {
  REQUESTED: "환불 요청",
  COMPLETED: "환불 완료",
  FAILED: "환불 실패",
  REJECTED: "환불 거절",
};

const ticketStatusLabel = {
  PENDING_PAYMENT: "결제 대기",
  CONFIRMED: "확정",
  REFUNDED: "환불 완료",
};

export default function RefundDetail() {
  const { isAuthenticated } = useAuth();
  const { refundId } = useParams();
  const [params] = useSearchParams();
  const orderNo = params.get("orderNo");
  const orderAccessToken = orderNo ? sessionStorage.getItem(`ticket-order-token:${orderNo}`) : null;
  const backPath = !isAuthenticated && orderNo && orderAccessToken
    ? `/guest/orders/${encodeURIComponent(orderNo)}`
    : "/mypage";
  const [refund, setRefund] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    paymentApi.getRefund(refundId, orderAccessToken)
      .then((result) => {
        if (!cancelled) setRefund(result?.data || null);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message || "환불 상세를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [refundId, orderAccessToken]);

  return (
    <div className="min-h-screen bg-surface-container-low text-on-surface">
      <TopNav active="mypage" />
      <main className="mx-auto max-w-[760px] px-lg pb-xxl pt-[96px]">
        <div className="mb-lg flex items-center justify-between gap-md">
          <div>
            <p className="text-caption text-primary">REFUND</p>
            <h1 className="font-display-lg text-[30px]">환불 상세</h1>
          </div>
          <Link to={backPath} className="rounded-full border border-hairline px-lg py-sm text-caption font-body-strong">
            {backPath === "/mypage" ? "마이페이지" : "비회원 예매 관리"}
          </Link>
        </div>

        {loading && <div className="rounded-2xl border border-hairline bg-white p-xl text-center text-ink-muted">환불 정보를 불러오는 중입니다.</div>}
        {error && <div className="rounded-2xl border border-error/20 bg-error/10 p-lg text-error">{error}</div>}
        {!loading && !error && !refund && (
          <div className="rounded-2xl border border-hairline bg-white p-xl text-center text-ink-muted">환불 정보를 찾을 수 없습니다.</div>
        )}

        {refund && (
          <section className="rounded-2xl border border-hairline bg-white p-xl">
            <div className="mb-lg flex flex-wrap items-start justify-between gap-md">
              <div>
                <h2 className="font-display-md text-[24px]">{refund.eventName}</h2>
              </div>
              <span className="rounded-full bg-primary/10 px-md py-1 text-caption font-body-strong text-primary">
                {refundStatusLabel[refund.refundStatus] || refund.refundStatus}
              </span>
            </div>
            <div className="grid gap-md sm:grid-cols-2">
              <Info label="주문번호" value={refund.orderNo} />
              <Info label="환불 금액" value={formatMoney(refund.refundAmount)} />
              <Info label="결제 수단" value={refund.paymentMethod || "-"} />
              <Info label="티켓 주문 상태" value={ticketStatusLabel[refund.ticketOrderStatus] || refund.ticketOrderStatus} />
              <Info label="신청 시각" value={formatDateTime(refund.requestedAt)} />
              <Info label="처리 시각" value={formatDateTime(refund.completedAt)} />
              <Info label="환불 사유" value={refund.refundReason} />
            </div>
            {refund.paymentId && (
              <Link to={`/payments/${refund.paymentId}?orderNo=${encodeURIComponent(refund.orderNo || "")}`} className="mt-lg inline-flex rounded-full border border-hairline px-lg py-sm text-caption font-body-strong">
                결제 상세
              </Link>
            )}
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
