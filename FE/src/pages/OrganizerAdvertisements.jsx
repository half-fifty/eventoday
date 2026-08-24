import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { advertisementApi } from "../api/advertisementApi.js";
import { eventApi } from "../api/eventApi.js";
import FileUploadField from "../components/FileUploadField.jsx";
import Icon from "../components/Icon.jsx";
import { fileDownloadUrl } from "../api/fileApi.js";
import { ANONYMOUS, loadTossPayments } from "@tosspayments/tosspayments-sdk";
import TopNav from "../components/TopNav.jsx";

const emptyForm = {
  eventId: "",
  bannerFileId: null,
  adText: "",
  startAt: "",
  endAt: "",
};
const statusLabel = {
  PAYMENT_PENDING: "결제 대기",
  PAID: "결제 완료·심사 대기",
  REVIEW_PENDING: "심사 대기",
  REVISION_PENDING: "수정 재심사",
  SCHEDULED: "노출 예정",
  ACTIVE: "노출 중",
  REJECTED: "반려",
  CANCELLED: "취소",
  ENDED: "종료",
  REFUNDED: "취소 · 환불 완료",
  STOPPED: "노출 중단",
};
const toOffset = (value) => (value ? new Date(value).toISOString() : null);
const toInput = (value) => {
  if (!value) return "";
  const date = new Date(value);
  if (Number.isNaN(date.getTime())) return "";
  const localDate = new Date(
    date.getTime() - date.getTimezoneOffset() * 60_000,
  );
  return localDate.toISOString().slice(0, 16);
};
const formatPrice = (value) =>
  value == null ? "-" : Number(value).toLocaleString();
const tossBankNames = {
  "002": "산업은행", "003": "기업은행", "004": "국민은행", "007": "수협은행",
  "011": "농협은행", "020": "우리은행", "023": "SC제일은행", "027": "한국씨티은행",
  "031": "대구은행", "032": "부산은행", "034": "광주은행", "035": "제주은행",
  "037": "전북은행", "039": "경남은행", "045": "새마을금고", "048": "신협",
  "071": "우체국", "081": "하나은행", "088": "신한은행", "089": "케이뱅크",
  "090": "카카오뱅크", "092": "토스뱅크",
};
const bankName = (code) => tossBankNames[String(code || "").padStart(3, "0")] || code || "은행";

