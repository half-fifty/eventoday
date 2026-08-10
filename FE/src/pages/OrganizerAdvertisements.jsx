import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { advertisementApi } from "../api/advertisementApi.js";
import { eventApi } from "../api/eventApi.js";
import FileUploadField from "../components/FileUploadField.jsx";
import Icon from "../components/Icon.jsx";
import { fileDownloadUrl } from "../api/fileApi.js";
import { ANONYMOUS, loadTossPayments } from "@tosspayments/tosspayments-sdk";
import TopNav from "../components/TopNav.jsx";

const emptyForm = { eventId: "", bannerFileId: null, adText: "", startAt: "", endAt: "" };
const statusLabel = {
  PAYMENT_PENDING: "결제 대기", PAID: "결제 완료·심사 대기", REVIEW_PENDING: "심사 대기",
  SCHEDULED: "노출 예정", ACTIVE: "노출 중", REJECTED: "반려", CANCELLED: "취소", ENDED: "종료",
};
const toOffset = (value) => value ? new Date(value).toISOString() : null;
const toInput = (value) => {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const localDate = new Date(date.getTime() - date.getTimezoneOffset() * 60_000);
  return localDate.toISOString().slice(0, 16);
};
const formatPrice = (value) => value == null ? "-" : Number(value).toLocaleString();

