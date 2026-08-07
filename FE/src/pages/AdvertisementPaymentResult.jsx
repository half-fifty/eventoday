import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { paymentApi } from "../api/paymentApi.js";

export default function AdvertisementPaymentResult({ failed = false }) {
  const [params] = useSearchParams();
  const [state, setState] = useState(failed ? "failed" : "confirming");
  const [message, setMessage] = useState(failed ? params.get("message") || "결제 인증이 취소되었거나 실패했습니다." : "결제를 승인하고 있습니다.");
  const paymentKey = params.get("paymentKey");
  const orderId = params.get("orderId");
  const rawAmount = params.get("amount");
  const amount = rawAmount && /^\d+$/.test(rawAmount) ? Number(rawAmount) : Number.NaN;
  const validAmount = Number.isSafeInteger(amount) && amount > 0;
  const confirmationStartedRef = useRef(false);

  useEffect(() => {
    if (failed || confirmationStartedRef.current) return;
    confirmationStartedRef.current = true;
    if (!paymentKey || !orderId || !validAmount) { setState("failed"); setMessage("결제 결과 정보가 올바르지 않습니다."); return; }
    paymentApi.confirm({ paymentKey, orderId, amount })
      .then(() => { setState("success"); setMessage("광고비 결제가 완료되었습니다. 플랫폼 심사를 기다려 주세요."); })
      .catch((error) => { setState("failed"); setMessage(error.message || "결제 승인에 실패했습니다."); });
  }, [failed, paymentKey, orderId, amount, validAmount]);
  return <main className="min-h-screen bg-surface-container-low grid place-items-center p-lg"><section className="w-full max-w-lg bg-white border border-hairline rounded-2xl p-xl text-center space-y-lg"><div className={`text-5xl ${state === "success" ? "text-primary" : state === "failed" ? "text-error" : "text-ink-muted"}`}>{state === "success" ? "✓" : state === "failed" ? "!" : "…"}</div><h1 className="font-display-lg text-[28px]">{state === "confirming" ? "결제 확인 중" : state === "success" ? "결제 완료" : "결제 실패"}</h1><p className="text-ink-muted">{message}</p><Link to="/organizer-admin/advertisements" className="inline-block px-xl py-sm bg-primary text-white rounded-full">광고 관리로 돌아가기</Link></section></main>;
}
