import { useEffect, useRef, useState } from "react";
import { ANONYMOUS, loadTossPayments } from "@tosspayments/tosspayments-sdk";
import { Link, useParams } from "react-router-dom";
import { ApiError } from "../api/apiClient.js";
import { eventApi } from "../api/eventApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import { listContents } from "../api/contentApi.js";
import { listPublicVenueMaps } from "../api/venueMapApi.js";
import { listAllPublicBooths } from "../api/boothApi.js";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import TopNav from "../components/TopNav.jsx";
import FileDownloadLink from "../components/FileDownloadLink.jsx";
import VenueMapPins from "../components/VenueMapPins.jsx";
import BoothPinPopup from "../components/BoothPinPopup.jsx";
import TicketLinkEventDetail from "../components/TicketLinkEventDetail.jsx";
import useAuth from "../hooks/useAuth.js";
import useFunnelTracking, { resolveSessionId } from "../hooks/useFunnelTracking.js";
import {
  admissionRetryDelayMs,
  isTicketOrderAdmissionRejected,
  MAX_ADMISSION_RETRIES,
} from "../utils/ticketOrderAdmissionRetry.js";

const formatDateTime = (value) =>
  value
    ? new Date(value).toLocaleString("ko-KR", {
        dateStyle: "long",
        timeStyle: "short",
      })
    : "미정";

const formatDate = (value) =>
  value
    ? new Date(value).toLocaleDateString("ko-KR", {
        year: "numeric",
        month: "long",
        day: "numeric",
      })
    : "미정";

const getEventProgress = (startAt, endAt) => {
  const now = Date.now();
  const start = startAt ? new Date(startAt).getTime() : null;
  const end = endAt ? new Date(endAt).getTime() : null;
  if (start && now < start)
    return { label: "진행 예정", tone: "bg-white/15 text-white" };
  if (end && now > end)
    return { label: "행사 종료", tone: "bg-black/20 text-white/80" };
  return { label: "진행 중", tone: "bg-status-available text-white" };
};

const getEventDuration = (startAt, endAt) => {
  if (!startAt || !endAt) return "일정 확인";
  const days = Math.max(
    1,
    Math.ceil((new Date(endAt) - new Date(startAt)) / 86400000) + 1,
  );
  return `${days.toLocaleString("ko-KR")}일간`;
};

const emptyBuyer = { name: "", email: "", phone: "" };
const OPERATION_CUTOFF_MS = 60 * 60 * 1000;
const PAYMENT_METHODS = {
  CARD: "CARD",
  VIRTUAL_ACCOUNT: "VIRTUAL_ACCOUNT",
};
const TOSS_VIRTUAL_ACCOUNT_TIME_ZONE = "Asia/Seoul";
const PURCHASE_RETRYING_MESSAGE = "현재 주문 요청이 많습니다. 다시 시도하는 중이니 잠시만 기다려주세요.";
const PURCHASE_BUSY_MESSAGE = "현재 주문 요청이 많습니다. 잠시 후 다시 시도해 주세요.";

const formatTossVirtualAccountDueDate = (expiresAt) => {
  if (!expiresAt) return undefined;
  const date = new Date(expiresAt);
  if (Number.isNaN(date.getTime())) return undefined;
  const parts = new Intl.DateTimeFormat("en-CA", {
    timeZone: TOSS_VIRTUAL_ACCOUNT_TIME_ZONE,
    year: "numeric",
    month: "2-digit",
    day: "2-digit",
    hour: "2-digit",
    minute: "2-digit",
    second: "2-digit",
    hour12: false,
    hourCycle: "h23",
  }).formatToParts(date).reduce((acc, part) => {
    if (part.type !== "literal") acc[part.type] = part.value;
    return acc;
  }, {});
  return `${parts.year}-${parts.month}-${parts.day}T${parts.hour}:${parts.minute}:${parts.second}`;
};

const effectiveTicketSalesEndTime = (event) => {
  const operationCutoffTime = event?.endAt
    ? new Date(event.endAt).getTime() - OPERATION_CUTOFF_MS
    : null;
  const configuredSalesEndTime = event?.ticketSalesEndAt
    ? new Date(event.ticketSalesEndAt).getTime()
    : null;
  if (operationCutoffTime == null) return configuredSalesEndTime;
  if (configuredSalesEndTime == null) return operationCutoffTime;
  return Math.min(configuredSalesEndTime, operationCutoffTime);
};

const isTicketSalesEnded = (event, now = Date.now()) => {
  const effectiveSalesEndTime = effectiveTicketSalesEndTime(event);
  return effectiveSalesEndTime != null && now >= effectiveSalesEndTime;
};

