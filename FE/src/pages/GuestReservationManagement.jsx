import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import { admissionApi } from "../api/admissionApi.js";
import { exchangeCodeApi } from "../api/exchangeCodeApi.js";
import { paymentApi } from "../api/paymentApi.js";
import TopNav from "../components/TopNav.jsx";
import { REFUND_BANK_GROUPS, REFUND_BANKS } from "../constants/refundBanks.js";
import { shareOrDownloadAdmissionTicketImage } from "../utils/admissionTicketImage.js";

const formatDateTime = (value) =>
  value ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-";
const formatMoney = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;

const statusLabel = {
  PENDING: "대기",
  PENDING_PAYMENT: "결제 대기",
  WAITING_FOR_DEPOSIT: "입금 대기",
  PAID: "결제 완료",
  CONFIRMED: "확정",
  REFUNDED: "환불 완료",
  ISSUED: "사용 가능",
  REDEEMED: "사용 완료",
  USED: "입장 완료",
  CANCELLED: "취소",
  EXPIRED: "만료",
};

export default function GuestReservationManagement() {
  const { orderNo } = useParams();
  const orderAccessToken = useMemo(
    () => sessionStorage.getItem(`ticket-order-token:${orderNo}`),
    [orderNo]
  );
  const [order, setOrder] = useState(null);
  const [payment, setPayment] = useState(null);
  const [exchangeCodes, setExchangeCodes] = useState([]);
  const [admissionTickets, setAdmissionTickets] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [redeemingId, setRedeemingId] = useState(null);
  const [actionError, setActionError] = useState("");
  const [refundReason, setRefundReason] = useState("");
  const [refundAccount, setRefundAccount] = useState({ bank: "", accountNumber: "", holderName: "" });
  const [refunding, setRefunding] = useState(false);
  const [qrUrls, setQrUrls] = useState({});
  const [qrErrors, setQrErrors] = useState({});
  const [savingTicketImageId, setSavingTicketImageId] = useState(null);
  const [ticketImageMessages, setTicketImageMessages] = useState({});
  const [ticketImageErrors, setTicketImageErrors] = useState({});
  const loadGenerationRef = useRef(0);

  const loadAll = useCallback(async () => {
    const generation = loadGenerationRef.current + 1;
    loadGenerationRef.current = generation;
    const isLatestLoad = () => generation === loadGenerationRef.current;

    if (!orderAccessToken) {
      if (!isLatestLoad()) return;
      setOrder(null);
      setPayment(null);
      setExchangeCodes([]);
      setAdmissionTickets([]);
      setError("비회원 예매 조회가 필요합니다.");
      setLoading(false);
      return;
    }
    setLoading(true);
    setError("");
    setActionError("");
    setOrder(null);
    setPayment(null);
    setExchangeCodes([]);
    setAdmissionTickets([]);
    try {
      const orderResult = await paymentApi.getTicketOrder(orderNo, orderAccessToken);
      if (!isLatestLoad()) return;
      const nextOrder = orderResult?.data || null;
      setOrder(nextOrder);

      const [codesResult, ticketsResult, paymentResult] = await Promise.all([
        exchangeCodeApi.getGuestOrderExchangeCodes(orderNo, orderAccessToken),
        admissionApi.getGuestOrderAdmissionTickets(orderNo, orderAccessToken),
        nextOrder?.paymentId
          ? paymentApi.getPayment(nextOrder.paymentId, orderAccessToken).catch(() => null)
          : Promise.resolve(null),
      ]);
      if (!isLatestLoad()) return;
      setExchangeCodes(codesResult?.data || []);
      setAdmissionTickets(ticketsResult?.data || []);
      setPayment(paymentResult?.data || null);
    } catch (requestError) {
      if (!isLatestLoad()) return;
      setError(requestError.message || "예매 정보를 불러오지 못했습니다.");
    } finally {
      if (isLatestLoad()) setLoading(false);
    }
  }, [orderAccessToken, orderNo]);

  useEffect(() => {
    loadAll();
  }, [loadAll]);

  useEffect(() => {
    const objectUrls = {};
    let cancelled = false;

    admissionTickets
      .filter((ticket) => ticket.status === "ISSUED")
      .forEach((ticket) => {
        admissionApi.getGuestOrderAdmissionTicketQr(orderNo, ticket.admissionTicketId, orderAccessToken)
          .then((blob) => {
            if (cancelled) return;
            const url = URL.createObjectURL(blob);
            objectUrls[ticket.admissionTicketId] = url;
            setQrUrls((current) => ({ ...current, [ticket.admissionTicketId]: url }));
          })
          .catch((requestError) => {
            if (!cancelled) {
              setQrErrors((current) => ({
                ...current,
                [ticket.admissionTicketId]: requestError.message || "QR을 불러오지 못했습니다.",
              }));
            }
          });
      });

    return () => {
      cancelled = true;
      Object.values(objectUrls).forEach((url) => URL.revokeObjectURL(url));
      setQrUrls({});
      setQrErrors({});
    };
  }, [admissionTickets, orderAccessToken, orderNo]);

  const redeem = async (exchangeCodeId) => {
    if (redeemingId) return;
    setRedeemingId(exchangeCodeId);
    setActionError("");
    try {
      await exchangeCodeApi.redeemGuestOrderExchangeCode(orderNo, exchangeCodeId, orderAccessToken);
      await loadAll();
    } catch (requestError) {
      setActionError(requestError.message || "입장권 발급에 실패했습니다.");
    } finally {
      setRedeemingId(null);
    }
  };

  const saveTicketImage = async (ticket) => {
    const qrImageUrl = qrUrls[ticket.admissionTicketId];
    if (!qrImageUrl || savingTicketImageId) return;
    setSavingTicketImageId(ticket.admissionTicketId);
    setTicketImageMessages((current) => ({ ...current, [ticket.admissionTicketId]: "" }));
    setTicketImageErrors((current) => ({ ...current, [ticket.admissionTicketId]: "" }));
    try {
      const result = await shareOrDownloadAdmissionTicketImage({
        ticket,
        qrImageUrl,
      });
      if (result.action === "downloaded" || result.action === "shared") {
        setTicketImageMessages((current) => ({
          ...current,
          [ticket.admissionTicketId]: "입장권 이미지가 저장되었습니다.",
        }));
      }
    } catch {
      setTicketImageErrors((current) => ({
        ...current,
        [ticket.admissionTicketId]: "입장권 이미지를 저장하지 못했습니다.",
      }));
    } finally {
      setSavingTicketImageId(null);
    }
  };

  const requestRefund = async (event) => {
    event.preventDefault();
    if (!payment?.paymentId || refunding) return;
    const reason = refundReason.trim();
    if (!reason) {
      setActionError("환불 사유를 입력해 주세요.");
      return;
    }
    const isVirtualAccountPayment =
      payment?.method === "VIRTUAL_ACCOUNT" || payment?.method === "가상계좌" || Boolean(payment?.virtualAccount);
    if (isVirtualAccountPayment && (!refundAccount.bank.trim() || !refundAccount.accountNumber.trim() || !refundAccount.holderName.trim())) {
      setActionError("가상계좌 환불을 받을 계좌 정보를 입력해 주세요.");
      return;
    }
    if (!window.confirm("해당 결제 전체를 환불 신청하시겠습니까?")) return;

    setRefunding(true);
    setActionError("");
    try {
      await paymentApi.requestRefund(payment.paymentId, {
        reason,
        ...(isVirtualAccountPayment ? {
          refundReceiveAccount: {
            bank: refundAccount.bank.trim(),
            accountNumber: refundAccount.accountNumber.trim(),
            holderName: refundAccount.holderName.trim(),
          },
        } : {}),
      }, orderAccessToken);
      setRefundReason("");
      setRefundAccount({ bank: "", accountNumber: "", holderName: "" });
      await loadAll();
    } catch (requestError) {
      setActionError(requestError.message || "환불 신청에 실패했습니다.");
    } finally {
      setRefunding(false);
    }
  };

  const canRefund = payment?.paymentStatus === "PAID" && payment?.ticketOrderStatus === "CONFIRMED";

  return (
    <div className="min-h-screen bg-surface-container-low text-on-surface">
      <TopNav active="mypage" />
      <main className="mx-auto max-w-[960px] px-lg pb-xxl pt-[96px]">
        <div className="mb-lg flex items-center justify-between gap-md">
          <div>
            <p className="text-caption text-primary">GUEST RESERVATION</p>
            <h1 className="font-display-lg text-[30px]">비회원 예매 관리</h1>
          </div>
          <Link to="/guest/orders" className="rounded-full border border-hairline px-lg py-sm text-caption font-body-strong">
            다른 예매 조회
          </Link>
        </div>

        {loading && <Panel>예매 정보를 불러오는 중입니다.</Panel>}
        {error && (
          <Panel>
            <p className="text-error">{error}</p>
            <Link to="/guest/orders" className="mt-sm inline-flex rounded-full bg-primary px-lg py-sm text-white">
              비회원 예매 조회
            </Link>
          </Panel>
        )}

        {!loading && !error && order && (
          <div className="space-y-lg">
            {actionError && <div className="rounded-xl bg-error/10 p-md text-caption text-error">{actionError}</div>}

            <section className="rounded-2xl border border-hairline bg-white p-xl">
              <div className="mb-md flex flex-wrap items-start justify-between gap-md">
                <div>
                  <h2 className="font-display-md text-[24px]">{order.eventName}</h2>
                  <p className="text-caption text-ink-muted">주문번호 {order.orderNo}</p>
                </div>
                <span className="rounded-full bg-primary/10 px-md py-1 text-caption font-body-strong text-primary">
                  {statusLabel[order.ticketOrderStatus] || order.ticketOrderStatus}
                </span>
              </div>
              <div className="grid gap-md sm:grid-cols-2 lg:grid-cols-4">
                <Info label="수량" value={`${order.quantity}매`} />
                <Info label="단가" value={formatMoney(order.unitPrice)} />
                <Info label="총 금액" value={formatMoney(order.totalAmount)} />
                <Info label="주문 확정" value={formatDateTime(order.confirmedAt)} />
              </div>
            </section>

            <section className="rounded-2xl border border-hairline bg-white p-xl">
              <h2 className="mb-md font-display-md text-[22px]">결제 및 환불</h2>
              {payment ? (
                <div className="space-y-md">
                  <div className="grid gap-md sm:grid-cols-2 lg:grid-cols-4">
                    <Info label="결제 상태" value={statusLabel[payment.paymentStatus] || payment.paymentStatus} />
                    <Info label="결제 수단" value={payment.method || "-"} />
                    <Info label="승인 시각" value={formatDateTime(payment.approvedAt)} />
                  </div>
                  {payment.virtualAccount && (
                    <div className="rounded-xl border border-primary/20 bg-primary/5 p-md">
                      <p className="text-caption text-ink-muted">입금 계좌</p>
                      <p className="mt-xs font-body-strong">{payment.virtualAccount.bankCode || "은행"} {payment.virtualAccount.accountNumber}</p>
                      <p className="text-caption text-ink-muted">
                        예금주 {payment.virtualAccount.customerName || "-"} · 입금액 {formatMoney(payment.virtualAccount.amount || payment.amount)}
                      </p>
                      <p className="text-caption text-ink-muted">입금기한 {formatDateTime(payment.virtualAccount.dueAt)}</p>
                    </div>
                  )}
                  {canRefund ? (
                    <form onSubmit={requestRefund} className="grid gap-sm">
                      <input
                        value={refundReason}
                        onChange={(event) => setRefundReason(event.target.value)}
                        placeholder="환불 사유"
                        className="h-11 min-w-0 flex-1 rounded-lg border border-hairline px-md outline-none focus:border-primary-focus"
                      />
                      {(payment?.method === "VIRTUAL_ACCOUNT" || payment?.method === "가상계좌" || Boolean(payment?.virtualAccount)) && (
                        <div className="grid gap-sm sm:grid-cols-3">
                          <select
                            required
                            value={refundAccount.bank}
                            onChange={(event) => setRefundAccount((current) => ({ ...current, bank: event.target.value }))}
                            className="h-11 rounded-lg border border-hairline px-md outline-none focus:border-primary-focus"
                          >
                            <option value="">은행을 선택해주세요</option>
                            {REFUND_BANK_GROUPS.map((group) => (
                              <optgroup key={group} label={group}>
                                {REFUND_BANKS.filter((bank) => bank.group === group).map((bank) => (
                                  <option key={bank.code} value={bank.code}>
                                    {bank.name}
                                  </option>
                                ))}
                              </optgroup>
                            ))}
                          </select>
                          <input
                            value={refundAccount.accountNumber}
                            onChange={(event) => setRefundAccount((current) => ({ ...current, accountNumber: event.target.value }))}
                            placeholder="계좌번호"
                            className="h-11 rounded-lg border border-hairline px-md outline-none focus:border-primary-focus"
                          />
                          <input
                            value={refundAccount.holderName}
                            onChange={(event) => setRefundAccount((current) => ({ ...current, holderName: event.target.value }))}
                            placeholder="예금주"
                            className="h-11 rounded-lg border border-hairline px-md outline-none focus:border-primary-focus"
                          />
                        </div>
                      )}
                      <button disabled={refunding} className="h-11 rounded-lg bg-primary px-lg text-white disabled:opacity-50">
                        {refunding ? "처리 중" : "환불 신청"}
                      </button>
                    </form>
                  ) : (
                    <div className="space-y-md">
                      <p className="text-caption text-ink-muted">현재 상태에서는 환불 신청 버튼이 표시되지 않습니다.</p>
                    </div>
                  )}
                </div>
              ) : (
                <p className="text-caption text-ink-muted">무료 티켓이거나 결제 정보가 아직 없습니다.</p>
              )}
            </section>

            <section className="rounded-2xl border border-hairline bg-white p-xl">
              <h2 className="mb-md font-display-md text-[22px]">교환 코드</h2>
              {exchangeCodes.length === 0 ? (
                <p className="text-caption text-ink-muted">발급된 교환 코드가 없습니다.</p>
              ) : (
                <div className="space-y-sm">
                  {exchangeCodes.map((code) => (
                    <div key={code.exchangeCodeId} className="flex flex-wrap items-center gap-md rounded-xl bg-surface-container p-md">
                      <div className="min-w-0 flex-1">
                        <p className="font-mono font-body-strong break-all">{code.code}</p>
                        <p className="text-caption text-ink-muted">
                          {code.eventName} · {statusLabel[code.status] || code.status} · 만료 {formatDateTime(code.expiresAt)}
                        </p>
                      </div>
                      {code.status === "ISSUED" && (
                        <button
                          type="button"
                          onClick={() => redeem(code.exchangeCodeId)}
                          disabled={redeemingId === code.exchangeCodeId}
                          className="rounded-lg bg-black px-lg py-sm text-caption font-body-strong text-white disabled:opacity-50"
                        >
                          {redeemingId === code.exchangeCodeId ? "발급 중" : "입장권 발급"}
                        </button>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>

            <section className="rounded-2xl border border-hairline bg-white p-xl">
              <h2 className="mb-md font-display-md text-[22px]">입장 티켓 / QR</h2>
              {admissionTickets.length === 0 ? (
                <p className="text-caption text-ink-muted">발급된 입장 티켓이 없습니다.</p>
              ) : (
                <div className="grid gap-md md:grid-cols-2">
                  {admissionTickets.map((ticket) => (
                    <div key={ticket.admissionTicketId} className="rounded-xl border border-hairline p-md">
                      <div className="mb-sm flex items-center justify-between gap-sm">
                        <div>
                          <p className="font-body-strong">{ticket.eventName}</p>
                        </div>
                        <span className="rounded-full bg-primary/10 px-sm py-1 text-[11px] font-bold text-primary">
                          {statusLabel[ticket.status] || ticket.status}
                        </span>
                      </div>
                      {ticket.status === "ISSUED" && qrUrls[ticket.admissionTicketId] && (
                        <>
                          <img
                            src={qrUrls[ticket.admissionTicketId]}
                            alt={`${ticket.eventName} 입장 QR`}
                            className="mx-auto h-48 w-48 rounded-xl border border-hairline bg-white p-sm"
                          />
                          <button
                            type="button"
                            onClick={() => saveTicketImage(ticket)}
                            disabled={savingTicketImageId === ticket.admissionTicketId}
                            className="mx-auto mt-sm flex w-fit rounded-full border border-primary px-lg py-sm text-caption font-body-strong text-primary disabled:opacity-50"
                          >
                            {savingTicketImageId === ticket.admissionTicketId ? "저장 중..." : "이미지 저장"}
                          </button>
                          {ticketImageMessages[ticket.admissionTicketId] && (
                            <p className="mt-xs text-center text-caption text-primary">
                              {ticketImageMessages[ticket.admissionTicketId]}
                            </p>
                          )}
                          {ticketImageErrors[ticket.admissionTicketId] && (
                            <p className="mt-xs text-center text-caption text-error">
                              {ticketImageErrors[ticket.admissionTicketId]}
                            </p>
                          )}
                        </>
                      )}
                      {qrErrors[ticket.admissionTicketId] && (
                        <p className="text-caption text-error">{qrErrors[ticket.admissionTicketId]}</p>
                      )}
                    </div>
                  ))}
                </div>
              )}
            </section>
          </div>
        )}
      </main>
    </div>
  );
}

function Panel({ children }) {
  return (
    <div className="rounded-2xl border border-hairline bg-white p-xl text-center text-ink-muted">
      {children}
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
