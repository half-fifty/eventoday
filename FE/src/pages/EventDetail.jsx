import { useEffect, useState } from "react";
import { ANONYMOUS, loadTossPayments } from "@tosspayments/tosspayments-sdk";
import { Link, useParams } from "react-router-dom";
import { eventApi } from "../api/eventApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import { listContents } from "../api/contentApi.js";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import TopNav from "../components/TopNav.jsx";
import FileDownloadLink from "../components/FileDownloadLink.jsx";
import useAuth from "../hooks/useAuth.js";

const formatDateTime = (value) => value
  ? new Date(value).toLocaleString("ko-KR", { dateStyle: "long", timeStyle: "short" })
  : "미정";

const emptyBuyer = { name: "", email: "", phone: "" };

export default function EventDetail() {
  const { eventId } = useParams();
  const { isAuthenticated } = useAuth();
  const [event, setEvent] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [purchaseOpen, setPurchaseOpen] = useState(false);
  const [quantity, setQuantity] = useState(1);
  const [buyer, setBuyer] = useState(emptyBuyer);
  const [purchasing, setPurchasing] = useState(false);
  const [purchaseError, setPurchaseError] = useState("");
  const [issuedCodes, setIssuedCodes] = useState([]);
  // 공지·자료 (WBS-199): 권한에 따라 BE가 필터링해 내려준다
  const [contents, setContents] = useState([]);
  const [expandedContentId, setExpandedContentId] = useState(null);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    setEvent(null);
    eventApi.detail(eventId)
      .then((result) => !cancelled && setEvent(result?.data || null))
      .catch((requestError) => !cancelled && setError(requestError.message || "행사를 불러오지 못했습니다."))
      .finally(() => !cancelled && setLoading(false));
    return () => { cancelled = true; };
  }, [eventId]);

  // CONTENT-API-001: 행사 공지·자료 로드 (실패해도 페이지 표시에는 영향 없도록 조용히 처리)
  useEffect(() => {
    let cancelled = false;
    setContents([]);
    listContents(eventId)
      .then((data) => {
        if (cancelled) return;
        const list = Array.isArray(data) ? data : [];
        // 고정 공지 우선, 이후 게시일 최신순 정렬
        list.sort((a, b) => (b.pinned - a.pinned) || new Date(b.publishedAt || 0) - new Date(a.publishedAt || 0));
        setContents(list);
      })
      .catch(() => { if (!cancelled) setContents([]); });
    return () => { cancelled = true; };
  }, [eventId]);

  const submitTicketOrder = async (submitEvent) => {
    submitEvent.preventDefault();
    setPurchasing(true);
    setPurchaseError("");
    try {
      const payload = {
        quantity: Number(quantity),
        ...(isAuthenticated ? {} : { buyer }),
      };
      const result = await eventApi.createTicketOrder(eventId, payload);
      const order = result?.data;
      if (!order) throw new Error("티켓 주문 정보를 받지 못했습니다.");

      if (!order.paymentRequired) {
        setIssuedCodes(order.exchangeCodes || []);
        return;
      }

      const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
      if (!clientKey) throw new Error("VITE_TOSS_CLIENT_KEY가 설정되지 않아 결제창을 열 수 없습니다.");
      if (order.orderAccessToken) {
        sessionStorage.setItem(`ticket-order-token:${order.orderNo}`, order.orderAccessToken);
      }
      const tossPayments = await loadTossPayments(clientKey);
      const payment = tossPayments.payment({ customerKey: ANONYMOUS });
      await payment.requestPayment({
        method: "CARD",
        amount: { currency: "KRW", value: Number(order.totalAmount) },
        orderId: order.orderNo,
        orderName: `${event.name} 티켓`,
        successUrl: `${window.location.origin}/tickets/payment/success?eventId=${eventId}`,
        failUrl: `${window.location.origin}/tickets/payment/fail?eventId=${eventId}`,
        card: { useEscrow: false, flowMode: "DEFAULT", useCardPoint: false, useAppCardOnly: false },
      });
    } catch (requestError) {
      setPurchaseError(requestError.message || "티켓 구매를 시작하지 못했습니다.");
    } finally {
      setPurchasing(false);
    }
  };

  const closePurchase = () => {
    setPurchaseOpen(false);
    setPurchaseError("");
    setIssuedCodes([]);
  };

  const now = Date.now();
  const salesNotStarted = event?.ticketSalesStartAt && new Date(event.ticketSalesStartAt).getTime() > now;
  const salesEnded = event?.ticketSalesEndAt && new Date(event.ticketSalesEndAt).getTime() < now;
  const ticketButtonLabel = salesNotStarted
    ? `${formatDateTime(event.ticketSalesStartAt)} 판매 시작`
    : salesEnded ? "티켓 판매 종료" : "티켓 구매하기";

  return (
    <div className="bg-surface min-h-screen text-on-surface">
      <TopNav active="events" />
      <main className="pt-[76px] max-w-[1000px] mx-auto px-lg pb-xxl">
        {loading && <p className="py-xxl text-center text-ink-muted">행사 정보를 불러오는 중입니다.</p>}
        {error && <div className="bg-error/10 border border-error/20 text-error rounded-xl p-lg">{error}<Link to="/" className="underline ml-sm">행사 목록으로</Link></div>}
        {event && <>
          <section className="overflow-hidden rounded-2xl text-white bg-gradient-to-br from-primary-focus to-secondary mb-xl grid md:grid-cols-[280px_1fr]">
            {event.representativeFileId && <div className="bg-black/15 p-md"><img src={fileDownloadUrl(event.representativeFileId)} alt={`${event.name} 포스터`} className="w-full aspect-[3/4] object-cover rounded-xl shadow-xl" /></div>}
            <div className="p-xl md:p-xxl flex flex-col justify-center"><p className="text-caption text-white/70 mb-sm">{event.eventType}</p><h1 className="font-display-lg text-[32px] md:text-[42px] mb-sm">{event.name}</h1><p className="text-white/80">{event.shortDescription}</p></div>
          </section>
          <div className="grid md:grid-cols-[1fr_320px] gap-xl">
            <section className="space-y-xl">
              <div><h2 className="font-display-md text-[22px] mb-md">행사 소개</h2><p className="whitespace-pre-wrap leading-7">{event.description}</p></div>
              <div><h2 className="font-display-md text-[22px] mb-md">운영 기능</h2><div className="flex flex-wrap gap-sm">{event.boothRecruitmentEnabled && <span className="px-md py-xs bg-primary/10 text-primary rounded-full text-caption">부스 모집</span>}{event.venueMapEnabled && <span className="px-md py-xs bg-primary/10 text-primary rounded-full text-caption">평면도</span>}{event.boothReservationEnabled && <span className="px-md py-xs bg-primary/10 text-primary rounded-full text-caption">부스 예약</span>}</div></div>

              {/* 공지·자료 (WBS-199): 제목 클릭 시 내용 펼침, 첨부는 다운로드 링크 */}
              {contents.length > 0 && (
                <div>
                  <h2 className="font-display-md text-[22px] mb-md">공지 · 자료</h2>
                  <div className="bg-white border border-hairline rounded-2xl divide-y divide-divider-soft overflow-hidden">
                    {contents.map((content) => (
                      <div key={content.contentId}>
                        <button
                          onClick={() => setExpandedContentId(expandedContentId === content.contentId ? null : content.contentId)}
                          className="w-full flex items-center gap-sm p-lg text-left hover:bg-surface-pearl/50 transition-colors"
                        >
                          <Icon
                            name={content.contentType === "NOTICE" ? "campaign" : "folder"}
                            className="text-[18px] text-ink-muted flex-shrink-0"
                          />
                          <div className="flex-1 min-w-0">
                            <p className="font-body-strong text-[14px] truncate">
                              {content.pinned && <Icon name="push_pin" className="text-[13px] text-primary mr-1" />}
                              {content.title}
                            </p>
                            <p className="text-caption text-ink-muted">
                              {content.contentType === "NOTICE" ? "공지" : "자료"}
                              {content.version ? ` · v${content.version}` : ""}
                              {content.publishedAt ? ` · ${new Date(content.publishedAt).toLocaleDateString("ko-KR")}` : ""}
                            </p>
                          </div>
                          <Icon name={expandedContentId === content.contentId ? "expand_less" : "expand_more"} className="text-ink-muted text-[18px]" />
                        </button>
                        {expandedContentId === content.contentId && (
                          <div className="px-lg pb-lg space-y-sm">
                            {content.content && <p className="text-caption whitespace-pre-line bg-surface-pearl rounded-lg p-md">{content.content}</p>}
                            {content.fileId && (
                              <FileDownloadLink fileId={content.fileId} fileName="첨부파일 다운로드" />
                            )}
                          </div>
                        )}
                      </div>
                    ))}
                  </div>
                </div>
              )}
            </section>
            <aside className="bg-white border border-hairline rounded-2xl p-lg h-fit space-y-md">
              <p className="flex gap-sm"><Icon name="calendar_month" /><span>{formatDateTime(event.startAt)}<br />~ {formatDateTime(event.endAt)}</span></p>
              <p className="flex gap-sm"><Icon name="location_on" /><span>{event.venueName}<br /><span className="text-caption text-ink-muted">{event.address}</span></span></p>
              <div className="border-t border-hairline pt-md"><p className="text-caption text-ink-muted">입장 가격</p><p className="font-display-md text-[22px]">{Number(event.ticketPrice) === 0 ? "무료" : `${Number(event.ticketPrice).toLocaleString("ko-KR")}원`}</p></div>
              {(event.contactEmail || event.contactPhone) && <div className="border-t border-hairline pt-md space-y-sm"><p className="text-caption text-ink-muted">행사 문의</p>{event.contactEmail && <a href={`mailto:${event.contactEmail}`} className="flex gap-sm items-center text-body hover:text-primary"><Icon name="mail" />{event.contactEmail}</a>}{event.contactPhone && <a href={`tel:${event.contactPhone}`} className="flex gap-sm items-center text-body hover:text-primary"><Icon name="call" />{event.contactPhone}</a>}</div>}
              <button disabled={salesNotStarted || salesEnded} onClick={() => setPurchaseOpen(true)} className="w-full py-sm bg-primary text-white rounded-full font-body-strong disabled:bg-surface-container-highest disabled:text-ink-muted disabled:cursor-not-allowed">{ticketButtonLabel}</button>
            </aside>
          </div>
        </>}
      </main>
      <Footer />
      {purchaseOpen && event && <div className="fixed inset-0 z-50 bg-black/50 grid place-items-center p-lg" onMouseDown={(e) => e.target === e.currentTarget && closePurchase()}>
        <section role="dialog" aria-modal="true" aria-labelledby="ticket-title" className="w-full max-w-md bg-white rounded-2xl p-xl shadow-2xl">
          <div className="flex items-start justify-between gap-md mb-lg"><div><p className="text-caption text-primary">TICKET</p><h2 id="ticket-title" className="font-display-md text-[24px]">{issuedCodes.length ? "예매 완료" : "티켓 구매"}</h2></div><button type="button" onClick={closePurchase} aria-label="닫기"><Icon name="close" /></button></div>
          {issuedCodes.length ? <div className="space-y-md"><p>무료 티켓이 발급되었습니다.</p>{issuedCodes.map((item, index) => <div key={item.exchangeCode || index} className="rounded-xl bg-surface-container p-md"><p className="text-caption text-ink-muted">입장 코드 {index + 1}</p><p className="font-mono font-bold text-lg break-all">{item.exchangeCode || item.code}</p></div>)}<button type="button" onClick={closePurchase} className="w-full py-sm bg-primary text-white rounded-full">확인</button></div> : <form onSubmit={submitTicketOrder} className="space-y-md">
            <div className="rounded-xl bg-surface-container p-md"><p className="font-body-strong">{event.name}</p><p className="text-caption text-ink-muted">1매 {Number(event.ticketPrice) === 0 ? "무료" : `${Number(event.ticketPrice).toLocaleString("ko-KR")}원`}</p></div>
            <label className="block">수량<input required min="1" max={event.ticketPurchaseLimit || 1} type="number" value={quantity} onChange={(e) => setQuantity(e.target.value)} className="mt-xs w-full h-11 border border-hairline rounded-lg px-md" /></label>
            {!isAuthenticated && <div className="space-y-md border-t border-hairline pt-md"><p className="text-caption text-ink-muted">비회원 구매 정보</p><label className="block">이름<input required value={buyer.name} onChange={(e) => setBuyer({ ...buyer, name: e.target.value })} className="mt-xs w-full h-11 border border-hairline rounded-lg px-md" /></label><label className="block">이메일<input required type="email" value={buyer.email} onChange={(e) => setBuyer({ ...buyer, email: e.target.value })} className="mt-xs w-full h-11 border border-hairline rounded-lg px-md" /></label><label className="block">전화번호<input required placeholder="010-1234-5678" value={buyer.phone} onChange={(e) => setBuyer({ ...buyer, phone: e.target.value })} className="mt-xs w-full h-11 border border-hairline rounded-lg px-md" /></label></div>}
            <div className="flex justify-between border-t border-hairline pt-md"><span>결제 금액</span><strong>{Number(event.ticketPrice) === 0 ? "무료" : `${(Number(event.ticketPrice) * Number(quantity || 0)).toLocaleString("ko-KR")}원`}</strong></div>
            {purchaseError && <p className="text-caption text-error bg-error/10 rounded-lg p-sm">{purchaseError}</p>}
            <button disabled={purchasing} className="w-full py-sm bg-primary text-white rounded-full disabled:opacity-50">{purchasing ? "주문 생성 중..." : Number(event.ticketPrice) === 0 ? "무료 티켓 받기" : "결제하기"}</button>
          </form>}
        </section>
      </div>}
    </div>
  );
}
