import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { paymentApi } from "../api/paymentApi.js";

export default function TicketPaymentResult({ failed = false }) {
  const [params] = useSearchParams();
  const [state, setState] = useState(failed ? "failed" : "confirming");
  const [message, setMessage] = useState(failed ? params.get("message") || "결제가 취소되었거나 실패했습니다." : "결제를 승인하고 있습니다.");
  const eventId = params.get("eventId");
  const paymentKey = params.get("paymentKey");
  const orderId = params.get("orderId");
  const amount = Number(params.get("amount"));
  const confirmationStartedRef = useRef(false);

  useEffect(() => {
    if (failed || confirmationStartedRef.current) return;
    confirmationStartedRef.current = true;
    if (!paymentKey || !orderId || !Number.isFinite(amount)) {
      setState("failed");
      setMessage("결제 결과 정보가 올바르지 않습니다.");
      return;
    }
    const tokenKey = `ticket-order-token:${orderId}`;
    const orderAccessToken = sessionStorage.getItem(tokenKey);
    paymentApi.confirm({ paymentKey, orderId, amount }, orderAccessToken)
      .then(() => {
        sessionStorage.removeItem(tokenKey);
        setState("success");
        setMessage("티켓 결제가 완료되었습니다. 예매 내역에서 입장권을 확인해 주세요.");
      })
      .catch((error) => {
        setState("failed");
        setMessage(error.message || "결제 승인에 실패했습니다.");
      });
  }, [failed, paymentKey, orderId, amount]);

  return <main className="min-h-screen bg-surface-container-low grid place-items-center p-lg"><section className="w-full max-w-lg bg-white border border-hairline rounded-2xl p-xl text-center space-y-lg"><div className={`text-5xl ${state === "success" ? "text-primary" : state === "failed" ? "text-error" : "text-ink-muted"}`}>{state === "success" ? "✓" : state === "failed" ? "!" : "…"}</div><h1 className="font-display-lg text-[28px]">{state === "confirming" ? "결제 확인 중" : state === "success" ? "예매 완료" : "결제 실패"}</h1><p className="text-ink-muted">{message}</p><div className="flex justify-center gap-sm">{eventId && <Link to={`/events/${eventId}`} className="px-lg py-sm border border-hairline rounded-full">행사로 돌아가기</Link>}<Link to="/mypage" className="px-lg py-sm bg-primary text-white rounded-full">예매 내역 보기</Link></div></section></main>;
}
