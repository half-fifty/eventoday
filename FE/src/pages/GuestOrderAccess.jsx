import { useState } from "react";
import { Link, useNavigate } from "react-router-dom";
import { paymentApi } from "../api/paymentApi.js";
import TopNav from "../components/TopNav.jsx";

export default function GuestOrderAccess() {
  const navigate = useNavigate();
  const [orderNo, setOrderNo] = useState("");
  const [email, setEmail] = useState("");
  const [phone, setPhone] = useState("");
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");

  const submit = async (event) => {
    event.preventDefault();
    if (submitting) return;
    const normalizedOrderNo = orderNo.trim();
    const normalizedEmail = email.trim();
    const normalizedPhone = phone.trim();
    if (!normalizedOrderNo || !normalizedEmail || !normalizedPhone) {
      setError("주문번호, 이메일, 전화번호를 모두 입력해 주세요.");
      return;
    }

    setSubmitting(true);
    setError("");
    try {
      const result = await paymentApi.recoverGuestOrderAccess(normalizedOrderNo, {
        email: normalizedEmail,
        phone: normalizedPhone,
      });
      const data = result?.data;
      if (!data?.orderAccessToken || !data?.orderNo) {
        throw new Error("주문 정보를 확인할 수 없습니다.");
      }
      sessionStorage.setItem(`ticket-order-token:${data.orderNo}`, data.orderAccessToken);
      navigate(`/guest/orders/${encodeURIComponent(data.orderNo)}`);
    } catch (requestError) {
      setError(requestError.message || "주문 정보를 확인할 수 없습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="min-h-screen bg-surface-container-low text-on-surface">
      <TopNav active="mypage" />
      <main className="mx-auto max-w-[520px] px-lg pb-xxl pt-[96px]">
        <div className="mb-lg">
          <p className="text-caption text-primary">GUEST ORDER</p>
          <h1 className="font-display-lg text-[30px]">비회원 예매 조회</h1>
        </div>
        <form onSubmit={submit} className="space-y-md rounded-2xl border border-hairline bg-white p-xl">
          <label className="block">
            <span className="text-caption text-ink-muted">주문번호</span>
            <input
              required
              value={orderNo}
              onChange={(event) => setOrderNo(event.target.value)}
              className="mt-xs h-11 w-full rounded-lg border border-hairline px-md outline-none focus:border-primary-focus"
            />
          </label>
          <label className="block">
            <span className="text-caption text-ink-muted">구매 이메일</span>
            <input
              required
              type="email"
              value={email}
              onChange={(event) => setEmail(event.target.value)}
              className="mt-xs h-11 w-full rounded-lg border border-hairline px-md outline-none focus:border-primary-focus"
            />
          </label>
          <label className="block">
            <span className="text-caption text-ink-muted">구매 전화번호</span>
            <input
              required
              value={phone}
              onChange={(event) => setPhone(event.target.value)}
              placeholder="010-1234-5678"
              className="mt-xs h-11 w-full rounded-lg border border-hairline px-md outline-none focus:border-primary-focus"
            />
          </label>
          {error && <p className="rounded-lg bg-error/10 p-sm text-caption text-error">{error}</p>}
          <button
            disabled={submitting}
            className="h-11 w-full rounded-full bg-primary px-lg text-white font-body-strong disabled:opacity-50"
          >
            {submitting ? "확인 중" : "예매 조회"}
          </button>
          <Link to="/" className="block text-center text-caption text-ink-muted underline">
            행사 목록으로 돌아가기
          </Link>
        </form>
      </main>
    </div>
  );
}