export default function OrganizerAdvertisements() {
  const query = new URLSearchParams(window.location.search);
  const requestedEventId = query.get("eventId") || "";
  const [organizationId, setOrganizationId] = useState(
    query.get("organizationId") || localStorage.getItem("organizationId") || "",
  );
  const [organizations, setOrganizations] = useState([]);
  const [events, setEvents] = useState([]);
  const [ads, setAds] = useState([]);
  const [pricing, setPricing] = useState({
    eventAdPrice: null,
    boothAdPrice: null,
    paymentExpiresInMinutes: null,
  });
  const [form, setForm] = useState(emptyForm);
  const [editingId, setEditingId] = useState(null);
  const [loading, setLoading] = useState(true);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [cancelTarget, setCancelTarget] = useState(null);
  const [paymentTarget, setPaymentTarget] = useState(null);
  const [paymentMethod, setPaymentMethod] = useState("CARD");
  const [archiveTarget, setArchiveTarget] = useState(null);
  const [actionLoading, setActionLoading] = useState(false);
  const [aiTone, setAiTone] = useState("명확하고 신뢰감 있게");
  const [aiLoading, setAiLoading] = useState(false);
  const [aiResult, setAiResult] = useState(null);
  const field =
    "w-full h-11 border border-hairline rounded-lg px-md bg-white outline-none focus:border-primary";
  const editingAd = ads.find((ad) => ad.id === editingId);
  const creativeOnly = ["SCHEDULED", "ACTIVE"].includes(editingAd?.status);

  const loadAds = async (id = organizationId) => {
    if (!id) {
      setAds([]);
      return;
    }
    const result = await advertisementApi.organizationList(id, {
      size: 100,
      sort: "createdAt,desc",
    });
    setAds(
      (result?.data?.content || []).filter(
        (advertisement) => advertisement.eventId,
      ),
    );
  };

  useEffect(() => {
    advertisementApi
      .pricing()
      .then((result) => {
        if (result?.data) setPricing(result.data);
      })
      .catch((e) =>
        setError(e.message || "광고 가격 정보를 불러오지 못했습니다."),
      );
    eventApi
      .managedOrganizations()
      .then((result) => {
        const list = result?.data || [];
        setOrganizations(list);
        const selected = list.some(
          (item) => String(item.id) === String(organizationId),
        )
          ? organizationId
          : String(list[0]?.id || "");
        setOrganizationId(selected);
      })
      .catch((e) => setError(e.message || "운영 조직을 불러오지 못했습니다."));
  }, []);

  useEffect(() => {
    if (!organizationId) {
      setLoading(false);
      return;
    }
    localStorage.setItem("organizationId", organizationId);
    setLoading(true);
    setError("");
    setMessage("");
    setForm(emptyForm);
    setEditingId(null);
    Promise.all([
      eventApi.organizationList(organizationId, { size: 100 }),
      loadAds(organizationId),
    ])
      .then(([eventResult]) => {
        const list = (eventResult?.data?.content || []).filter(
          (item) => item.status === "PUBLISHED",
        );
        setEvents(list);
        const selectedEventId = list.some(
          (item) => String(item.id) === requestedEventId,
        )
          ? requestedEventId
          : String(list[0]?.id || "");
        setForm((previous) => ({ ...previous, eventId: selectedEventId }));
      })
      .catch((e) => setError(e.message || "광고 정보를 불러오지 못했습니다."))
      .finally(() => setLoading(false));
  }, [organizationId]);

  useEffect(() => {
    const waitingForDeposit = ads.some(
      (ad) => ad.paymentOrder?.status === "WAITING_FOR_DEPOSIT",
    );
    if (!organizationId || !waitingForDeposit) return undefined;
    const refresh = () => {
      if (document.visibilityState === "visible") {
        loadAds(organizationId).catch(() => {});
      }
    };
    const timer = window.setInterval(refresh, 10_000);
    window.addEventListener("focus", refresh);
    document.addEventListener("visibilitychange", refresh);
    return () => {
      window.clearInterval(timer);
      window.removeEventListener("focus", refresh);
      document.removeEventListener("visibilitychange", refresh);
    };
  }, [ads, organizationId]);

  const eventNames = useMemo(
    () => Object.fromEntries(events.map((event) => [event.id, event.name])),
    [events],
  );
  const selectedEvent = events.find(
    (event) => String(event.id) === String(form.eventId),
  );
  const exposureDays =
    form.startAt && form.endAt
      ? Math.max(
          0,
          Math.ceil(
            (new Date(form.endAt) - new Date(form.startAt)) / 86_400_000,
          ),
        )
      : 0;
  const change = (key, value) =>
    setForm((previous) => ({ ...previous, [key]: value }));
  const reset = () => {
    setEditingId(null);
    setForm({ ...emptyForm, eventId: String(events[0]?.id || "") });
    setAiResult(null);
  };

  const suggestAdCopy = async () => {
    if (!form.eventId || !organizationId || aiLoading) return;
    setAiLoading(true);
    setAiResult(null);
    setError("");
    try {
      const result = await advertisementApi.suggestCopy({
        organizationId: Number(organizationId),
        eventId: Number(form.eventId),
        draft: form.adText,
        tone: aiTone,
      });
      setAiResult(result?.data || null);
    } catch (requestError) {
      setError(requestError.message || "AI 광고 문구를 생성하지 못했습니다.");
    } finally {
      setAiLoading(false);
    }
  };

  const submit = async (event) => {
    event.preventDefault();
    setMessage("");
    if (!editingId && !form.eventId) {
      setError("광고할 행사를 선택해 주세요.");
      return;
    }
    if (!form.bannerFileId) {
      setError("광고 배너 이미지를 업로드해 주세요.");
      return;
    }
    if (new Date(form.startAt) >= new Date(form.endAt)) {
      setError("광고 종료일은 시작일 이후여야 합니다.");
      return;
    }
    setSaving(true);
    setError("");
    setMessage("");
    const payload = {
      applicantOrganizationId: Number(organizationId),
      bannerFileId: form.bannerFileId,
      adText: form.adText,
      startAt: toOffset(form.startAt),
      endAt: toOffset(form.endAt),
    };
    try {
      const result = creativeOnly
        ? await advertisementApi.updateCreative(editingId, {
            bannerFileId: form.bannerFileId,
            adText: form.adText,
          })
        : editingId
          ? await advertisementApi.update(editingId, payload)
          : await advertisementApi.createEvent(form.eventId, payload);
      if (result?.data?.paymentOrder) {
        setMessage(
          `광고 신청이 저장되었습니다. 결제 금액은 ${Number(result.data.paymentOrder.totalAmount).toLocaleString()}원이며, 아래 신청 내역에서 결제할 수 있습니다.`,
        );
      } else {
        setMessage(
          creativeOnly
            ? "광고 콘텐츠를 수정했습니다. 기존 노출은 중단되며 관리자 재심사 후 다시 노출됩니다."
            : editingId ? "광고 정보를 수정했습니다." : "광고 신청을 저장했습니다.",
        );
      }
      await loadAds();
      reset();
    } catch (e) {
      setError(e.message || "광고를 저장하지 못했습니다.");
    } finally {
      setSaving(false);
    }
  };

  const edit = (ad) => {
    setEditingId(ad.id);
    setMessage("");
    setForm({
      eventId: String(ad.eventId || ""),
      bannerFileId: ad.bannerFileId,
      adText: ad.adText || "",
      startAt: toInput(ad.startAt),
      endAt: toInput(ad.endAt),
    });
    window.scrollTo({ top: 0, behavior: "smooth" });
  };
  const confirmCancel = async () => {
    if (!cancelTarget || actionLoading) return;
    const id = cancelTarget.id;
    setActionLoading(true);
    setMessage("");
    try {
      const result = await advertisementApi.cancel(id);
      const nextStatus = result?.data?.advertisementStatus;
      if (nextStatus) {
        setAds((previous) =>
          previous.map((ad) =>
            ad.id === id
              ? {
                  ...ad,
                  status: nextStatus,
                  paymentOrder:
                    result?.data?.refunded && ad.paymentOrder
                      ? { ...ad.paymentOrder, status: "REFUNDED" }
                      : ad.paymentOrder,
                }
              : ad,
          ),
        );
      }
      setCancelTarget(null);
      await loadAds();
      setError("");
      setMessage(result?.data?.message || "광고 신청을 취소했습니다.");
    } catch (e) {
      setError(e.message || "광고를 취소하지 못했습니다.");
    } finally {
      setActionLoading(false);
    }
  };

  const confirmArchive = async () => {
    if (!archiveTarget || actionLoading) return;
    const id = archiveTarget.id;
    setActionLoading(true);
    try {
      await advertisementApi.archive(id);
      setAds((previous) => previous.filter((ad) => ad.id !== id));
      setArchiveTarget(null);
      setError("");
      setMessage(
        "광고를 신청 내역에서 숨겼습니다. 결제·환불 기록은 유지됩니다.",
      );
      await loadAds();
    } catch (e) {
      setError(e.message || "광고를 신청 내역에서 숨기지 못했습니다.");
    } finally {
      setActionLoading(false);
    }
  };
  const pay = async (ad, method = paymentMethod) => {
    const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
    if (!clientKey) {
      setError("VITE_TOSS_CLIENT_KEY가 설정되지 않아 결제창을 열 수 없습니다.");
      return;
    }
    if (!ad.paymentOrder) {
      setError(
        "결제 주문 정보를 불러오지 못했습니다. 새로고침 후 다시 시도해 주세요.",
      );
      return;
    }
    try {
      await advertisementApi.selectPaymentMethod(ad.id, method);
      const tossPayments = await loadTossPayments(clientKey);
      const payment = tossPayments.payment({ customerKey: ANONYMOUS });
      const organizationName = organizations.find(
        (item) => String(item.id) === String(organizationId),
      )?.name || "광고주";
      const commonRequest = {
        method,
        amount: { currency: "KRW", value: Number(ad.paymentOrder.totalAmount) },
        orderId: ad.paymentOrder.orderNo,
        orderName: `${eventNames[ad.eventId] || "행사"} 광고`,
        successUrl: `${window.location.origin}/organizer-admin/advertisements/payment/success`,
        failUrl: `${window.location.origin}/organizer-admin/advertisements/payment/fail`,
        customerName: organizationName.slice(0, 100),
      };
      await payment.requestPayment(method === "VIRTUAL_ACCOUNT" ? {
        ...commonRequest,
        virtualAccount: {
          cashReceipt: { type: "지출증빙" },
          useEscrow: false,
          validHours: Math.max(1, Math.min(2160, Math.ceil(Number(pricing.paymentExpiresInMinutes || 1440) / 60))),
        },
      } : {
        ...commonRequest,
        card: {
          useEscrow: false,
          flowMode: "DEFAULT",
          useCardPoint: false,
          useAppCardOnly: false,
        },
      });
    } catch (e) {
      setPaymentTarget(null);
      setError(e.message || "결제창을 열지 못했습니다.");
    }
  };

  return (
    <>
      <TopNav active="organizer" />
      <main className="min-h-screen bg-surface-container-low px-lg pb-xl pt-[76px]">
        <div className="max-w-[1100px] mx-auto space-y-lg">
          <header className="flex justify-between items-end">
            <div>
              <p className="text-caption text-primary">ORGANIZER CENTER</p>
              <h1 className="font-display-lg text-[32px]">
                행사 광고 신청·관리
              </h1>
              <p className="text-ink-muted">
                관리 중인 행사를 선택해 메인 광고를 신청하고 노출 콘텐츠를
                관리합니다.
              </p>
            </div>
            <Link
              to={`/organizer-admin?organizationId=${organizationId}&eventId=${form.eventId || requestedEventId}`}
              className="text-caption"
            >
              관리자 홈
            </Link>
          </header>
          {error && (
            <p className="bg-error/10 border border-error/20 text-error p-md rounded-xl">
              {error}
            </p>
          )}
          {message && (
            <p className="bg-status-available/10 border border-status-available/20 text-status-available p-md rounded-xl">
              {message}
            </p>
          )}
          <section className="overflow-hidden bg-white border border-hairline rounded-2xl">
            <div className="p-lg bg-gradient-to-r from-primary/10 to-primary-container/10 flex flex-col md:flex-row md:items-center md:justify-between gap-md">
              <div>
                <p className="text-caption font-bold text-primary tracking-wider">
                  PRICING GUIDE
                </p>
                <h2 className="font-display-md text-[24px]">
                  투명한 광고 이용 안내
                </h2>
                <p className="text-caption text-ink-muted mt-xs">
                  현재는 기간과 무관한 건별 정액제이며, 결제 전에 최종 금액을
                  다시 확인할 수 있습니다.
                </p>
              </div>
              <div className="flex items-center gap-sm text-caption text-ink-muted">
                <Icon name="schedule" />
                <span>
                  결제 유효시간{" "}
                  {pricing.paymentExpiresInMinutes == null
                    ? "-"
                    : `${pricing.paymentExpiresInMinutes}분`}
                </span>
              </div>
            </div>
            <div>
              <div className="p-lg">
                <div className="flex items-start justify-between gap-md">
                  <div className="flex gap-sm">
                    <span className="w-10 h-10 rounded-xl bg-primary/10 text-primary grid place-items-center">
                      <Icon name="event" />
                    </span>
                    <div>
                      <p className="font-body-strong">행사 메인 광고</p>
                      <p className="text-caption text-ink-muted">
                        메인 배너에 행사 홍보 콘텐츠 노출
                      </p>
                    </div>
                  </div>
                  <span className="text-caption px-sm py-xs rounded-full bg-primary/10 text-primary">
                    유료
                  </span>
                </div>
                <p className="mt-lg text-[28px] font-bold text-on-surface">
                  {formatPrice(pricing.eventAdPrice)}
                  <span className="text-body font-normal text-ink-muted">
                    원 / 건
                  </span>
                </p>
                <p className="mt-sm text-caption text-ink-muted">
                  결제 완료 후 플랫폼 관리자 심사를 거쳐 노출됩니다.
                </p>
              </div>
            </div>
          </section>
          <section className="rounded-2xl border border-primary/20 bg-primary/5 p-lg">
            <div className="flex gap-md">
              <span className="w-10 h-10 shrink-0 rounded-xl bg-white text-primary grid place-items-center">
                <Icon name="policy" />
              </span>
              <div>
                <h2 className="font-body-strong">광고 취소·환불 정책</h2>
                <ul className="mt-sm space-y-xs text-caption text-on-surface-variant">
                  <li>· 결제 전: 광고 신청만 즉시 취소됩니다.</li>
                  <li>
                    · 결제 후부터 노출 시작 전까지: 결제 금액 전액을 환불합니다.
                  </li>
                  <li>
                    · 운영자 심사 반려: 결제된 광고는 전액 자동 환불됩니다.
                  </li>
                  <li>
                    · 노출 시작 후: 즉시 중단할 수 있지만 이미 제공된 광고이므로
                    환불되지 않습니다.
                  </li>
                </ul>
              </div>
            </div>
          </section>
          {!loading && events.length === 0 ? (
            <section className="rounded-2xl border border-hairline bg-white p-xl text-center">
              <Icon name="event_busy" className="text-[32px] text-ink-muted" />
              <h2 className="mt-sm font-display-md text-[22px]">
                광고할 행사가 없습니다
              </h2>
              <p className="mt-xs text-caption text-ink-muted">
                행사를 먼저 등록한 뒤 광고를 신청해 주세요.
              </p>
              <Link
                to={`/organizer-admin/events/new?organizationId=${organizationId}`}
                className="mt-lg inline-flex rounded-full bg-primary px-lg py-sm text-caption font-body-strong text-white"
              >
                새 행사 등록
              </Link>
            </section>
          ) : (
            <section className="bg-white border border-hairline rounded-2xl p-lg space-y-lg">
              <ol
                className="grid grid-cols-2 md:grid-cols-4 gap-sm"
                aria-label="광고 신청 절차"
              >
                {[
                  ["edit_square", "1. 콘텐츠 작성"],
                  ["payments", "2. 광고비 결제"],
                  ["fact_check", "3. 운영자 심사"],
                  ["campaign", "4. 광고 노출"],
                ].map(([icon, label], index) => (
                  <li
                    key={label}
                    className={`rounded-xl border p-md text-caption font-body-strong flex items-center gap-sm ${index === 0 ? "border-primary bg-primary/5 text-primary" : "border-hairline text-ink-muted"}`}
                  >
                    <Icon name={icon} />
                    {label}
                  </li>
                ))}
              </ol>
              <div className="flex items-start justify-between gap-md">
                <div>
                  <h2 className="font-display-md text-[22px]">
                    {editingId
                      ? creativeOnly
                        ? "노출 콘텐츠 수정"
                        : "광고 신청 수정"
                      : "새 광고 신청"}
                  </h2>
                  {creativeOnly && (
                    <div className="mt-sm rounded-xl border border-amber-300 bg-amber-50 p-md text-caption leading-6 text-amber-900">
                      <p className="font-body-strong">수정하면 즉시 재심사 상태로 전환됩니다.</p>
                      <p>현재 노출 중인 광고는 화면에서 내려가며, 관리자가 다시 승인한 뒤 기존 일정 범위 안에서 재노출됩니다. 이미 노출된 기간은 환불되지 않습니다.</p>
                    </div>
                  )}
                </div>
                {!editingId && (
                  <span className="text-caption px-sm py-xs rounded-full bg-surface-container">
                    예상 광고비 {formatPrice(pricing.eventAdPrice)}원
                  </span>
                )}
              </div>
              <label>
                운영 조직
                <select
                  className={field}
                  value={organizationId}
                  onChange={(e) => setOrganizationId(e.target.value)}
                >
                  {organizations.map((item) => (
                    <option key={item.id} value={item.id}>
                      {item.name}
                    </option>
                  ))}
                </select>
              </label>
              <form onSubmit={submit} className="space-y-lg">
                {!editingId && (
                  <label>
                    광고할 행사
                    <select
                      required
                      className={field}
                      value={form.eventId}
                      onChange={(e) => change("eventId", e.target.value)}
                    >
                      <option value="">행사를 선택하세요</option>
                      {events.map((item) => (
                        <option key={item.id} value={item.id}>
                          {item.name}
                        </option>
                      ))}
                    </select>
                  </label>
                )}
                <div className="grid lg:grid-cols-[1fr_420px] gap-lg items-start">
                  <div className="space-y-sm">
                    <FileUploadField
                      label="광고 배너"
                      required
                      value={form.bannerFileId}
                      onChange={(id) => change("bannerFileId", id)}
                    />
                    <div className="rounded-xl bg-surface-container-low p-md text-caption text-ink-muted leading-6">
                      <p className="font-body-strong text-on-surface">
                        권장 이미지: 1600 × 900px (16:9)
                      </p>
                      <p>
                        JPG·PNG·WebP 권장 · 중요한 로고와 문구는 가장자리에서
                        80px 이상 안쪽에 배치해 주세요.
                      </p>
                      <p>
                        화면 크기에 따라 일부 영역이 잘릴 수 있으므로 이미지
                        안에 긴 문장을 넣기보다 아래 광고 문구를 활용하는 것이
                        좋습니다.
                      </p>
                    </div>
                  </div>
                  <aside className="rounded-2xl border border-hairline bg-surface-container-low p-md lg:sticky lg:top-[92px]">
                    <div className="flex items-center justify-between mb-sm">
                      <p className="font-body-strong">실제 노출 미리보기</p>
                      <span className="text-[11px] text-ink-muted">
                        PC · 16:9
                      </span>
                    </div>
                    <div className="overflow-hidden rounded-xl bg-gradient-to-br from-primary-focus to-secondary text-white shadow-lg">
                      <div className="relative aspect-[16/9] bg-black/15">
                        {form.bannerFileId ? (
                          <img
                            src={fileDownloadUrl(form.bannerFileId)}
                            alt="광고 배너 미리보기"
                            className="absolute inset-0 w-full h-full object-cover"
                          />
                        ) : (
                          <div className="absolute inset-0 grid place-items-center text-center text-white/70">
                            <span>
                              <Icon
                                name="image"
                                className="block mx-auto text-[36px] mb-xs"
                              />
                              배너를 올리면 여기에 표시됩니다.
                            </span>
                          </div>
                        )}
                        <div className="absolute inset-x-0 bottom-0 h-2/3 bg-gradient-to-t from-black/80 to-transparent" />
                        <div className="absolute inset-x-md bottom-md">
                          <span className="text-[10px] px-xs py-xxs rounded bg-white/20">
                            AD
                          </span>
                          <p className="mt-xs font-display-md text-[18px] line-clamp-1">
                            {selectedEvent?.name ||
                              "광고할 행사를 선택해 주세요"}
                          </p>
                          <p className="mt-xxs text-caption text-white/85 line-clamp-2">
                            {form.adText ||
                              "방문객에게 보여줄 핵심 광고 문구가 표시됩니다."}
                          </p>
                        </div>
                      </div>
                    </div>
                    <div className="mt-md grid grid-cols-2 gap-sm text-caption">
                      <div className="rounded-lg bg-white p-sm">
                        <span className="block text-ink-muted">
                          예상 노출 기간
                        </span>
                        <strong>
                          {exposureDays ? `${exposureDays}일` : "기간 선택 전"}
                        </strong>
                      </div>
                      <div className="rounded-lg bg-white p-sm">
                        <span className="block text-ink-muted">문구 사용</span>
                        <strong>{form.adText.length}/300자</strong>
                      </div>
                    </div>
                  </aside>
                </div>
                <label>
                  <span className="flex flex-wrap items-center justify-between gap-sm">
                    <span>광고 문구</span>
                    <span className="flex items-center gap-xs">
                      <select
                        value={aiTone}
                        onChange={(e) => setAiTone(e.target.value)}
                        className="h-9 rounded-full border border-hairline bg-white px-sm text-caption"
                      >
                        <option>명확하고 신뢰감 있게</option>
                        <option>짧고 강렬하게</option>
                        <option>친근하고 활기차게</option>
                        <option>전문적이고 고급스럽게</option>
                      </select>
                      <button
                        type="button"
                        disabled={aiLoading || !form.eventId}
                        onClick={suggestAdCopy}
                        className="inline-flex h-9 items-center gap-xs rounded-full bg-primary/10 px-md text-caption font-body-strong text-primary disabled:opacity-50"
                      >
                        <Icon name="auto_awesome" />
                        {aiLoading ? "생성 중..." : "AI 문구 추천"}
                      </button>
                    </span>
                  </span>
                  <div className="relative">
                    <textarea
                      maxLength={300}
                      className={`${field} h-24 py-md pb-lg`}
                      placeholder="방문객이 한눈에 이해할 수 있는 짧은 문구를 입력하세요."
                      value={form.adText}
                      onChange={(e) => change("adText", e.target.value)}
                    />
                    <span className="absolute right-sm bottom-xs text-caption text-ink-muted">
                      {form.adText.length}/300
                    </span>
                  </div>
                </label>
                {aiResult && (
                  <section className="rounded-xl border border-primary/20 bg-primary/5 p-md">
                    <div className="flex items-start justify-between gap-md">
                      <div>
                        <p className="font-body-strong text-primary">AI 추천 결과</p>
                        <p className="mt-xs text-caption text-ink-muted">
                          행사 정보와 광고 정책을 근거로 생성했습니다. 선택 후 직접 수정할 수 있습니다.
                        </p>
                      </div>
                      <span className="rounded-full bg-white px-sm py-xs text-[11px] font-bold text-primary">검색 기반 RAG</span>
                    </div>
                    {aiResult.notice && (
                      <p className="mt-md rounded-lg bg-white p-sm text-caption text-error">{aiResult.notice}</p>
                    )}
                    <div className="mt-md space-y-sm">
                      {(aiResult.suggestions || []).map((suggestion, index) => (
                        <button
                          key={`${suggestion.copy}-${index}`}
                          type="button"
                          onClick={() => change("adText", suggestion.copy)}
                          className="w-full rounded-lg border border-hairline bg-white p-md text-left transition hover:border-primary"
                        >
                          <span className="text-[11px] font-bold text-primary">추천 {index + 1}</span>
                          <span className="mt-xs block font-body-strong leading-6">{suggestion.copy}</span>
                          {suggestion.rationale && (
                            <span className="mt-xs block text-caption text-ink-muted">{suggestion.rationale}</span>
                          )}
                          {(suggestion.warnings || []).length > 0 && (
                            <span className="mt-sm block text-caption text-error">주의 · {suggestion.warnings.join(" · ")}</span>
                          )}
                        </button>
                      ))}
                    </div>
                    {(aiResult.sources || []).length > 0 && (
                      <div className="mt-md border-t border-primary/10 pt-md">
                        <p className="text-[11px] font-bold text-ink-muted">AI가 사용한 검색 근거 {aiResult.sources.length}개</p>
                        <div className="mt-xs flex flex-wrap gap-xs">
                          {aiResult.sources.map((source) => (
                            <span key={source} className="rounded-full border border-primary/10 bg-white px-sm py-xs text-[11px] text-ink-muted">{source}</span>
                          ))}
                        </div>
                      </div>
                    )}
                  </section>
                )}
                {!creativeOnly && (
                  <div className="grid md:grid-cols-2 gap-md">
                    <label>
                      노출 시작
                      <input
                        required
                        type="datetime-local"
                        className={field}
                        value={form.startAt}
                        onChange={(e) => change("startAt", e.target.value)}
                      />
                    </label>
                    <label>
                      노출 종료
                      <input
                        required
                        type="datetime-local"
                        className={field}
                        value={form.endAt}
                        onChange={(e) => change("endAt", e.target.value)}
                      />
                    </label>
                  </div>
                )}
                <div className="flex gap-sm justify-end">
                  {editingId && (
                    <button
                      type="button"
                      onClick={reset}
                      className="px-lg py-sm border border-hairline rounded-full hover:bg-surface-container"
                    >
                      수정 취소
                    </button>
                  )}
                  <button
                    disabled={saving || loading || !form.eventId}
                    className="px-xl py-sm bg-primary text-white rounded-full disabled:opacity-50 hover:brightness-95"
                  >
                    {saving
                      ? "저장 중..."
                      : creativeOnly
                        ? "수정 후 재심사 요청"
                        : editingId
                          ? "광고 수정"
                          : `광고 신청 · ${formatPrice(pricing.eventAdPrice)}원`}
                  </button>
                </div>
              </form>
            </section>
          )}
          <section className="space-y-md">
            <div>
              <h2 className="font-display-md text-[22px]">신청 내역</h2>
              <p className="text-caption text-ink-muted">
                노출 예정·노출 중 광고의 이미지나 문구를 수정하면 기존 노출이
                중단되고 관리자 재심사를 거칩니다.
              </p>
            </div>
            {ads.length === 0 ? (
              <div className="bg-white border border-hairline rounded-xl p-xl text-center text-ink-muted">
                신청한 광고가 없습니다.
              </div>
            ) : (
              <div className="grid gap-md">
                {ads.map((ad) => (
                  <article
                    key={ad.id}
                    className="relative overflow-hidden bg-white border border-hairline rounded-2xl flex flex-col md:flex-row"
                  >
                    {["CANCELLED", "REFUNDED", "STOPPED", "ENDED"].includes(
                      ad.status,
                    ) && (
                      <button
                        type="button"
                        onClick={() => setArchiveTarget(ad)}
                        aria-label={`${eventNames[ad.eventId] || "광고"} 신청 내역에서 숨기기`}
                        title="신청 내역에서 숨기기"
                        className="absolute right-sm top-sm z-10 grid h-8 w-8 place-items-center rounded-full bg-white/95 text-ink-muted shadow hover:text-error"
                      >
                        <Icon name="close" />
                      </button>
                    )}
                    <div className="md:w-56 h-36 md:h-auto bg-surface-container relative">
                      {ad.bannerFileId ? (
                        <img
                          src={fileDownloadUrl(ad.bannerFileId)}
                          alt=""
                          onError={(imageEvent) => {
                            imageEvent.currentTarget.style.display = "none";
                          }}
                          className="absolute inset-0 w-full h-full object-cover"
                        />
                      ) : (
                        <div className="h-full grid place-items-center text-ink-muted">
                          <Icon name={ad.eventId ? "event" : "storefront"} />
                        </div>
                      )}
                    </div>
                    <div className="flex-1 p-lg flex flex-col md:flex-row md:items-center gap-md">
                      <div className="flex-1">
                        <div className="flex flex-wrap items-center gap-sm">
                          <p className="font-body-strong">
                            {ad.eventId
                              ? eventNames[ad.eventId] || "행사 광고"
                              : "부스 광고"}
                          </p>
                          <span
                            className={`text-caption px-sm py-[3px] rounded-full ${ad.status === "ACTIVE" ? "bg-green-100 text-green-700" : "bg-surface-container text-ink-muted"}`}
                          >
                            {statusLabel[ad.status] || ad.status}
                          </span>
                        </div>
                        <p className="text-body mt-sm">
                          {ad.adText || "광고 문구 없음"}
                        </p>
                        <p className="text-caption text-ink-muted mt-xs">
                          {new Date(ad.startAt).toLocaleString("ko-KR")} ~{" "}
                          {new Date(ad.endAt).toLocaleString("ko-KR")}
                        </p>
                        {ad.paymentOrder && (
                          <p className="text-caption text-ink-muted mt-xs">
                            광고비{" "}
                            {Number(
                              ad.paymentOrder.totalAmount,
                            ).toLocaleString()}
                            원 · 주문 {ad.paymentOrder.orderNo}
                            {ad.paymentOrder.status === "REFUNDED"
                              ? " · 환불 완료"
                              : ad.paymentOrder.status === "PAID"
                                ? " · 결제 완료"
                                : ad.paymentOrder.status === "WAITING_FOR_DEPOSIT"
                                  ? " · 가상계좌 입금 대기"
                                  : ad.paymentOrder.status === "EXPIRED"
                                    ? " · 결제기한 만료"
                                : ""}
                          </p>
                        )}
                        {ad.paymentOrder?.status === "WAITING_FOR_DEPOSIT" && ad.paymentOrder.virtualAccount && (
                          <div className="mt-sm rounded-lg border border-primary/20 bg-primary/5 px-md py-sm text-caption">
                            <p className="font-body-strong text-ink">입금 계좌</p>
                            <p className="mt-xs text-ink">
                              {bankName(ad.paymentOrder.virtualAccount.bankCode)} {ad.paymentOrder.virtualAccount.accountNumber}
                            </p>
                            <p className="mt-xs text-ink-muted">
                              예금주 {ad.paymentOrder.virtualAccount.customerName || "-"} · 입금기한 {new Date(ad.paymentOrder.virtualAccount.dueAt).toLocaleString("ko-KR")}
                            </p>
                            <p className="mt-xs text-primary">입금 확인 시 이 화면이 자동으로 갱신됩니다.</p>
                          </div>
                        )}
                        {ad.rejectionReason && (
                          <p className="text-caption text-error mt-xs">
                            반려 사유: {ad.rejectionReason}
                          </p>
                        )}
                      </div>
                      <div className="flex flex-wrap gap-sm">
                        {ad.status === "PAYMENT_PENDING" && !["WAITING_FOR_DEPOSIT", "EXPIRED"].includes(ad.paymentOrder?.status) && (
                          <button
                            onClick={() => {
                              setPaymentMethod("CARD");
                              setPaymentTarget(ad);
                            }}
                            className="px-md py-xs bg-primary text-white rounded-full text-caption"
                          >
                            결제하기
                          </button>
                        )}
                        {[
                          "PAYMENT_PENDING",
                          "REVIEW_PENDING",
                          "SCHEDULED",
                          "ACTIVE",
                        ].includes(ad.status) && (
                          <button
                            onClick={() => edit(ad)}
                            className="px-md py-xs border border-hairline rounded-full text-caption hover:bg-surface-container"
                          >
                            {["SCHEDULED", "ACTIVE"].includes(ad.status)
                              ? "콘텐츠 수정"
                              : "수정"}
                          </button>
                        )}
                        {![
                          "ENDED",
                          "CANCELLED",
                          "REFUNDED",
                          "STOPPED",
                        ].includes(ad.status) && (
                          <button
                            onClick={() => setCancelTarget(ad)}
                            className="px-md py-xs border border-error/30 text-error rounded-full text-caption"
                          >
                            {ad.status === "ACTIVE"
                              ? "노출 중단"
                              : [
                                    "PAID",
                                    "REVIEW_PENDING",
                                    "REJECTED",
                                    "SCHEDULED",
                                  ].includes(ad.status)
                                ? "취소 · 전액 환불"
                                : ad.status === "REVISION_PENDING" && new Date(ad.startAt).getTime() > Date.now()
                                  ? "취소 · 전액 환불"
                                  : ad.status === "REVISION_PENDING"
                                    ? "취소 · 환불 불가"
                                : "취소"}
                          </button>
                        )}
                      </div>
                    </div>
                  </article>
                ))}
              </div>
            )}
          </section>
        </div>
      </main>
      {paymentTarget && (
        <div
          className="fixed inset-0 z-[200] grid place-items-center bg-black/55 p-lg"
          onMouseDown={(event) => event.target === event.currentTarget && setPaymentTarget(null)}
        >
          <section role="dialog" aria-modal="true" aria-labelledby="ad-payment-title" className="w-full max-w-md rounded-2xl bg-white p-xl shadow-2xl">
            <p className="text-caption font-bold text-primary">광고비 결제</p>
            <h2 id="ad-payment-title" className="mt-xs font-display-md text-[22px]">결제 방법을 선택해 주세요</h2>
            <p className="mt-sm text-caption leading-6 text-ink-muted">
              {eventNames[paymentTarget.eventId] || "행사 광고"} · {Number(paymentTarget.paymentOrder?.totalAmount || 0).toLocaleString()}원
            </p>
            <div className="mt-lg grid gap-sm sm:grid-cols-2">
              {[
                { value: "CARD", icon: "credit_card", title: "카드 결제", description: "결제 승인 후 광고 심사가 시작됩니다." },
                { value: "VIRTUAL_ACCOUNT", icon: "account_balance", title: "가상계좌", description: "발급 계좌에 입금되면 광고 심사가 시작됩니다." },
              ].map((method) => (
                <button
                  key={method.value}
                  type="button"
                  onClick={() => setPaymentMethod(method.value)}
                  className={`rounded-xl border p-md text-left transition ${paymentMethod === method.value ? "border-primary bg-primary/5 ring-1 ring-primary" : "border-hairline hover:border-primary/50"}`}
                >
                  <Icon name={method.icon} className={paymentMethod === method.value ? "text-primary" : "text-ink-muted"} />
                  <strong className="mt-sm block">{method.title}</strong>
                  <span className="mt-xs block text-caption leading-5 text-ink-muted">{method.description}</span>
                </button>
              ))}
            </div>
            {paymentMethod === "VIRTUAL_ACCOUNT" && (
              <div className="mt-md rounded-xl bg-amber-50 p-md text-caption leading-6 text-amber-900">
                <p>계좌 발급만으로 결제가 완료되지 않습니다. 입금 확인 웹훅이 도착한 뒤 광고가 심사 대기로 변경됩니다.</p>
                <p className="mt-xs">가상계좌 사용 가능 여부는 Toss 상점 계약 설정에 따라 달라질 수 있습니다.</p>
              </div>
            )}
            <div className="mt-lg flex justify-end gap-sm">
              <button type="button" onClick={() => setPaymentTarget(null)} className="rounded-full border border-hairline px-lg py-sm">취소</button>
              <button type="button" onClick={() => pay(paymentTarget, paymentMethod)} className="rounded-full bg-primary px-lg py-sm font-body-strong text-white">
                {paymentMethod === "VIRTUAL_ACCOUNT" ? "가상계좌 발급하기" : "카드로 결제하기"}
              </button>
            </div>
          </section>
        </div>
      )}
      {cancelTarget && (
        <div
          className="fixed inset-0 z-[200] grid place-items-center bg-black/55 p-lg"
          onMouseDown={(event) =>
            event.target === event.currentTarget &&
            !actionLoading &&
            setCancelTarget(null)
          }
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="ad-cancel-title"
            className="w-full max-w-md rounded-2xl bg-white p-xl shadow-2xl"
          >
            <div className="flex items-start gap-md">
              <span className="grid h-11 w-11 shrink-0 place-items-center rounded-xl bg-error/10 text-error">
                <Icon
                  name={
                    cancelTarget.status === "ACTIVE" ? "stop_circle" : "cancel"
                  }
                />
              </span>
              <div>
                <p className="text-caption font-bold text-error">
                  {cancelTarget.status === "ACTIVE" ? "노출 중단" : "광고 취소"}
                </p>
                <h2
                  id="ad-cancel-title"
                  className="mt-xs font-display-md text-[22px]"
                >
                  정말{" "}
                  {cancelTarget.status === "ACTIVE"
                    ? "노출을 중단"
                    : "광고를 취소"}
                  하시겠습니까?
                </h2>
              </div>
            </div>
            <div className="mt-lg rounded-xl bg-surface-container-low p-md text-caption leading-6 text-on-surface-variant">
              <p className="font-body-strong text-on-surface">
                {eventNames[cancelTarget.eventId] || "선택한 광고"}
              </p>
              {cancelTarget.status === "ACTIVE" ? (
                <>
                  <p className="mt-sm">
                    확인 즉시 메인 화면에서 광고 노출이 중단됩니다.
                  </p>
                  <p className="mt-xs font-body-strong text-error">
                    이미 노출이 시작되어 결제 금액은 환불되지 않습니다.
                  </p>
                </>
              ) : ["PAID", "REVIEW_PENDING", "REJECTED", "SCHEDULED"].includes(
                  cancelTarget.status,
                ) || (cancelTarget.status === "REVISION_PENDING" && new Date(cancelTarget.startAt).getTime() > Date.now()) ? (
                <>
                  <p className="mt-sm">
                    광고 노출은 시작되지 않으며 Toss 결제가 즉시 취소됩니다.
                  </p>
                  <p className="mt-xs font-body-strong text-primary">
                    결제 금액{" "}
                    {Number(
                      cancelTarget.paymentOrder?.totalAmount || 0,
                    ).toLocaleString()}
                    원이 전액 환불됩니다.
                  </p>
                </>
              ) : cancelTarget.status === "REVISION_PENDING" ? (
                <div className="space-y-sm rounded-xl bg-amber-50 p-md text-caption leading-6 text-amber-900">
                  <p>수정 재심사를 취소하면 광고 노출이 중단됩니다.</p>
                  <p>이미 노출이 시작된 광고이므로 결제 금액은 환불되지 않습니다.</p>
                </div>
              ) : (
                <p className="mt-sm">
                  아직 결제되지 않은 신청이며 결제 주문과 광고 신청을
                  취소합니다.
                </p>
              )}
              <p className="mt-sm text-ink-muted">
                처리 후 이 광고는 종료 상태로 남으며, 신청 내역의 X 버튼으로
                목록에서 숨길 수 있습니다.
              </p>
            </div>
            <div className="mt-lg flex justify-end gap-sm">
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => setCancelTarget(null)}
                className="rounded-full border border-hairline px-lg py-sm disabled:opacity-50"
              >
                돌아가기
              </button>
              <button
                type="button"
                disabled={actionLoading}
                onClick={confirmCancel}
                className="rounded-full bg-error px-lg py-sm font-body-strong text-white disabled:opacity-50"
              >
                {actionLoading
                  ? "처리 중..."
                  : cancelTarget.status === "ACTIVE"
                    ? "노출 중단"
                    : [
                          "PAID",
                          "REVIEW_PENDING",
                          "REJECTED",
                          "SCHEDULED",
                        ].includes(cancelTarget.status)
                      ? "취소하고 전액 환불"
                      : "광고 취소"}
              </button>
            </div>
          </section>
        </div>
      )}
      {archiveTarget && (
        <div
          className="fixed inset-0 z-[200] grid place-items-center bg-black/55 p-lg"
          onMouseDown={(event) =>
            event.target === event.currentTarget &&
            !actionLoading &&
            setArchiveTarget(null)
          }
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="ad-archive-title"
            className="w-full max-w-md rounded-2xl bg-white p-xl shadow-2xl"
          >
            <p className="text-caption font-bold text-primary">
              신청 내역 정리
            </p>
            <h2
              id="ad-archive-title"
              className="mt-xs font-display-md text-[22px]"
            >
              이 광고를 목록에서 숨길까요?
            </h2>
            <p className="mt-md text-body leading-7 text-on-surface-variant">
              광고 카드는 신청 내역에서 사라지지만 결제·환불 내역과 운영 감사
              기록은 안전하게 유지됩니다. 실제 데이터 삭제가 아닙니다.
            </p>
            <div className="mt-lg flex justify-end gap-sm">
              <button
                type="button"
                disabled={actionLoading}
                onClick={() => setArchiveTarget(null)}
                className="rounded-full border border-hairline px-lg py-sm disabled:opacity-50"
              >
                취소
              </button>
              <button
                type="button"
                disabled={actionLoading}
                onClick={confirmArchive}
                className="rounded-full bg-on-surface px-lg py-sm font-body-strong text-white disabled:opacity-50"
              >
                {actionLoading ? "처리 중..." : "목록에서 숨기기"}
              </button>
            </div>
          </section>
        </div>
      )}
    </>
  );
}
