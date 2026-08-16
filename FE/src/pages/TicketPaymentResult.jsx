import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { paymentApi } from "../api/paymentApi.js";
import useAuth from "../hooks/useAuth.js";

export default function TicketPaymentResult({ failed = false }) {
  const { isAuthenticated } = useAuth();
  const [params] = useSearchParams();
  const [state, setState] = useState(failed ? "failed" : "confirming");
  const [message, setMessage] = useState(
    failed ? params.get("message") || "결제가 취소되었거나 실패했습니다." : "결제를 확인하고 있습니다."
  );
  const [copyState, setCopyState] = useState("");
  const [accountCopyState, setAccountCopyState] = useState("");
  const [virtualAccount, setVirtualAccount] = useState(null);
  const eventId = params.get("eventId");
  const paymentKey = params.get("paymentKey");
  const orderId = params.get("orderId");
  const rawAmount = params.get("amount");
  const amount = rawAmount && /^\d+$/.test(rawAmount) ? Number(rawAmount) : Number.NaN;
  const validAmount = Number.isSafeInteger(amount) && amount > 0;
  const confirmationStartedRef = useRef(false);
  const [confirmedOrderNo, setConfirmedOrderNo] = useState(orderId);
  const [confirmedPaymentId, setConfirmedPaymentId] = useState(null);

  useEffect(() => {
    if (failed || confirmationStartedRef.current) return;
    confirmationStartedRef.current = true;
    if (!paymentKey || !orderId || !validAmount) {
      setState("failed");
      setMessage("결제 결과 정보가 올바르지 않습니다.");
      return;
    }
    const tokenKey = `ticket-order-token:${orderId}`;
    const orderAccessToken = sessionStorage.getItem(tokenKey);
    paymentApi.confirm({ paymentKey, orderId, amount }, orderAccessToken)
      .then((result) => {
        const confirmed = result?.data;
        if (confirmed?.paymentId) setConfirmedPaymentId(confirmed.paymentId);
        if (confirmed?.orderNo) setConfirmedOrderNo(confirmed.orderNo);
        if (confirmed?.paymentStatus === "WAITING_FOR_DEPOSIT") {
          setVirtualAccount(confirmed.virtualAccount || null);
          setState("waiting");
          setMessage("가상계좌가 발급되었습니다. 입금이 확인되면 예매가 확정됩니다.");
          return;
        }
        setState("success");
        setMessage("티켓 결제가 완료되었습니다. 예매 내역에서 입장권을 확인해 주세요.");
      })
      .catch((error) => {
        setState("failed");
        setMessage(error.message || "결제 확인에 실패했습니다.");
      });
  }, [failed, paymentKey, orderId, amount, validAmount]);

  const copyOrderNo = async () => {
    if (!confirmedOrderNo) return;
    try {
      await navigator.clipboard.writeText(confirmedOrderNo);
      setCopyState("주문번호를 복사했습니다.");
    } catch {
      setCopyState("복사하지 못했습니다. 주문번호를 직접 선택해 복사해 주세요.");
    }
  };

  const copyAccountNumber = async () => {
    if (!virtualAccount?.accountNumber) return;
    try {
      await navigator.clipboard.writeText(virtualAccount.accountNumber);
      setAccountCopyState("계좌번호를 복사했습니다.");
    } catch {
      setAccountCopyState("복사하지 못했습니다. 계좌번호를 직접 선택해 복사해 주세요.");
    }
  };

  const reservationPath =
    !isAuthenticated && confirmedOrderNo
      ? `/guest/orders/${encodeURIComponent(confirmedOrderNo)}`
      : "/mypage";
  const showGuestOrderGuide = (state === "success" || state === "waiting") && !isAuthenticated && confirmedOrderNo;

  return (
    <main className="grid min-h-screen place-items-center bg-surface-container-low p-lg">
      <section className="w-full max-w-lg space-y-lg rounded-2xl border border-hairline bg-white p-xl text-center">
        <div className={`text-5xl ${state === "success" || state === "waiting" ? "text-primary" : state === "failed" ? "text-error" : "text-ink-muted"}`}>
          {state === "success" ? "✓" : state === "waiting" ? "₩" : state === "failed" ? "!" : "…"}
        </div>
        <h1 className="font-display-lg text-[28px]">
          {state === "confirming" ? "결제 확인 중" : state === "success" ? "예매 완료" : state === "waiting" ? "입금 대기" : "결제 실패"}
        </h1>
        <p className="text-ink-muted">{message}</p>

        {state === "waiting" && virtualAccount && (
          <div className="rounded-xl border border-primary/20 bg-primary/5 p-lg text-left">
            <p className="text-caption text-ink-muted">입금 계좌</p>
            <p className="mt-xs text-lg font-bold text-on-surface">
              {virtualAccount.bankCode || "은행"} {virtualAccount.accountNumber}
            </p>
            <p className="mt-xs text-caption text-ink-muted">
              예금주 {virtualAccount.customerName || "-"} · 입금액 {Number(virtualAccount.amount || 0).toLocaleString("ko-KR")}원
            </p>
            <p className="text-caption text-ink-muted">
              입금기한 {virtualAccount.dueAt ? new Date(virtualAccount.dueAt).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-"}
            </p>
            <button
              type="button"
              onClick={copyAccountNumber}
              className="mt-md rounded-full border border-hairline px-md py-1.5 text-caption font-body-strong"
            >
              계좌번호 복사
            </button>
            {accountCopyState && <p className="mt-xs text-caption text-primary">{accountCopyState}</p>}
          </div>
        )}

        {showGuestOrderGuide && (
          <div className="rounded-xl border border-primary/20 bg-primary/5 p-lg text-left">
            <p className="text-caption text-ink-muted">주문번호</p>
            <p className="mt-xs break-all font-mono text-[20px] font-bold text-on-surface">
              {confirmedOrderNo}
            </p>
            <button
              type="button"
              onClick={copyOrderNo}
              className="mt-md rounded-full border border-hairline px-md py-1.5 text-caption font-body-strong"
            >
              주문번호 복사
            </button>
            {copyState && <p className="mt-xs text-caption text-primary">{copyState}</p>}
            <p className="mt-md text-caption text-ink-muted">
              ※ 비회원 예매 조회 시 필요한 번호입니다.<br />
              구매 시 입력한 이메일로 주문번호 안내 메일 발송을 시도합니다.<br />
              메일을 받지 못할 수 있으니 현재 주문번호를 보관해주세요.
            </p>
          </div>
        )}

        <div className="flex flex-wrap justify-center gap-sm">
          {eventId && (
            <Link to={`/events/${eventId}`} className="rounded-full border border-hairline px-lg py-sm">
              행사로 돌아가기
            </Link>
          )}
          {(state === "success" || state === "waiting") && confirmedOrderNo && (
            <Link to={`/tickets/orders/${encodeURIComponent(confirmedOrderNo)}`} className="rounded-full border border-hairline px-lg py-sm">
              주문 상세 보기
            </Link>
          )}
          {(state === "success" || state === "waiting") && confirmedPaymentId && (
            <Link
              to={`/payments/${confirmedPaymentId}?orderNo=${encodeURIComponent(confirmedOrderNo || "")}`}
              className="rounded-full border border-hairline px-lg py-sm"
            >
              결제 상세 보기
            </Link>
          )}
          <Link to={reservationPath} className="rounded-full bg-primary px-lg py-sm text-white">
            예매 내역 보기
          </Link>
        </div>
      </section>
    </main>
  );
}