export default function OrganizerAdvertisements() {
  const query = new URLSearchParams(window.location.search);
  const requestedEventId = query.get("eventId") || "";
  const [organizationId, setOrganizationId] = useState(query.get("organizationId") || localStorage.getItem("organizationId") || "");
  const [organizations, setOrganizations] = useState([]);
  const [events, setEvents] = useState([]);
  const [ads, setAds] = useState([]);
  const [pricing, setPricing] = useState({ eventAdPrice: null, boothAdPrice: null, paymentExpiresInMinutes: null });
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const field = "w-full h-11 border border-hairline rounded-lg px-md bg-white outline-none focus:border-primary";
  const editingAd = ads.find((ad) => ad.id === editingId);
  const creativeOnly = ["SCHEDULED", "ACTIVE"].includes(editingAd?.status);

  const loadAds = async (id = organizationId) => {
    if (!id) { setAds([]); return; }
    const result = await advertisementApi.organizationList(id, { size: 100, sort: "createdAt,desc" });
    setAds((result?.data?.content || []).filter((advertisement) => advertisement.eventId));
  };

  useEffect(() => {
    advertisementApi.pricing().then((result) => {
      if (result?.data) setPricing(result.data);
    }).catch((e) => setError(e.message || "광고 가격 정보를 불러오지 못했습니다."));
    eventApi.managedOrganizations().then((result) => {
      const list = result?.data || [];
      setOrganizations(list);
      const selected = list.some((item) => String(item.id) === String(organizationId)) ? organizationId : String(list[0]?.id || "");
      setOrganizationId(selected);
    }).catch((e) => setError(e.message || "운영 조직을 불러오지 못했습니다."));
  }, []);

  useEffect(() => {
    if (!organizationId) { setLoading(false); return; }
    localStorage.setItem("organizationId", organizationId);
    setLoading(true); setError(""); setMessage(""); setForm(emptyForm); setEditingId(null);
    Promise.all([eventApi.organizationList(organizationId, { size: 100 }), loadAds(organizationId)])
      .then(([eventResult]) => {
        const list = eventResult?.data?.content || [];
        setEvents(list);
        const selectedEventId = list.some((item) => String(item.id) === requestedEventId)
          ? requestedEventId
          : String(list[0]?.id || "");
        setForm((previous) => ({ ...previous, eventId: selectedEventId }));
      }).catch((e) => setError(e.message || "광고 정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [organizationId]);

  const eventNames = useMemo(() => Object.fromEntries(events.map((event) => [event.id, event.name])), [events]);
  const change = (key, value) => setForm((previous) => ({ ...previous, [key]: value }));
  const reset = () => { setEditingId(null); setForm({ ...emptyForm, eventId: String(events[0]?.id || "") }); };

  const submit = async (event) => {
    event.preventDefault();
    if (!editingId && !form.eventId) { setError("광고할 행사를 선택해 주세요."); return; }
    if (!form.bannerFileId) { setError("광고 배너 이미지를 업로드해 주세요."); return; }
    if (new Date(form.startAt) >= new Date(form.endAt)) { setError("광고 종료일은 시작일 이후여야 합니다."); return; }
    setSaving(true); setError(""); setMessage("");
    const payload = { applicantOrganizationId: Number(organizationId), bannerFileId: form.bannerFileId,
      adText: form.adText, startAt: toOffset(form.startAt), endAt: toOffset(form.endAt) };
    try {
      const result = creativeOnly
        ? await advertisementApi.updateCreative(editingId, { bannerFileId: form.bannerFileId, adText: form.adText })
        : editingId ? await advertisementApi.update(editingId, payload)
        : await advertisementApi.createEvent(form.eventId, payload);
      if (result?.data?.paymentOrder) {
        setMessage(`광고 신청이 저장되었습니다. 결제 금액은 ${Number(result.data.paymentOrder.totalAmount).toLocaleString()}원이며, 아래 신청 내역에서 결제할 수 있습니다.`);
      } else {
        setMessage(editingId ? "광고 정보를 수정했습니다." : "광고 신청을 저장했습니다.");
      }
      await loadAds(); reset();
    } catch (e) { setError(e.message || "광고를 저장하지 못했습니다."); }
    finally { setSaving(false); }
  };

  const edit = (ad) => {
    setEditingId(ad.id);
    setMessage("");
    setForm({ eventId: String(ad.eventId || ""),
      bannerFileId: ad.bannerFileId, adText: ad.adText || "", startAt: toInput(ad.startAt), endAt: toInput(ad.endAt) });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };
  const cancel = async (id) => {
    if (!window.confirm("이 광고 신청을 취소할까요?")) return;
    setMessage("");
    try { await advertisementApi.cancel(id); await loadAds(); setMessage("광고 신청을 취소했습니다."); }
    catch (e) { setError(e.message || "광고를 취소하지 못했습니다."); }
  };
  const pay = async (ad) => {
    const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
    if (!clientKey) { setError("VITE_TOSS_CLIENT_KEY가 설정되지 않아 결제창을 열 수 없습니다."); return; }
    if (!ad.paymentOrder) { setError("결제 주문 정보를 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요."); return; }
    try {
      const tossPayments = await loadTossPayments(clientKey);
      const payment = tossPayments.payment({ customerKey: ANONYMOUS });
      await payment.requestPayment({ method: "CARD",
        amount: { currency: "KRW", value: Number(ad.paymentOrder.totalAmount) },
        orderId: ad.paymentOrder.orderNo, orderName: `${eventNames[ad.eventId] || "행사"} 광고`,
        successUrl: `${window.location.origin}/organizer-admin/advertisements/payment/success`,
        failUrl: `${window.location.origin}/organizer-admin/advertisements/payment/fail`,
        card: { useEscrow: false, flowMode: "DEFAULT", useCardPoint: false, useAppCardOnly: false },
      });
    } catch (e) { setError(e.message || "결제창을 열지 못했습니다."); }
  };

  return <><TopNav active="organizer" /><main className="min-h-screen bg-surface-container-low px-lg pb-xl pt-[76px]">
    <div className="max-w-[1100px] mx-auto space-y-lg">
      <header className="flex justify-between items-end"><div><p className="text-caption text-primary">ORGANIZER CENTER</p><h1 className="font-display-lg text-[32px]">행사 광고 신청·관리</h1><p className="text-ink-muted">관리 중인 행사를 선택해 메인 광고를 신청하고 노출 콘텐츠를 관리합니다.</p></div><Link to={`/organizer-admin?organizationId=${organizationId}&eventId=${form.eventId || requestedEventId}`} className="text-caption">관리자 홈</Link></header>
      {error && <p className="bg-error/10 border border-error/20 text-error p-md rounded-xl">{error}</p>}
      {message && <p className="bg-status-available/10 border border-status-available/20 text-status-available p-md rounded-xl">{message}</p>}
      <section className="overflow-hidden bg-white border border-hairline rounded-2xl">
        <div className="p-lg bg-gradient-to-r from-primary/10 to-primary-container/10 flex flex-col md:flex-row md:items-center md:justify-between gap-md">
          <div><p className="text-caption font-bold text-primary tracking-wider">PRICING GUIDE</p><h2 className="font-display-md text-[24px]">투명한 광고 이용 안내</h2><p className="text-caption text-ink-muted mt-xs">현재는 기간과 무관한 건별 정액제이며, 결제 전에 최종 금액을 다시 확인할 수 있습니다.</p></div>
          <div className="flex items-center gap-sm text-caption text-ink-muted"><Icon name="schedule" /><span>결제 유효시간 {pricing.paymentExpiresInMinutes == null ? "-" : `${pricing.paymentExpiresInMinutes}분`}</span></div>
        </div>
        <div>
          <div className="p-lg">
            <div className="flex items-start justify-between gap-md"><div className="flex gap-sm"><span className="w-10 h-10 rounded-xl bg-primary/10 text-primary grid place-items-center"><Icon name="event" /></span><div><p className="font-body-strong">행사 메인 광고</p><p className="text-caption text-ink-muted">메인 배너에 행사 홍보 콘텐츠 노출</p></div></div><span className="text-caption px-sm py-xs rounded-full bg-primary/10 text-primary">유료</span></div>
            <p className="mt-lg text-[28px] font-bold text-on-surface">{formatPrice(pricing.eventAdPrice)}<span className="text-body font-normal text-ink-muted">원 / 건</span></p>
            <p className="mt-sm text-caption text-ink-muted">결제 완료 후 플랫폼 관리자 심사를 거쳐 노출됩니다.</p>
          </div>
        </div>
      </section>
      {!loading && events.length === 0 ? (
        <section className="rounded-2xl border border-hairline bg-white p-xl text-center">
          <Icon name="event_busy" className="text-[32px] text-ink-muted" />
          <h2 className="mt-sm font-display-md text-[22px]">광고할 행사가 없습니다</h2>
          <p className="mt-xs text-caption text-ink-muted">행사를 먼저 등록한 뒤 광고를 신청해 주세요.</p>
          <Link to={`/organizer-admin/events/new?organizationId=${organizationId}`} className="mt-lg inline-flex rounded-full bg-primary px-lg py-sm text-caption font-body-strong text-white">새 행사 등록</Link>
        </section>
      ) : (
      <section className="bg-white border border-hairline rounded-2xl p-lg space-y-lg">
        <div className="flex items-start justify-between gap-md"><div><h2 className="font-display-md text-[22px]">{editingId ? creativeOnly ? "노출 콘텐츠 수정" : "광고 신청 수정" : "새 광고 신청"}</h2>{creativeOnly && <p className="text-caption text-primary mt-xs">노출 일정과 대상은 유지되며 이미지와 문구만 바로 변경됩니다.</p>}</div>{!editingId && <span className="text-caption px-sm py-xs rounded-full bg-surface-container">예상 광고비 {formatPrice(pricing.eventAdPrice)}원</span>}</div>
        <label>운영 조직<select className={field} value={organizationId} onChange={(e) => setOrganizationId(e.target.value)}>{organizations.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>
        <form onSubmit={submit} className="space-y-lg">
          {!editingId && <label>광고할 행사<select required className={field} value={form.eventId} onChange={(e) => change("eventId", e.target.value)}><option value="">행사를 선택하세요</option>{events.map((item) => <option key={item.id} value={item.id}>{item.name}</option>)}</select></label>}
          <FileUploadField label="광고 배너" required value={form.bannerFileId} onChange={(id) => change("bannerFileId", id)} />
          <label>광고 문구<div className="relative"><textarea maxLength={300} className={`${field} h-24 py-md pb-lg`} placeholder="방문객이 한눈에 이해할 수 있는 짧은 문구를 입력하세요." value={form.adText} onChange={(e) => change("adText", e.target.value)} /><span className="absolute right-sm bottom-xs text-caption text-ink-muted">{form.adText.length}/300</span></div></label>
          {!creativeOnly && <div className="grid md:grid-cols-2 gap-md"><label>노출 시작<input required type="datetime-local" className={field} value={form.startAt} onChange={(e) => change("startAt", e.target.value)} /></label><label>노출 종료<input required type="datetime-local" className={field} value={form.endAt} onChange={(e) => change("endAt", e.target.value)} /></label></div>}
          <div className="flex gap-sm justify-end">{editingId && <button type="button" onClick={reset} className="px-lg py-sm border border-hairline rounded-full hover:bg-surface-container">수정 취소</button>}<button disabled={saving || loading || !form.eventId} className="px-xl py-sm bg-primary text-white rounded-full disabled:opacity-50 hover:brightness-95">{saving ? "저장 중..." : creativeOnly ? "노출 콘텐츠 저장" : editingId ? "광고 수정" : `광고 신청 · ${formatPrice(pricing.eventAdPrice)}원`}</button></div>
        </form>
      </section>
      )}
      <section className="space-y-md"><div><h2 className="font-display-md text-[22px]">신청 내역</h2><p className="text-caption text-ink-muted">노출 예정·노출 중 광고는 이미지와 문구를 언제든 관리할 수 있습니다.</p></div>{ads.length === 0 ? <div className="bg-white border border-hairline rounded-xl p-xl text-center text-ink-muted">신청한 광고가 없습니다.</div> : <div className="grid gap-md">{ads.map((ad) => <article key={ad.id} className="overflow-hidden bg-white border border-hairline rounded-2xl flex flex-col md:flex-row"><div className="md:w-56 h-36 md:h-auto bg-surface-container relative">{ad.bannerFileId ? <img src={fileDownloadUrl(ad.bannerFileId)} alt="" className="absolute inset-0 w-full h-full object-cover" /> : <div className="h-full grid place-items-center text-ink-muted"><Icon name={ad.eventId ? "event" : "storefront"} /></div>}</div><div className="flex-1 p-lg flex flex-col md:flex-row md:items-center gap-md"><div className="flex-1"><div className="flex flex-wrap items-center gap-sm"><p className="font-body-strong">{ad.eventId ? eventNames[ad.eventId] || `행사 #${ad.eventId}` : `부스 #${ad.boothId}`}</p><span className={`text-caption px-sm py-[3px] rounded-full ${ad.status === "ACTIVE" ? "bg-green-100 text-green-700" : "bg-surface-container text-ink-muted"}`}>{statusLabel[ad.status] || ad.status}</span></div><p className="text-body mt-sm">{ad.adText || "광고 문구 없음"}</p><p className="text-caption text-ink-muted mt-xs">{new Date(ad.startAt).toLocaleString("ko-KR")} ~ {new Date(ad.endAt).toLocaleString("ko-KR")}</p>{ad.paymentOrder && <p className="text-caption text-ink-muted mt-xs">광고비 {Number(ad.paymentOrder.totalAmount).toLocaleString()}원 · 주문 {ad.paymentOrder.orderNo}</p>}{ad.rejectionReason && <p className="text-caption text-error mt-xs">반려 사유: {ad.rejectionReason}</p>}</div><div className="flex flex-wrap gap-sm">{ad.status === "PAYMENT_PENDING" && <button onClick={() => pay(ad)} className="px-md py-xs bg-primary text-white rounded-full text-caption">결제하기</button>}{["PAYMENT_PENDING", "REVIEW_PENDING", "SCHEDULED", "ACTIVE"].includes(ad.status) && <button onClick={() => edit(ad)} className="px-md py-xs border border-hairline rounded-full text-caption hover:bg-surface-container">{["SCHEDULED", "ACTIVE"].includes(ad.status) ? "콘텐츠 수정" : "수정"}</button>}{!["ACTIVE", "ENDED", "CANCELLED"].includes(ad.status) && <button onClick={() => cancel(ad.id)} className="px-md py-xs border border-error/30 text-error rounded-full text-caption">취소</button>}</div></div></article>)}</div>}</section>
    </div>
  </main></>;
}
