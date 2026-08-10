import { useEffect, useState } from "react";
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
  PARTIAL_CANCELED: "부분 취소",
  CANCELED: "취소",
};

export default function PaymentDetail() {
  const { paymentId } = useParams();
  const [params] = useSearchParams();
  const [payment, setPayment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  useEffect(() => {
    let cancelled = false;
    const orderNo = params.get("orderNo");
    const orderAccessToken = orderNo ? sessionStorage.getItem(`ticket-order-token:${orderNo}`) : null;
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
  }, [paymentId, params]);

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
          <section className="rounded-2xl border border-hairline bg-white p-xl">
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