export default function EventDetail() {
  const { eventId } = useParams();
  const { isAuthenticated } = useAuth();
  const trackFunnelAction = useFunnelTracking(eventId, "VIEW_EVENT_DETAIL");
  const [event, setEvent] = useState(null);
  const [currentTime, setCurrentTime] = useState(Date.now());
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [purchaseOpen, setPurchaseOpen] = useState(false);
  const [quantity, setQuantity] = useState(1);
  const [buyer, setBuyer] = useState(emptyBuyer);
  const [paymentMethod, setPaymentMethod] = useState(PAYMENT_METHODS.CARD);
  const [purchasing, setPurchasing] = useState(false);
  const [purchaseError, setPurchaseError] = useState("");
  const [purchaseInfo, setPurchaseInfo] = useState("");
  const [ticketOrderIdempotencyKey, setTicketOrderIdempotencyKey] = useState("");
  const [issuedCodes, setIssuedCodes] = useState([]);
  // 공지·자료 (WBS-199): 권한에 따라 BE가 필터링해 내려준다
  const [contents, setContents] = useState([]);
  const [expandedContentId, setExpandedContentId] = useState(null);
  const [completedOrderNo, setCompletedOrderNo] = useState("");
  const [orderNoCopyMessage, setOrderNoCopyMessage] = useState("");

  const [venueMaps, setVenueMaps] = useState([]);
  const [loadingVenueMaps, setLoadingVenueMaps] = useState(false);
  const [venueMapError, setVenueMapError] = useState("");
  const [selectedMapBooth, setSelectedMapBooth] = useState(null);

  // 부스 혼잡도 기반 추천 데이터
  const [recommendedBooths, setRecommendedBooths] = useState(null);
  const [loadingRecommendation, setLoadingRecommendation] = useState(false);
  const [recommendationError, setRecommendationError] = useState("");
  const [boothsMap, setBoothsMap] = useState({});
  const retryTimerRef = useRef(null);
  const purchaseRunRef = useRef(0);
  const purchaseAbortControllerRef = useRef(null);

  const clearPendingTicketOrderRetry = () => {
    if (retryTimerRef.current !== null) {
      window.clearTimeout(retryTimerRef.current);
      retryTimerRef.current = null;
    }
  };

  const cancelTicketOrderAttempt = () => {
    purchaseRunRef.current += 1;
    clearPendingTicketOrderRetry();
    purchaseAbortControllerRef.current?.abort();
    purchaseAbortControllerRef.current = null;
  };

  const waitForTicketOrderRetry = (delayMs, runId) => new Promise((resolve, reject) => {
    clearPendingTicketOrderRetry();
    retryTimerRef.current = window.setTimeout(() => {
      retryTimerRef.current = null;
      if (purchaseRunRef.current !== runId) {
        reject(new DOMException("Ticket order retry cancelled.", "AbortError"));
        return;
      }
      resolve();
    }, delayMs);
  });

  const createTicketOrderWithAdmissionRetry = async (payload, idempotencyKey, runId, signal) => {
    for (let attempt = 0; attempt <= MAX_ADMISSION_RETRIES; attempt += 1) {
      try {
        return await eventApi.createTicketOrder(eventId, payload, idempotencyKey, { signal });
      } catch (requestError) {
        if (!isTicketOrderAdmissionRejected(requestError) || attempt >= MAX_ADMISSION_RETRIES) {
          throw requestError;
        }
        setPurchaseInfo(PURCHASE_RETRYING_MESSAGE);
        await waitForTicketOrderRetry(admissionRetryDelayMs(requestError, attempt + 1), runId);
      }
    }
    return null;
  };

  useEffect(() => () => {
    cancelTicketOrderAttempt();
  }, []);
  const [booths, setBooths] = useState([]);
  const [loadingBooths, setLoadingBooths] = useState(false);
  const [boothError, setBoothError] = useState("");
  const [detailImages, setDetailImages] = useState([]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    setEvent(null);
    eventApi
      .detail(eventId)
      .then((result) => !cancelled && setEvent(result?.data || null))
      .catch(
        (requestError) =>
          !cancelled &&
          setError(requestError.message || "행사를 불러오지 못했습니다."),
      )
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [eventId]);

  useEffect(() => {
    let cancelled = false;
    setDetailImages([]);
    eventApi
      .detailImages(eventId)
      .then((result) => !cancelled && setDetailImages(result?.data || []))
      .catch(() => !cancelled && setDetailImages([]));
    return () => {
      cancelled = true;
    };
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
        // pinned가 undefined면 뺄셈 결과가 NaN이 되므로 boolean → 숫자로 정규화
        list.sort(
          (a, b) =>
            Number(Boolean(b.pinned)) - Number(Boolean(a.pinned)) ||
            new Date(b.publishedAt || 0) - new Date(a.publishedAt || 0),
        );
        setContents(list);
      })
      .catch(() => {
        if (!cancelled) setContents([]);
      });
    return () => {
      cancelled = true;
    };
  }, [eventId]);

  useEffect(() => {
    if (!eventId || !event?.venueMapEnabled) return;
    let cancelled = false;
    setVenueMaps([]);
    setVenueMapError("");
    setLoadingVenueMaps(true);
    listPublicVenueMaps(eventId, "VISITOR")
      .then((data) => {
        if (!cancelled) setVenueMaps(data ?? []);
      })
      .catch((requestError) => {
        if (!cancelled) {
          setVenueMaps([]);
          setVenueMapError(
            requestError instanceof ApiError
              ? requestError.message
              : "평면도를 불러오지 못했습니다.",
          );
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingVenueMaps(false);
      });
    return () => {
      cancelled = true;
    };
  }, [eventId, event?.venueMapEnabled]);

  useEffect(() => {
    const effectiveSalesEndTime = effectiveTicketSalesEndTime(event);
    if (effectiveSalesEndTime == null || Date.now() >= effectiveSalesEndTime)
      return undefined;
    const delay = Math.max(
      1000,
      Math.min(effectiveSalesEndTime - Date.now(), 60000),
    );
    const timerId = window.setTimeout(() => setCurrentTime(Date.now()), delay);
    return () => window.clearTimeout(timerId);
  }, [event, currentTime]);

  useEffect(() => {
    if (!eventId) return;
    let cancelled = false;
    setLoadingBooths(true);
    setBoothError("");
    listAllPublicBooths(eventId)
      .then((data) => {
        if (!cancelled)
          setBooths(
            (data ?? []).filter((booth) => booth.status === "ASSIGNED"),
          );
      })
      .catch((requestError) => {
        if (!cancelled) {
          setBooths([]);
          setBoothError(
            requestError instanceof ApiError
              ? requestError.message
              : "참가 부스 정보를 불러오지 못했습니다.",
          );
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingBooths(false);
      });
    return () => {
      cancelled = true;
    };
  }, [eventId]);

  const openBooth = (booth) => {
    const fullBooth = booths.find(
      (item) => Number(item.id) === Number(booth.boothId ?? booth.id),
    );
    setSelectedMapBooth(fullBooth || booth);
  };

  const submitTicketOrder = async (submitEvent) => {
    submitEvent.preventDefault();
    if (purchasing) return;
    if (isTicketSalesEnded(event)) {
      setCurrentTime(Date.now());
      setPurchaseError("티켓 판매가 종료되었습니다.");
      return;
    }
    const ticketQuantity = Number(quantity);
    const purchaseLimit = event.ticketPurchaseLimit || 1;
    if (
      !Number.isInteger(ticketQuantity) ||
      ticketQuantity < 1 ||
      ticketQuantity > purchaseLimit
    ) {
      setPurchaseError(
        `수량은 1매부터 ${purchaseLimit}매까지 선택할 수 있습니다.`,
      );
      return;
    }
    setPurchasing(true);
    setPurchaseError("");
    setPurchaseInfo("");
    const runId = purchaseRunRef.current + 1;
    purchaseRunRef.current = runId;
    const abortController = new AbortController();
    purchaseAbortControllerRef.current = abortController;
    try {
      const selectedPaymentMethod = isAuthenticated
        ? paymentMethod
        : PAYMENT_METHODS.CARD;
      const payload = {
        quantity: ticketQuantity,
        paymentMethod: selectedPaymentMethod,
        funnelSessionId: resolveSessionId(),
        ...(isAuthenticated
          ? {}
          : {
              buyer: {
                name: buyer.name.trim(),
                email: buyer.email.trim(),
                phone: buyer.phone.trim(),
              },
            }),
      };
      const idempotencyKey = ticketOrderIdempotencyKey || crypto.randomUUID();
      if (!ticketOrderIdempotencyKey) {
        setTicketOrderIdempotencyKey(idempotencyKey);
      }
      const result = await createTicketOrderWithAdmissionRetry(
        payload,
        idempotencyKey,
        runId,
        abortController.signal
      );
      const order = result?.data;
      if (!order) throw new Error("티켓 주문 정보를 받지 못했습니다.");
      if (purchaseRunRef.current !== runId) {
        return;
      }

      setPurchaseInfo("");
      setCompletedOrderNo(order.orderNo || "");
      if (order.orderAccessToken) {
        sessionStorage.setItem(
          `ticket-order-token:${order.orderNo}`,
          order.orderAccessToken,
        );
      }

      if (!order.paymentRequired) {
        setIssuedCodes(order.exchangeCodes || []);
        setTicketOrderIdempotencyKey("");
        return;
      }

      const clientKey = import.meta.env.VITE_TOSS_CLIENT_KEY;
      if (!clientKey)
        throw new Error(
          "VITE_TOSS_CLIENT_KEY가 설정되지 않아 결제창을 열 수 없습니다.",
        );
      const tossPayments = await loadTossPayments(clientKey);
      const payment = tossPayments.payment({ customerKey: ANONYMOUS });
      const paymentRequest = {
        method: selectedPaymentMethod,
        amount: { currency: "KRW", value: Number(order.totalAmount) },
        orderId: order.orderNo,
        orderName: `${event.name} 티켓`,
        successUrl: `${window.location.origin}/tickets/payment/success?eventId=${eventId}`,
        failUrl: `${window.location.origin}/tickets/payment/fail?eventId=${eventId}`,
        ...(!isAuthenticated
          ? {
              customerName: buyer.name.trim(),
              customerEmail: buyer.email.trim(),
              customerMobilePhone: buyer.phone.replace(/[^0-9]/g, ""),
            }
          : {}),
        ...(selectedPaymentMethod === PAYMENT_METHODS.VIRTUAL_ACCOUNT
          ? {
              virtualAccount: {
                dueDate: formatTossVirtualAccountDueDate(order.expiresAt),
              },
            }
          : {
              card: {
                useEscrow: false,
                flowMode: "DEFAULT",
                useCardPoint: false,
                useAppCardOnly: false,
              },
            }),
      };
      await payment.requestPayment(paymentRequest);
    } catch (requestError) {
      if (requestError?.name === "AbortError" || purchaseRunRef.current !== runId) {
        return;
      }
      setPurchaseInfo("");
      setPurchaseError(isTicketOrderAdmissionRejected(requestError)
        ? PURCHASE_BUSY_MESSAGE
        : requestError.message || "티켓 구매를 시작하지 못했습니다.");
    } finally {
      if (purchaseRunRef.current === runId) {
        setPurchasing(false);
        purchaseAbortControllerRef.current = null;
      }
    }
  };

  const closePurchase = () => {
    cancelTicketOrderAttempt();
    setPurchasing(false);
    setPurchaseOpen(false);
    setPurchaseError("");
    setPurchaseInfo("");
    setIssuedCodes([]);
    setCompletedOrderNo("");
    setOrderNoCopyMessage("");
    setTicketOrderIdempotencyKey("");
    setPaymentMethod(PAYMENT_METHODS.CARD);
  };

  const copyCompletedOrderNo = async () => {
    if (!completedOrderNo) return;
    try {
      await navigator.clipboard.writeText(completedOrderNo);
      setOrderNoCopyMessage("주문번호를 복사했습니다.");
    } catch {
      setOrderNoCopyMessage(
        "복사하지 못했습니다. 주문번호를 직접 선택해 복사해 주세요.",
      );
    }
  };

  const openPurchase = () => {
    if (isTicketSalesEnded(event)) {
      setCurrentTime(Date.now());
      setPurchaseError("티켓 판매가 종료되었습니다.");
      return;
    }
    setPurchaseError("");
    setPurchaseInfo("");
    setPaymentMethod(PAYMENT_METHODS.CARD);
    setTicketOrderIdempotencyKey(crypto.randomUUID());
    setPurchaseOpen(true);
    trackFunnelAction("OPEN_PURCHASE_MODAL");
  };

  const now = currentTime;
  const salesNotStarted =
    event?.ticketSalesStartAt &&
    new Date(event.ticketSalesStartAt).getTime() > now;
  const effectiveSalesEndTime = effectiveTicketSalesEndTime(event);
  const salesEnded =
    effectiveSalesEndTime != null && now >= effectiveSalesEndTime;
  const ticketButtonLabel = salesNotStarted
    ? `${formatDateTime(event.ticketSalesStartAt)} 판매 시작`
    : salesEnded
      ? "티켓 판매 종료"
      : "티켓 구매하기";
  const eventProgress = event
    ? getEventProgress(event.startAt, event.endAt)
    : null;
  const featureCards = event
    ? [
        event.boothRecruitmentEnabled && {
          icon: "storefront",
          title: "부스 모집",
          description: "참가 기업과 브랜드의 부스가 함께합니다.",
        },
        event.venueMapEnabled && {
          icon: "map",
          title: "행사장 안내",
          description: "평면도에서 부스 위치를 한눈에 확인하세요.",
          action: () =>
            document
              .getElementById("venue-map")
              ?.scrollIntoView({ behavior: "smooth" }),
        },
        event.boothReservationEnabled && {
          icon: "event_available",
          title: "부스 예약",
          description: "관심 있는 부스의 방문 시간을 예약할 수 있습니다.",
        },
      ].filter(Boolean)
    : [];

  return (
    <div className="bg-surface min-h-screen text-on-surface">
      <TopNav active="events" />
      <main className="pt-[76px] pb-xxl">
        {loading && (
          <p className="mx-auto max-w-[1080px] px-lg py-xxl text-center text-ink-muted">
            행사 정보를 불러오는 중입니다.
          </p>
        )}
        {error && (
          <div className="mx-auto max-w-[1080px] rounded-xl border border-error/20 bg-error/10 p-lg text-error">
            {error}
            <Link to="/" className="ml-sm underline">
              행사 목록으로
            </Link>
          </div>
        )}
        {event && (
          <TicketLinkEventDetail
            event={event}
            detailImages={detailImages}
            booths={booths}
            loadingBooths={loadingBooths}
            boothError={boothError}
            venueMaps={venueMaps}
            loadingVenueMaps={loadingVenueMaps}
            venueMapError={venueMapError}
            contents={contents}
            onBoothClick={openBooth}
            onPurchase={openPurchase}
            purchaseDisabled={salesNotStarted || salesEnded}
            purchaseLabel={ticketButtonLabel}
          />
        )}
        {false && event && (
          <>
            <section className="relative overflow-hidden rounded-2xl text-white bg-gradient-to-br from-primary-focus via-primary to-secondary mb-xl shadow-xl shadow-primary/10">
              {event.representativeFileId && (
                <div aria-hidden="true" className="absolute inset-0 opacity-20">
                  <img
                    src={fileDownloadUrl(event.representativeFileId)}
                    alt=""
                    className="w-full h-full object-cover blur-2xl scale-110"
                  />
                  <div className="absolute inset-0 bg-gradient-to-r from-primary-focus/90 to-secondary/80" />
                </div>
              )}
              <div
                className={`relative grid ${event.representativeFileId ? "md:grid-cols-[280px_1fr]" : "md:grid-cols-1"}`}
              >
                {event.representativeFileId && (
                  <div className="bg-black/10 p-md md:p-lg">
                    <img
                      src={fileDownloadUrl(event.representativeFileId)}
                      alt={`${event.name} 포스터`}
                      onError={(imageEvent) => {
                        imageEvent.currentTarget.style.display = "none";
                      }}
                      className="w-full max-w-[260px] mx-auto aspect-[3/4] object-cover rounded-xl shadow-2xl"
                    />
                  </div>
                )}
                <div className="p-xl md:p-xxl flex flex-col justify-center">
                  <div className="flex flex-wrap items-center gap-sm mb-md">
                    <span
                      className={`px-sm py-xxs rounded-full text-caption font-medium ${eventProgress.tone}`}
                    >
                      {eventProgress.label}
                    </span>
                    <span className="text-caption text-white/70">
                      {event.eventType}
                    </span>
                  </div>
                  <h1 className="font-display-lg text-[32px] md:text-[42px] mb-sm">
                    {event.name}
                  </h1>
                  <p className="text-white/80 leading-7 max-w-2xl">
                    {event.shortDescription ||
                      "새로운 경험과 만남이 기다리는 행사입니다."}
                  </p>
                  <div className="grid grid-cols-2 lg:grid-cols-3 gap-sm mt-xl pt-lg border-t border-white/20">
                    <div>
                      <p className="text-caption text-white/60">행사 기간</p>
                      <p className="font-body-strong mt-xxs">
                        {getEventDuration(event.startAt, event.endAt)}
                      </p>
                    </div>
                    <div>
                      <p className="text-caption text-white/60">개최 장소</p>
                      <p className="font-body-strong mt-xxs truncate">
                        {event.venueName || "장소 미정"}
                      </p>
                    </div>
                    <div className="hidden lg:block">
                      <p className="text-caption text-white/60">입장 안내</p>
                      <p className="font-body-strong mt-xxs">
                        {Number(event.ticketPrice) === 0
                          ? "무료 입장"
                          : `${Number(event.ticketPrice).toLocaleString("ko-KR")}원`}
                      </p>
                    </div>
                  </div>
                </div>
              </div>
            </section>
            <div className="grid md:grid-cols-[1fr_320px] gap-xl">
              <section className="space-y-xl">
                <div className="bg-white border border-hairline rounded-2xl p-lg md:p-xl">
                  <p className="text-caption text-primary font-medium mb-xs">
                    ABOUT EVENT
                  </p>
                  <h2 className="font-display-md text-[24px] mb-md">
                    행사 소개
                  </h2>
                  <p className="whitespace-pre-wrap leading-8 text-on-surface-variant">
                    {event.description ||
                      event.shortDescription ||
                      "행사에 대한 자세한 내용은 현장에서 확인하실 수 있습니다."}
                  </p>
                </div>
                {featureCards.length > 0 && (
                  <div>
                    <div className="flex items-end justify-between gap-md mb-md">
                      <div>
                        <p className="text-caption text-primary font-medium mb-xs">
                          EXPLORE
                        </p>
                        <h2 className="font-display-md text-[24px]">
                          행사 이용 안내
                        </h2>
                      </div>
                      <span className="text-caption text-ink-muted">
                        제공 기능 {featureCards.length}개
                      </span>
                    </div>
                    <div className="grid sm:grid-cols-2 gap-sm">
                      {featureCards.map((feature) => {
                        const CardTag = feature.action ? "button" : "div";
                        return (
                          <CardTag
                            key={feature.title}
                            type={feature.action ? "button" : undefined}
                            onClick={feature.action}
                            className={`group text-left bg-white border border-hairline rounded-2xl p-lg transition ${feature.action ? "hover:border-primary hover:shadow-lg cursor-pointer" : ""}`}
                          >
                            <span className="w-11 h-11 rounded-xl bg-primary/10 text-primary grid place-items-center mb-md group-hover:bg-primary group-hover:text-white transition">
                              <Icon name={feature.icon} />
                            </span>
                            <h3 className="font-body-strong mb-xs">
                              {feature.title}
                            </h3>
                            <p className="text-caption text-ink-muted leading-6">
                              {feature.description}
                            </p>
                          </CardTag>
                        );
                      })}
                    </div>
                  </div>
                )}

                {/* 공지·자료 (WBS-199): 제목 클릭 시 내용 펼침, 첨부는 다운로드 링크 */}
                {contents.length > 0 && (
                  <div>
                    <h2 className="font-display-md text-[22px] mb-md">
                      공지 · 자료
                    </h2>
                    <div className="bg-white border border-hairline rounded-2xl divide-y divide-divider-soft overflow-hidden">
                      {contents.map((content) => (
                        <div key={content.contentId}>
                          <button
                            onClick={() =>
                              setExpandedContentId(
                                expandedContentId === content.contentId
                                  ? null
                                  : content.contentId,
                              )
                            }
                            className="w-full flex items-center gap-sm p-lg text-left hover:bg-surface-pearl/50 transition-colors"
                          >
                            <Icon
                              name={
                                content.contentType === "NOTICE"
                                  ? "campaign"
                                  : "folder"
                              }
                              className="text-[18px] text-ink-muted flex-shrink-0"
                            />
                            <div className="flex-1 min-w-0">
                              <p className="font-body-strong text-[14px] truncate">
                                {content.pinned && (
                                  <Icon
                                    name="push_pin"
                                    className="text-[13px] text-primary mr-1"
                                  />
                                )}
                                {content.title}
                              </p>
                              <p className="text-caption text-ink-muted">
                                {content.contentType === "NOTICE"
                                  ? "공지"
                                  : "자료"}
                                {content.version
                                  ? ` · v${content.version}`
                                  : ""}
                                {content.publishedAt
                                  ? ` · ${new Date(content.publishedAt).toLocaleDateString("ko-KR")}`
                                  : ""}
                              </p>
                            </div>
                            <Icon
                              name={
                                expandedContentId === content.contentId
                                  ? "expand_less"
                                  : "expand_more"
                              }
                              className="text-ink-muted text-[18px]"
                            />
                          </button>
                          {expandedContentId === content.contentId && (
                            <div className="px-lg pb-lg space-y-sm">
                              {content.content && (
                                <p className="text-caption whitespace-pre-line bg-surface-pearl rounded-lg p-md">
                                  {content.content}
                                </p>
                              )}
                              {/* fileName·fileSize: BE Summary에 포함된 원본 파일명·크기 (다운로드 파일명으로 사용) */}
                              {content.fileId && (
                                <FileDownloadLink
                                  fileId={content.fileId}
                                  fileName={content.fileName || "첨부파일"}
                                  fileSize={content.fileSize}
                                />
                              )}
                            </div>
                          )}
                        </div>
                      ))}
                    </div>
                  </div>
                )}
                {event.venueMapEnabled && (
                  <div id="venue-map" className="scroll-mt-24">
                    <h2 className="font-display-md text-[22px] mb-md">
                      행사장 배치도
                    </h2>
                    {loadingVenueMaps && (
                      <p className="text-caption text-ink-muted">
                        평면도를 불러오는 중입니다.
                      </p>
                    )}
                    {venueMapError && (
                      <p className="text-caption text-error">{venueMapError}</p>
                    )}
                    {!loadingVenueMaps &&
                      !venueMapError &&
                      venueMaps.length === 0 && (
                        <p className="text-caption text-ink-muted">
                          등록된 평면도가 없습니다.
                        </p>
                      )}
                    {!loadingVenueMaps && venueMaps.length > 0 && (
                      <div className="space-y-lg">
                        {venueMaps.map((venueMap) => (
                          <div key={venueMap.id}>
                            <h3 className="font-body-strong text-body mb-sm">
                              {venueMap.floorName}
                            </h3>
                            <div className="bg-surface-pearl border border-hairline rounded-2xl p-lg">
                              <VenueMapPins
                                venueMap={venueMap}
                                onPinClick={openBooth}
                              />
                            </div>
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
                <div id="participating-booths" className="scroll-mt-24">
                  <div className="flex items-end justify-between gap-md mb-md">
                    <div>
                      <p className="text-caption text-primary font-medium mb-xs">
                        EXHIBITORS
                      </p>
                      <h2 className="font-display-md text-[22px]">참가 부스</h2>
                    </div>
                    {!loadingBooths && (
                      <span className="text-caption text-ink-muted">
                        {booths.length}개 부스
                      </span>
                    )}
                  </div>
                  {loadingBooths && (
                    <p className="text-caption text-ink-muted">
                      참가 부스를 불러오는 중입니다.
                    </p>
                  )}
                  {boothError && (
                    <p className="text-caption text-error">{boothError}</p>
                  )}
                  {!loadingBooths && !boothError && booths.length === 0 && (
                    <div className="rounded-2xl border border-dashed border-hairline bg-white p-xl text-center text-ink-muted">
                      아직 공개된 참가 부스 소개가 없습니다.
                    </div>
                  )}
                  {booths.length > 0 && (
                    <div className="grid sm:grid-cols-2 gap-md">
                      {booths.map((booth) => (
                        <button
                          key={booth.id}
                          type="button"
                          onClick={() => openBooth(booth)}
                          className="group overflow-hidden rounded-2xl border border-hairline bg-white text-left transition hover:-translate-y-0.5 hover:border-primary hover:shadow-lg"
                        >
                          <div className="aspect-[16/9] bg-surface-container overflow-hidden">
                            {booth.representativeFileId ? (
                              <img
                                src={fileDownloadUrl(
                                  booth.representativeFileId,
                                )}
                                alt={`${booth.displayName || booth.boothCode} 대표 이미지`}
                                className="w-full h-full object-cover transition duration-300 group-hover:scale-105"
                              />
                            ) : (
                              <div className="h-full grid place-items-center text-ink-muted">
                                <Icon
                                  name="storefront"
                                  className="text-[36px]"
                                />
                              </div>
                            )}
                          </div>
                          <div className="p-lg">
                            <p className="text-caption font-bold text-primary">
                              {booth.boothCode} ·{" "}
                              {[booth.floorName, booth.zoneName]
                                .filter(Boolean)
                                .join(" · ") || "위치 안내 예정"}
                            </p>
                            <h3 className="font-display-md text-[18px] mt-xs">
                              {booth.displayName || booth.boothCode}
                            </h3>
                            <p className="mt-sm text-caption text-on-surface-variant line-clamp-2">
                              {booth.shortIntro ||
                                booth.description ||
                                "부스를 눌러 상세 소개와 전시·판매 내용을 확인하세요."}
                            </p>
                            {booth.exhibitionContent && (
                              <p className="mt-md pt-md border-t border-hairline text-caption text-ink-muted line-clamp-2">
                                <strong className="text-on-surface">
                                  전시·판매
                                </strong>{" "}
                                {booth.exhibitionContent}
                              </p>
                            )}
                          </div>
                        </button>
                      ))}
                    </div>
                  )}
                </div>
              </section>
              <aside className="bg-white border border-hairline rounded-2xl p-lg h-fit space-y-md md:sticky md:top-[92px] shadow-sm">
                <div>
                  <p className="text-caption text-primary font-medium mb-xs">
                    EVENT INFO
                  </p>
                  <h2 className="font-display-md text-[22px]">관람 정보</h2>
                </div>
                <p className="flex gap-sm">
                  <span className="w-10 h-10 shrink-0 rounded-xl bg-primary/10 text-primary grid place-items-center">
                    <Icon name="calendar_month" />
                  </span>
                  <span>
                    <strong className="block text-caption mb-xxs">
                      행사 일정
                    </strong>
                    {formatDateTime(event.startAt)}
                    <br />
                    <span className="text-caption text-ink-muted">
                      ~ {formatDateTime(event.endAt)}
                    </span>
                  </span>
                </p>
                <p className="flex gap-sm">
                  <span className="w-10 h-10 shrink-0 rounded-xl bg-primary/10 text-primary grid place-items-center">
                    <Icon name="location_on" />
                  </span>
                  <span>
                    <strong className="block text-caption mb-xxs">
                      행사 장소
                    </strong>
                    {event.venueName || "장소 미정"}
                    <br />
                    <span className="text-caption text-ink-muted">
                      {event.address}
                    </span>
                  </span>
                </p>
                <div className="border-t border-hairline pt-md">
                  <p className="text-caption text-ink-muted">입장 가격</p>
                  <p className="font-display-md text-[22px]">
                    {Number(event.ticketPrice) === 0
                      ? "무료"
                      : `${Number(event.ticketPrice).toLocaleString("ko-KR")}원`}
                  </p>
                </div>
                {(event.contactEmail || event.contactPhone) && (
                  <div className="border-t border-hairline pt-md space-y-sm">
                    <p className="text-caption text-ink-muted">행사 문의</p>
                    {event.contactEmail && (
                      <a
                        href={`mailto:${event.contactEmail}`}
                        className="flex gap-sm items-center text-body hover:text-primary"
                      >
                        <Icon name="mail" />
                        {event.contactEmail}
                      </a>
                    )}
                    {event.contactPhone && (
                      <a
                        href={`tel:${event.contactPhone}`}
                        className="flex gap-sm items-center text-body hover:text-primary"
                      >
                        <Icon name="call" />
                        {event.contactPhone}
                      </a>
                    )}
                  </div>
                )}
                <button
                  disabled={salesNotStarted || salesEnded}
                  onClick={openPurchase}
                  className="w-full py-sm bg-primary text-white rounded-full font-body-strong disabled:bg-surface-container-highest disabled:text-ink-muted disabled:cursor-not-allowed"
                >
                  {ticketButtonLabel}
                </button>
                <p className="text-center text-caption text-ink-muted">
                  {formatDate(event.startAt)}부터 관람할 수 있어요.
                </p>
              </aside>
            </div>
          </>
        )}
      </main>
      <Footer />
      {purchaseOpen && event && (
        <div
          className="fixed inset-0 z-50 bg-black/50 grid place-items-center p-lg"
          onMouseDown={(e) => e.target === e.currentTarget && closePurchase()}
        >
          <section
            role="dialog"
            aria-modal="true"
            aria-labelledby="ticket-title"
            className="w-full max-w-md bg-white rounded-2xl p-xl shadow-2xl"
          >
            <div className="flex items-start justify-between gap-md mb-lg">
              <div>
                <p className="text-caption text-primary">TICKET</p>
                <h2 id="ticket-title" className="font-display-md text-[24px]">
                  {issuedCodes.length ? "예매 완료" : "티켓 구매"}
                </h2>
              </div>
              <button type="button" onClick={closePurchase} aria-label="닫기">
                <Icon name="close" />
              </button>
            </div>
            {issuedCodes.length ? (
              <div className="space-y-md">
                <p>무료 티켓이 발급되었습니다.</p>
                {!isAuthenticated && completedOrderNo && (
                  <div className="rounded-xl border border-primary/20 bg-primary/5 p-md text-left">
                    <p className="text-caption text-ink-muted">주문번호</p>
                    <p className="mt-xs break-all font-mono text-lg font-bold text-on-surface">
                      {completedOrderNo}
                    </p>
                    <button
                      type="button"
                      onClick={copyCompletedOrderNo}
                      className="mt-sm rounded-full border border-hairline px-md py-1.5 text-caption font-body-strong"
                    >
                      주문번호 복사
                    </button>
                    {orderNoCopyMessage && (
                      <p className="mt-xs text-caption text-primary">
                        {orderNoCopyMessage}
                      </p>
                    )}
                    <p className="mt-sm text-caption text-ink-muted">
                      ※ 비회원 예매 조회 시 필요한 번호입니다.
                      <br />
                      구매 시 입력한 이메일로 주문번호 안내 메일 발송을
                      시도합니다.
                      <br />
                      메일을 받지 못할 수 있으니 현재 주문번호를 보관해주세요.
                    </p>
                  </div>
                )}
                {issuedCodes.map((item, index) => (
                  <div
                    key={item.exchangeCode || index}
                    className="rounded-xl bg-surface-container p-md"
                  >
                    <p className="text-caption text-ink-muted">
                      입장 코드 {index + 1}
                    </p>
                    <p className="font-mono font-bold text-lg break-all">
                      {item.exchangeCode || item.code}
                    </p>
                  </div>
                ))}
                {completedOrderNo && (
                  <Link
                    to={`/tickets/orders/${completedOrderNo}`}
                    className="block w-full py-sm border border-hairline rounded-full text-center"
                  >
                    주문 상세 보기
                  </Link>
                )}
                <button
                  type="button"
                  onClick={closePurchase}
                  className="w-full py-sm bg-primary text-white rounded-full"
                >
                  확인
                </button>
              </div>
            ) : (
              <form onSubmit={submitTicketOrder} className="space-y-md">
                <div className="rounded-xl bg-surface-container p-md">
                  <p className="font-body-strong">{event.name}</p>
                  <p className="text-caption text-ink-muted">
                    1매{" "}
                    {Number(event.ticketPrice) === 0
                      ? "무료"
                      : `${Number(event.ticketPrice).toLocaleString("ko-KR")}원`}
                  </p>
                </div>
                <label className="block">
                  수량
                  <input
                    required
                    min="1"
                    max={event.ticketPurchaseLimit || 1}
                    type="number"
                    value={quantity}
                    onChange={(e) => setQuantity(e.target.value)}
                    className="mt-xs w-full h-11 border border-hairline rounded-lg px-md"
                  />
                </label>
                {isAuthenticated && Number(event.ticketPrice) > 0 && (
                  <fieldset className="space-y-sm border-t border-hairline pt-md">
                    <legend className="text-caption text-ink-muted">
                      결제수단
                    </legend>
                    <div className="mt-sm grid grid-cols-2 gap-sm">
                      {[
                        {
                          value: PAYMENT_METHODS.CARD,
                          label: "카드",
                          icon: "credit_card",
                        },
                        {
                          value: PAYMENT_METHODS.VIRTUAL_ACCOUNT,
                          label: "가상계좌",
                          icon: "account_balance",
                        },
                      ].map((method) => (
                        <button
                          key={method.value}
                          type="button"
                          onClick={() => setPaymentMethod(method.value)}
                          aria-pressed={paymentMethod === method.value}
                          className={`flex h-12 items-center justify-center gap-xs rounded-lg border text-caption font-body-strong transition ${
                            paymentMethod === method.value
                              ? "border-primary bg-primary/10 text-primary ring-1 ring-primary"
                              : "border-hairline hover:border-primary/50"
                          }`}
                        >
                          <Icon name={method.icon} />
                          {method.label}
                        </button>
                      ))}
                    </div>
                    {paymentMethod === PAYMENT_METHODS.VIRTUAL_ACCOUNT && (
                      <div className="rounded-lg bg-surface-container p-sm text-[12px] leading-relaxed text-ink-muted">
                        <p>가상계좌 발급 후 30분 안에 입금해 주세요.</p>
                        <p>입금이 확인되어야 예매가 확정되며, 미입금 주문은 자동 만료됩니다.</p>
                      </div>
                    )}
                  </fieldset>
                )}
                {!isAuthenticated && (
                  <div className="space-y-md border-t border-hairline pt-md">
                    <p className="text-caption text-ink-muted">
                      비회원 구매 정보
                    </p>
                    <label className="block">
                      이름
                      <input
                        required
                        value={buyer.name}
                        onChange={(e) =>
                          setBuyer({ ...buyer, name: e.target.value })
                        }
                        className="mt-xs w-full h-11 border border-hairline rounded-lg px-md"
                      />
                    </label>
                    <label className="block">
                      이메일
                      <input
                        required
                        type="email"
                        value={buyer.email}
                        onChange={(e) =>
                          setBuyer({ ...buyer, email: e.target.value })
                        }
                        className="mt-xs w-full h-11 border border-hairline rounded-lg px-md"
                      />
                    </label>
                    <label className="block">
                      전화번호
                      <input
                        required
                        placeholder="010-1234-5678"
                        value={buyer.phone}
                        onChange={(e) =>
                          setBuyer({ ...buyer, phone: e.target.value })
                        }
                        className="mt-xs w-full h-11 border border-hairline rounded-lg px-md"
                      />
                    </label>
                  </div>
                )}
                <div className="flex justify-between border-t border-hairline pt-md">
                  <span>결제 금액</span>
                  <strong>
                    {Number(event.ticketPrice) === 0
                      ? "무료"
                      : `${(Number(event.ticketPrice) * Number(quantity || 0)).toLocaleString("ko-KR")}원`}
                  </strong>
                </div>
                {purchaseInfo && (
                  <p className="text-caption text-primary bg-primary/10 rounded-lg p-sm">
                    {purchaseInfo}
                  </p>
                )}

                {purchaseError && (
                  <p className="text-caption text-error bg-error/10 rounded-lg p-sm">
                    {purchaseError}
                  </p>
                )}
                <button
                  disabled={purchasing}
                  className="w-full py-sm bg-primary text-white rounded-full disabled:opacity-50"
                >
                  {purchasing
                    ? "주문 생성 중..."
                    : Number(event.ticketPrice) === 0
                      ? "무료 티켓 받기"
                      : paymentMethod === PAYMENT_METHODS.VIRTUAL_ACCOUNT &&
                          isAuthenticated
                        ? "가상계좌 발급하기"
                        : "카드로 결제하기"}
                </button>
              </form>
            )}
            {issuedCodes.length > 0 && completedOrderNo && !isAuthenticated && (
              <Link
                to={`/guest/orders/${completedOrderNo}`}
                className="mt-md block w-full py-sm border border-hairline rounded-full text-center"
              >
                비회원 예매 관리
              </Link>
            )}
          </section>
        </div>
      )}
      <BoothPinPopup
        booth={selectedMapBooth}
        onClose={() => setSelectedMapBooth(null)}
      />
    </div>
  );
}
