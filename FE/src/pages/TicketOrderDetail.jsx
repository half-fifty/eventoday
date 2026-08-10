import { useEffect, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { paymentApi } from "../api/paymentApi.js";
import TopNav from "../components/TopNav.jsx";

const formatDateTime = (value) =>
  value ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-";

const formatMoney = (value) =>
  `${Number(value || 0).toLocaleString("ko-KR")}원`;

const statusLabel = {
  PENDING: "대기",
  PAID: "결제 완료",
  CONFIRMED: "확정",
  CANCELLED: "취소",
  EXPIRED: "만료",
  FAILED: "실패",
  REFUNDED: "환불 완료",
};

export default function TicketOrderDetail() {
  const { orderNo } = useParams();
  const [order, setOrder] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    const orderAccessToken = sessionStorage.getItem(`ticket-order-token:${orderNo}`);
    setLoading(true);
    setError("");
    paymentApi.getTicketOrder(orderNo, orderAccessToken)
      .then((result) => {
        if (!cancelled) setOrder(result?.data || null);
      })
      .catch((requestError) => {
        if (!cancelled) setError(requestError.message || "주문 상세를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [orderNo]);

  const paymentId = order?.paymentId;

  return (
    <div className="min-h-screen bg-surface-container-low text-on-surface">
      <TopNav active="mypage" />
      <main className="mx-auto max-w-[820px] px-lg pb-xxl pt-[96px]">
        <div className="mb-lg flex items-center justify-between gap-md">
          <div>
            <p className="text-caption text-primary">TICKET ORDER</p>
            <h1 className="font-display-lg text-[30px]">주문 상세</h1>
          </div>
          <Link to="/mypage" className="rounded-full border border-hairline px-lg py-sm text-caption font-body-strong">
            예매 내역
          </Link>
        </div>

        {loading && <div className="rounded-2xl border border-hairline bg-white p-xl text-center text-ink-muted">주문 정보를 불러오는 중입니다.</div>}
        {error && <div className="rounded-2xl border border-error/20 bg-error/10 p-lg text-error">{error}</div>}
        {!loading && !error && !order && (
          <div className="rounded-2xl border border-hairline bg-white p-xl text-center text-ink-muted">주문 정보를 찾을 수 없습니다.</div>
        )}

        {order && (
          <section className="space-y-lg">
            <div className="rounded-2xl border border-hairline bg-white p-xl">
              <div className="mb-lg flex flex-wrap items-start justify-between gap-md">
                <div>
                  <h2 className="font-display-md text-[24px]">{order.eventName}</h2>
                  <p className="mt-xs text-caption text-ink-muted">주문번호 {order.orderNo}</p>
                </div>
                <span className="rounded-full bg-primary/10 px-md py-1 text-caption font-body-strong text-primary">
                  {statusLabel[order.ticketOrderStatus] || order.ticketOrderStatus}
                </span>
              </div>
              <div className="grid gap-md sm:grid-cols-2">
                <Info label="수량" value={`${order.quantity}매`} />
                <Info label="단가" value={formatMoney(order.unitPrice)} />
                <Info label="총 금액" value={formatMoney(order.totalAmount)} />
                <Info label="주문 일시" value={formatDateTime(order.createdAt)} />
                <Info label="주문 확정" value={formatDateTime(order.confirmedAt)} />
                <Info label="결제 주문 상태" value={order.paymentRequired ? (statusLabel[order.paymentOrderStatus] || order.paymentOrderStatus) : "무료 티켓"} />
              </div>
            </div>

            <div className="rounded-2xl border border-hairline bg-white p-xl">
              <h2 className="mb-md font-display-md text-[22px]">결제 정보</h2>
              {!order.paymentRequired ? (
                <p className="text-ink-muted">무료 티켓이라 별도 결제 정보가 없습니다.</p>
              ) : paymentId ? (
                <Link to={`/payments/${paymentId}?orderNo=${encodeURIComponent(order.orderNo)}`} className="inline-flex rounded-full bg-primary px-lg py-sm text-white">
                  결제 상세 보기
                </Link>
              ) : (
                <p className="text-caption text-ink-muted">현재 주문 상세 응답에 결제 식별자가 없어 결제 상세로 바로 이동할 수 없습니다.</p>
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
