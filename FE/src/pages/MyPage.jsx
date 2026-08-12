import { useCallback, useEffect, useRef, useState } from "react";
import { Link, Navigate } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import TopNav from "../components/TopNav.jsx";
import { admissionApi } from "../api/admissionApi.js";
import { eventApi } from "../api/eventApi.js";
import { exchangeCodeApi } from "../api/exchangeCodeApi.js";
import { paymentApi } from "../api/paymentApi.js";
import { getMyReviews } from "../api/boothReviewApi.js";
import { listMyReservations, cancelReservation } from "../api/boothReservationApi.js";
import useAuth from "../hooks/useAuth.js";
import { useNotifications } from "../notifications/NotificationContext.jsx";

const tabs = [
  { key: "tickets", label: "예매내역", icon: "confirmation_number" },
  { key: "refunds", label: "환불내역", icon: "payments" },
  { key: "qr", label: "입장 QR", icon: "qr_code_2" },
  { key: "boothReservations", label: "부스 예약", icon: "storefront" },
  { key: "notif", label: "알림", icon: "notifications" },
  { key: "profile", label: "회원정보", icon: "person" },
];
const formatDateTime = (value) =>
  value ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-";
const formatMoney = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;
const pageInfo = (result) => ({
  number: result?.data?.number || 0,
  totalPages: result?.data?.totalPages || 1,
});
const orderStatusLabel = {
  PENDING: "대기",
  PAID: "결제 완료",
  CONFIRMED: "확정",
  CANCELLED: "취소",
  EXPIRED: "만료",
  FAILED: "실패",
  REFUNDED: "환불 완료",
};
const refundStatusLabel = {
  REQUESTED: "환불 요청",
  COMPLETED: "환불 완료",
  FAILED: "환불 실패",
  REJECTED: "환불 거절",
};
const exchangeCodeStatusLabel = {
  ISSUED: "사용 전",
  REDEEMED: "사용 완료",
  CANCELLED: "취소",
  EXPIRED: "만료",
};
const exchangeCodeSourceLabel = {
  TICKET_ORDER: "티켓 주문",
  EXTERNAL_REQUEST: "외부 발급",
};

const admissionStatusLabel = {
  ISSUED: "사용 가능",
  USED: "입장 완료",
  CANCELLED: "취소",
  EXPIRED: "만료",
};

const boothReservationStatusLabel = {
  RESERVED: "예약 완료",
  CHECKED_IN: "체크인",
  COMPLETED: "이용 완료",
  NO_SHOW: "노쇼",
  CANCELLED: "취소됨",
};
const boothReservationStatusColor = {
  RESERVED: "status-available",
  CHECKED_IN: "status-pending",
  COMPLETED: "status-assigned",
  NO_SHOW: "status-visited",
  CANCELLED: "status-blocked",
};
const formatSlotRange = (startAt, endAt) => {
  if (!startAt) return "-";
  const start = new Date(startAt);
  const datePart = start.toLocaleDateString("ko-KR", { month: "2-digit", day: "2-digit" });
  const startTime = start.toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
  const endTime = endAt ? new Date(endAt).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" }) : "";
  return `${datePart} ${startTime}${endTime ? `~${endTime}` : ""}`;
};

export default function MyPage() {
  const { member, logout } = useAuth();
  const { openPanel, unreadCount } = useNotifications();
  const isBusinessMember = member.accountType === "BUSINESS";
  const isPlatformAdmin = member.platformRole === "PLATFORM_ADMIN";
  const organizationType = member.organization?.organizationType;
  const isOrganizer = organizationType === "ORGANIZER";
  const isExhibitor = organizationType === "EXHIBITOR";
  const businessActivityLabel = isOrganizer
    ? "행사 관리"
    : isExhibitor
      ? "부스 신청"
      : "활동 관리";
  const businessActivityIcon = isOrganizer
    ? "event"
    : isExhibitor
      ? "storefront"
      : "business_center";
  const businessTabs = [
    { key: "business-overview", label: "사업자 홈", icon: "dashboard" },
    {
      key: "business-activity",
      label: businessActivityLabel,
      icon: businessActivityIcon,
    },
    { key: "profile", label: "회원정보", icon: "person" },
  ];
  const visibleTabs = isBusinessMember
    ? businessTabs
    : tabs;
  const [tab, setTab] = useState(
    isBusinessMember ? "business-overview" : "tickets"
  );
  const [code, setCode] = useState("");
  const [redeemMsg, setRedeemMsg] = useState(null); // { ok, text }
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const [myReviews, setMyReviews] = useState([]);
  const [myReviewsPage, setMyReviewsPage] = useState(0);
  const [myReviewsPageInfo, setMyReviewsPageInfo] = useState({ number: 0, totalPages: 1 });
  const [myReviewsLoading, setMyReviewsLoading] = useState(false);
  const [myReviewsError, setMyReviewsError] = useState("");
  const [boothReservations, setBoothReservations] = useState([]);
  const [boothReservationsPage, setBoothReservationsPage] = useState(0);
  const [boothReservationsPageInfo, setBoothReservationsPageInfo] = useState({ number: 0, totalPages: 1 });
  const [boothReservationsLoading, setBoothReservationsLoading] = useState(false);
  const [boothReservationsError, setBoothReservationsError] = useState("");
  const [boothReservationsReloadToken, setBoothReservationsReloadToken] = useState(0);
  const [cancellingReservationId, setCancellingReservationId] = useState(null);
  const [ticketOrders, setTicketOrders] = useState([]);
  const [ticketOrdersPage, setTicketOrdersPage] = useState(0);
  const [ticketOrdersPageInfo, setTicketOrdersPageInfo] = useState({ number: 0, totalPages: 1 });
  const [ticketOrdersLoading, setTicketOrdersLoading] = useState(false);
  const [ticketOrdersError, setTicketOrdersError] = useState("");
  const [refunds, setRefunds] = useState([]);
  const [refundsPage, setRefundsPage] = useState(0);
  const [refundsPageInfo, setRefundsPageInfo] = useState({ number: 0, totalPages: 1 });
  const [refundsLoading, setRefundsLoading] = useState(false);
  const [refundsError, setRefundsError] = useState("");
  const [exchangeCodes, setExchangeCodes] = useState([]);
  const [exchangeCodesPage, setExchangeCodesPage] = useState(0);
  const [exchangeCodesPageInfo, setExchangeCodesPageInfo] = useState({ number: 0, totalPages: 1 });
  const [exchangeCodesLoading, setExchangeCodesLoading] = useState(false);
  const [exchangeCodesError, setExchangeCodesError] = useState("");
  const [admissionTickets, setAdmissionTickets] = useState([]);
  const [admissionTicketsPage, setAdmissionTicketsPage] = useState(0);
  const [admissionTicketsPageInfo, setAdmissionTicketsPageInfo] = useState({ number: 0, totalPages: 1 });
  const [admissionTicketsLoading, setAdmissionTicketsLoading] = useState(false);
  const [admissionTicketsError, setAdmissionTicketsError] = useState("");
  const [issuedAdmissionTicketId, setIssuedAdmissionTicketId] = useState(null);
  const [admissionEvents, setAdmissionEvents] = useState([]);
  const [admissionEventsLoading, setAdmissionEventsLoading] = useState(false);
  const [validationResult, setValidationResult] = useState(null);
  const [validatedCode, setValidatedCode] = useState("");
  const [validatingCode, setValidatingCode] = useState(false);
  const [redeemingCode, setRedeemingCode] = useState(false);
  const validationSeqRef = useRef(0);
  const loginDescription = isBusinessMember
    ? `${member.organization?.name || "사업자"} · 사업자 계정`
    : "일반 회원";
  const memberEmail = member.email?.endsWith("@oauth.invalid")
    ? "이메일 정보 없음"
    : member.email;

  const organizationTypeLabel = {
    ORGANIZER: "박람회 개최측",
    EXHIBITOR: "부스 참가측",
  }[organizationType] || "조직 정보 없음";

  const organizationRoleLabel = {
    OWNER: "소유자",
    MANAGER: "관리자",
    STAFF: "실무자",
  }[member.organization?.organizationRole] || "권한 정보 없음";

  const loadExchangeCodes = useCallback(() => {
    setExchangeCodesLoading(true);
    setExchangeCodesError("");
    return exchangeCodeApi.getMyExchangeCodes({ page: exchangeCodesPage, size: 20 })
      .then((result) => {
        setExchangeCodes(result?.data?.content || []);
        setExchangeCodesPageInfo(pageInfo(result));
      })
      .catch((requestError) => setExchangeCodesError(requestError.message || "교환 코드 목록을 불러오지 못했습니다."))
      .finally(() => setExchangeCodesLoading(false));
  }, [exchangeCodesPage]);

  const loadAdmissionTickets = useCallback(() => {
    setAdmissionTicketsLoading(true);
    setAdmissionTicketsError("");
    return admissionApi.getMyAdmissionTickets({ page: admissionTicketsPage, size: 20 })
      .then((result) => {
        setAdmissionTickets(result?.data?.content || []);
        setAdmissionTicketsPageInfo(pageInfo(result));
      })
      .catch((requestError) => setAdmissionTicketsError(requestError.message || "입장 티켓 목록을 불러오지 못했습니다."))
      .finally(() => setAdmissionTicketsLoading(false));
  }, [admissionTicketsPage]);

  useEffect(() => {
    const visibleTabKeys = isBusinessMember
      ? ["business-overview", "business-activity", "profile"]
      : tabs.map((item) => item.key);

    if (!visibleTabKeys.includes(tab)) {
      setTab(visibleTabKeys[0]);
    }
  }, [isBusinessMember, tab]);

  useEffect(() => {
    if (isBusinessMember || tab !== "tickets") return;
    let cancelled = false;
    setTicketOrdersLoading(true);
    setTicketOrdersError("");
    paymentApi.getMyTicketOrders({ page: ticketOrdersPage, size: 20 })
      .then((result) => {
        if (!cancelled) {
          setTicketOrders(result?.data?.content || []);
          setTicketOrdersPageInfo(pageInfo(result));
        }
      })
      .catch((requestError) => {
        if (!cancelled) setTicketOrdersError(requestError.message || "예매 내역을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setTicketOrdersLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isBusinessMember, tab, ticketOrdersPage]);

  useEffect(() => {
    if (isBusinessMember || tab !== "tickets") return;
    let cancelled = false;
    setExchangeCodesLoading(true);
    setExchangeCodesError("");
    exchangeCodeApi.getMyExchangeCodes({ page: exchangeCodesPage, size: 20 })
      .then((result) => {
        if (!cancelled) {
          setExchangeCodes(result?.data?.content || []);
          setExchangeCodesPageInfo(pageInfo(result));
        }
      })
      .catch((requestError) => {
        if (!cancelled) setExchangeCodesError(requestError.message || "교환 코드 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setExchangeCodesLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [exchangeCodesPage, isBusinessMember, tab]);

  useEffect(() => {
    if (isBusinessMember || tab !== "refunds") return;
    let cancelled = false;
    setRefundsLoading(true);
    setRefundsError("");
    paymentApi.getMyRefunds({ page: refundsPage, size: 20 })
      .then((result) => {
        if (!cancelled) {
          setRefunds(result?.data?.content || []);
          setRefundsPageInfo(pageInfo(result));
        }
      })
      .catch((requestError) => {
        if (!cancelled) setRefundsError(requestError.message || "환불 내역을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setRefundsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isBusinessMember, refundsPage, tab]);

  useEffect(() => {
    if (isBusinessMember || tab !== "qr") return;
    let cancelled = false;
    setAdmissionTicketsLoading(true);
    setAdmissionTicketsError("");
    admissionApi.getMyAdmissionTickets({ page: admissionTicketsPage, size: 20 })
      .then((result) => {
        if (!cancelled) {
          setAdmissionTickets(result?.data?.content || []);
          setAdmissionTicketsPageInfo(pageInfo(result));
        }
      })
      .catch((requestError) => {
        if (!cancelled) setAdmissionTicketsError(requestError.message || "입장 티켓 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setAdmissionTicketsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [admissionTicketsPage, isBusinessMember, tab]);

  // 내가 작성한 부스 후기 목록. 이 엔드포인트는 다른 마이페이지 API와 달리 ApiResponse({ data: ... })
  // 래핑 없이 Page를 그대로 반환한다.
  useEffect(() => {
    if (isBusinessMember || tab !== "profile") return;
    let cancelled = false;
    setMyReviewsLoading(true);
    setMyReviewsError("");
    getMyReviews({ page: myReviewsPage, size: 10 })
      .then((result) => {
        if (!cancelled) {
          setMyReviews(result?.content || []);
          setMyReviewsPageInfo({ number: result?.number || 0, totalPages: result?.totalPages || 1 });
        }
      })
      .catch((requestError) => {
        if (!cancelled) setMyReviewsError(requestError.message || "작성한 후기를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setMyReviewsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isBusinessMember, myReviewsPage, tab]);

  // 내 부스 예약 목록: 행사 전체에 걸쳐 내가 예약한 모든 부스 예약. ApiResponse 래핑 없음.
  useEffect(() => {
    if (isBusinessMember || tab !== "boothReservations") return;
    let cancelled = false;
    setBoothReservationsLoading(true);
    setBoothReservationsError("");
    listMyReservations({ page: boothReservationsPage, size: 10 })
      .then((result) => {
        if (!cancelled) {
          setBoothReservations(result?.content || []);
          setBoothReservationsPageInfo({ number: result?.number || 0, totalPages: result?.totalPages || 1 });
        }
      })
      .catch((requestError) => {
        if (!cancelled) setBoothReservationsError(requestError.message || "부스 예약 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setBoothReservationsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isBusinessMember, boothReservationsPage, tab, boothReservationsReloadToken]);

  const handleCancelBoothReservation = async (reservation) => {
    if (cancellingReservationId) return;
    if (!window.confirm("예약을 취소하시겠어요?")) return;
    setCancellingReservationId(reservation.id);
    try {
      await cancelReservation(reservation.boothId, reservation.id);
      setBoothReservationsReloadToken((value) => value + 1);
    } catch (requestError) {
      setBoothReservationsError(requestError.message || "예약 취소에 실패했습니다.");
    } finally {
      setCancellingReservationId(null);
    }
  };

  useEffect(() => {
    if (isBusinessMember || isPlatformAdmin) return;
    let cancelled = false;
    setAdmissionEventsLoading(true);
    eventApi.admissionEvents()
      .then((result) => {
        if (!cancelled) setAdmissionEvents(result?.data || []);
      })
      .catch(() => {
        if (!cancelled) setAdmissionEvents([]);
      })
      .finally(() => {
        if (!cancelled) setAdmissionEventsLoading(false);
      });
    return () => {
      cancelled = true;
    };
  }, [isBusinessMember, isPlatformAdmin]);

  const handleLogout = async () => {
    setIsLoggingOut(true);

    try {
      await logout();
    } catch {
      window.alert("로그아웃에 실패했습니다. 다시 시도해 주세요.");
    } finally {
      setIsLoggingOut(false);
    }
  };

  const handleCodeChange = (event) => {
    setCode(event.target.value);
    setValidationResult(null);
    setValidatedCode("");
    setRedeemMsg(null);
    setIssuedAdmissionTicketId(null);
  };

  const validateCode = async () => {
    const val = code.trim();
    if (!val) return setRedeemMsg({ ok: false, text: "교환 코드를 입력해 주세요." });
    if (validatingCode) return;
    const seq = validationSeqRef.current + 1;
    validationSeqRef.current = seq;
    setValidatingCode(true);
    setRedeemMsg(null);
    setValidationResult(null);
    setValidatedCode("");
    try {
      const result = await exchangeCodeApi.validateExchangeCode(val);
      if (validationSeqRef.current !== seq || code.trim() !== val) return;
      const data = result?.data || null;
      if (!data?.valid) {
        setRedeemMsg({ ok: false, text: data?.message || "사용할 수 없는 교환 코드입니다." });
        return;
      }
      setValidationResult(data);
      setValidatedCode(val);
      setRedeemMsg({ ok: true, text: "교환 코드가 확인되었습니다." });
    } catch (requestError) {
      if (validationSeqRef.current === seq && code.trim() === val) {
        setRedeemMsg({ ok: false, text: requestError.message || "교환 코드를 확인하지 못했습니다." });
      }
    } finally {
      if (validationSeqRef.current === seq) setValidatingCode(false);
    }
  };

  const redeemCode = async () => {
    const val = validatedCode;
    if (!validationResult?.valid || validatedCode !== code.trim() || redeemingCode) return;
    setRedeemingCode(true);
    setRedeemMsg(null);
    try {
      const result = await exchangeCodeApi.redeemExchangeCode(val);
      const redeemed = result?.data;
      setValidationResult(null);
      setValidatedCode("");
      setCode("");
      setIssuedAdmissionTicketId(redeemed?.admissionTicketId || null);
      setRedeemMsg({
        ok: true,
        text: redeemed?.admissionTicketId
          ? `입장 티켓이 발급되었습니다. 티켓 ID: ${redeemed.admissionTicketId}`
          : "입장 티켓이 발급되었습니다.",
      });
      await loadExchangeCodes();
      await loadAdmissionTickets();
    } catch (requestError) {
      setRedeemMsg({ ok: false, text: requestError.message || "교환 코드 사용에 실패했습니다." });
    } finally {
      setRedeemingCode(false);
    }
  };

  const tabBtnCls = (active) =>
    `flex-shrink-0 px-md py-sm rounded-full text-caption font-body-strong flex items-center gap-1 transition-colors ${
      active ? "bg-white text-on-surface" : "text-white/60"
    }`;

  if (isPlatformAdmin) {
    return <Navigate to="/platform-admin" replace />;
  }

  if (isOrganizer) {
    return (
      <Navigate
        to={`/organizer-admin?organizationId=${member.organization.organizationId}`}
        replace
      />
    );
  }

  if (isExhibitor) {
    return <Navigate to="/exhibitor-admin" replace />;
  }

  return (
    <div className="bg-surface-container-low text-on-surface">
      <TopNav active="mypage" />

      <main className="pt-[44px] pb-xxl">
        {/* Profile header */}
        <section className="bg-black text-white px-lg pt-xl pb-lg">
          <div className="max-w-[900px] mx-auto">
            <div className="flex items-center gap-md mb-lg">
              <div className="w-12 h-12 rounded-full bg-surface-tile-dark-alt flex items-center justify-center"><Icon name="person" /></div>
              <div>
                <p className="font-body-strong">{member.nickname} 님</p>
                <p className="text-[12px] text-white/50">{loginDescription}</p>
              </div>
            </div>
            <div className="flex gap-xs overflow-x-auto hide-scrollbar">
              {visibleTabs.map((t) => (
                <button
                  key={t.key}
                  onClick={() => t.key === "notif" ? openPanel() : setTab(t.key)}
                  className={tabBtnCls(tab === t.key)}
                >
                  <Icon name={t.icon} className="text-[16px]" />{t.label}
                  {t.key === "notif" && unreadCount > 0 && (
                    <span className="ml-0.5 rounded-full bg-[#ff3b30] px-1.5 text-[9px] font-bold leading-4 text-white">
                      {unreadCount > 99 ? "99+" : unreadCount}
                    </span>
                  )}
                </button>
              ))}
            </div>
          </div>
        </section>

        <div className="max-w-[900px] mx-auto px-lg py-xl">
          {!admissionEventsLoading && admissionEvents.length > 0 && (
            <div className="mb-lg rounded-2xl border border-hairline bg-white p-lg shadow-sm">
              <div className="flex flex-col gap-md sm:flex-row sm:items-center sm:justify-between">
                <div>
                  <p className="text-caption font-body-strong">현장 입장 업무</p>
                  <p className="mt-xs text-caption text-ink-muted">
                    담당 행사 {admissionEvents.length}개
                  </p>
                </div>
                <Link
                  to="/staff/admission"
                  className="inline-flex items-center justify-center gap-xs rounded-full bg-primary px-lg py-sm text-caption font-body-strong text-white"
                >
                  <Icon name="qr_code_scanner" className="text-[18px]" />
                  담당 행사 보기
                </Link>
              </div>
            </div>
          )}

          {tab === "business-overview" && (
            <div className="space-y-lg">
              <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
                <div className="p-lg font-body-strong">사업자 정보</div>
                <div className="grid gap-0 sm:grid-cols-3 sm:divide-x sm:divide-divider-soft">
                  <div className="p-lg">
                    <p className="text-[11px] text-ink-muted">회사명</p>
                    <p className="mt-1 font-body-strong">{member.organization?.name || "조직 정보 없음"}</p>
                  </div>
                  <div className="p-lg">
                    <p className="text-[11px] text-ink-muted">사업자 유형</p>
                    <p className="mt-1 font-body-strong">{organizationTypeLabel}</p>
                  </div>
                  <div className="p-lg">
                    <p className="text-[11px] text-ink-muted">조직 권한</p>
                    <p className="mt-1 font-body-strong">{organizationRoleLabel}</p>
                  </div>
                </div>
              </div>

              <div className="bg-white rounded-2xl border border-hairline p-xl text-center">
                <div className="mx-auto mb-sm flex h-12 w-12 items-center justify-center rounded-full bg-surface-container">
                  <Icon name={businessActivityIcon} className="text-[22px] text-ink-muted" />
                </div>
                <p className="font-body-strong">
                  {isOrganizer ? "등록한 행사가 없습니다." : isExhibitor ? "부스 신청 내역이 없습니다." : "조직 정보를 확인할 수 없습니다."}
                </p>
                <p className="mt-xs text-caption text-ink-muted">
                  {isOrganizer ? "행사를 등록하면 여기에서 관리할 수 있어요." : isExhibitor ? "부스 참가를 신청하면 여기에 현황이 표시돼요." : "조직 관리자에게 소속 정보를 확인해 주세요."}
                </p>
              </div>
            </div>
          )}

          {tab === "business-activity" && (
            <div className="bg-white rounded-2xl border border-hairline p-xl text-center">
              <Icon name={businessActivityIcon} className="mb-sm text-[28px] text-ink-muted" />
              <h2 className="font-body-strong">
                {isExhibitor ? "부스 신청 현황" : businessActivityLabel}
              </h2>
              <p className="mt-xs text-caption text-ink-muted">
                {isOrganizer ? "개최자센터에서 등록한 행사를 관리할 수 있습니다." : isExhibitor ? "부스 신청 내역과 검토 상태를 확인할 수 있습니다." : "조직 정보를 확인한 후 이용해 주세요."}
              </p>
              {/* 참가기업: 내 부스 신청 현황 페이지로 연결 (WBS-196) */}
              {isExhibitor && (
                <Link
                  to="/my-applications"
                  className="mt-md inline-flex items-center gap-xs px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong"
                >
                  <Icon name="storefront" className="text-[16px]" /> 내 부스 신청 현황 보기
                </Link>
              )}
              {/* 개최자: 개최자센터로 연결 */}
              {isOrganizer && (
                <Link
                  to="/organizer-admin"
                  className="mt-md inline-flex items-center gap-xs px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong"
                >
                  <Icon name="event" className="text-[16px]" /> 개최자센터로 이동
                </Link>
              )}
            </div>
          )}

          {/* TICKETS */}
          {tab === "tickets" && (
            <div className="space-y-lg">
              <div className="bg-white rounded-2xl border border-hairline p-lg">
                <h3 className="font-body-strong text-body-strong mb-1">교환 코드 등록</h3>
                <p className="text-caption text-ink-muted mb-md">외부 예매처에서 받은 코드를 입력하면 입장 QR이 발급돼요.</p>
                <div className="flex gap-sm">
                  <input
                    value={code}
                    onChange={handleCodeChange}
                    type="text"
                    placeholder="교환 코드 입력 (예: ABCD-1234)"
                    className="flex-1 h-[44px] rounded-lg border border-hairline px-sm outline-none focus:border-primary-focus"
                  />
                  <button
                    onClick={validateCode}
                    disabled={validatingCode || redeemingCode}
                    className="px-lg h-[44px] rounded-lg bg-primary text-white font-body-strong flex items-center gap-1 disabled:opacity-50"
                  >
                    <Icon name="key" className="text-[18px]" />{validatingCode ? "확인 중" : "검증"}
                  </button>
                </div>
                {validationResult?.valid && validatedCode === code.trim() && (
                  <div className="mt-md rounded-xl bg-surface-container p-md text-caption">
                    <p className="font-body-strong text-body">{validationResult.eventName}</p>
                    <p className="text-ink-muted">상태 {exchangeCodeStatusLabel[validationResult.status] || validationResult.status} · {exchangeCodeSourceLabel[validationResult.source] || validationResult.source}</p>
                    <p className="text-ink-muted">만료일 {formatDateTime(validationResult.expiresAt)}</p>
                    <button
                      onClick={redeemCode}
                      disabled={redeemingCode || validatedCode !== code.trim()}
                      className="mt-md w-full rounded-lg bg-black px-lg py-sm text-white font-body-strong disabled:opacity-50"
                    >
                      {redeemingCode ? "사용 중..." : "사용하기"}
                    </button>
                  </div>
                )}
                {redeemMsg && (
                  <p className={`text-caption mt-sm ${redeemMsg.ok ? "text-status-available" : "text-error"}`}>{redeemMsg.text}</p>
                )}
                {issuedAdmissionTicketId && (
                  <Link
                    to={`/admission-tickets/${issuedAdmissionTicketId}`}
                    className="mt-sm inline-flex items-center gap-1 rounded-full border border-hairline px-md py-1.5 text-caption font-body-strong"
                  >
                    <Icon name="qr_code_2" className="text-[16px]" />
                    입장 티켓 보기
                  </Link>
                )}
              </div>

              <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
                <div className="p-lg font-body-strong">내 교환 코드</div>
                {exchangeCodesLoading && (
                  <p className="p-lg text-caption text-ink-muted">교환 코드 목록을 불러오는 중입니다.</p>
                )}
                {exchangeCodesError && (
                  <p className="p-lg text-caption text-error">{exchangeCodesError}</p>
                )}
                {!exchangeCodesLoading && !exchangeCodesError && exchangeCodes.length === 0 && (
                  <p className="p-lg text-caption text-ink-muted">보유한 교환 코드가 없습니다.</p>
                )}
                {!exchangeCodesLoading && !exchangeCodesError && exchangeCodes.map((exchangeCode) => (
                  <div key={exchangeCode.exchangeCodeId} className="flex items-center gap-md p-lg">
                    <div className="w-11 h-11 rounded-lg flex items-center justify-center text-white flex-shrink-0" style={{ background: "linear-gradient(135deg,#00b09b,#96c93d)" }}><Icon name="key" className="text-[18px]" /></div>
                    <div className="min-w-0 flex-1">
                      <p className="font-mono font-body-strong break-all">{exchangeCode.code}</p>
                      <p className="text-caption text-ink-muted">{exchangeCode.eventName} · {exchangeCodeSourceLabel[exchangeCode.source] || exchangeCode.source}</p>
                      <p className="text-[11px] text-ink-muted">
                        발급 {formatDateTime(exchangeCode.createdAt)}
                        {exchangeCode.expiresAt ? ` · 만료 ${formatDateTime(exchangeCode.expiresAt)}` : ""}
                        {exchangeCode.redeemedAt ? ` · 사용 ${formatDateTime(exchangeCode.redeemedAt)}` : ""}
                      </p>
                    </div>
                    <span className="text-[11px] font-bold px-sm py-1 rounded-full bg-primary-container/10 text-primary-focus">
                      {exchangeCodeStatusLabel[exchangeCode.status] || exchangeCode.status}
                    </span>
                  </div>
                ))}
                {!exchangeCodesError && (
                  <Pager
                    pageInfo={exchangeCodesPageInfo}
                    loading={exchangeCodesLoading}
                    onPrev={() => setExchangeCodesPage((page) => Math.max(0, page - 1))}
                    onNext={() => setExchangeCodesPage((page) => Math.min(exchangeCodesPageInfo.totalPages - 1, page + 1))}
                  />
                )}
              </div>

              <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
                <div className="p-lg font-body-strong">예매한 행사</div>
                {ticketOrdersLoading && (
                  <p className="p-lg text-caption text-ink-muted">예매 내역을 불러오는 중입니다.</p>
                )}
                {ticketOrdersError && (
                  <p className="p-lg text-caption text-error">{ticketOrdersError}</p>
                )}
                {!ticketOrdersLoading && !ticketOrdersError && ticketOrders.length === 0 && (
                  <p className="p-lg text-caption text-ink-muted">아직 구매한 티켓이 없습니다.</p>
                )}
                {!ticketOrdersLoading && !ticketOrdersError && ticketOrders.map((order) => (
                  <Link key={order.orderNo} to={`/tickets/orders/${order.orderNo}`} className="flex items-center gap-md p-lg transition-colors hover:bg-surface-container-low">
                    <div className="w-11 h-11 rounded-lg flex items-center justify-center text-white flex-shrink-0" style={{ background: "linear-gradient(135deg,#ff9966,#ff5e62)" }}><Icon name="confirmation_number" className="text-[18px]" /></div>
                    <div className="min-w-0 flex-1">
                      <p className="font-body-strong truncate">{order.eventName}</p>
                      <p className="text-caption text-ink-muted">주문 {order.orderNo} · {order.quantity}매 · {formatMoney(order.totalAmount)}</p>
                      <p className="text-[11px] text-ink-muted">주문 일시 {formatDateTime(order.createdAt)}</p>
                    </div>
                    <span className="text-[11px] font-bold px-sm py-1 rounded-full bg-primary-container/10 text-primary-focus">
                      {orderStatusLabel[order.ticketOrderStatus] || order.ticketOrderStatus}
                    </span>
                  </Link>
                ))}
                {!ticketOrdersError && (
                  <Pager
                    pageInfo={ticketOrdersPageInfo}
                    loading={ticketOrdersLoading}
                    onPrev={() => setTicketOrdersPage((page) => Math.max(0, page - 1))}
                    onNext={() => setTicketOrdersPage((page) => Math.min(ticketOrdersPageInfo.totalPages - 1, page + 1))}
                  />
                )}
              </div>
            </div>
          )}

          {/* REFUNDS */}
          {tab === "refunds" && (
            <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
              <div className="p-lg font-body-strong">환불 내역</div>
              {refundsLoading && (
                <p className="p-lg text-caption text-ink-muted">환불 내역을 불러오는 중입니다.</p>
              )}
              {refundsError && (
                <p className="p-lg text-caption text-error">{refundsError}</p>
              )}
              {!refundsLoading && !refundsError && refunds.length === 0 && (
                <p className="p-lg text-caption text-ink-muted">환불 내역이 없습니다.</p>
              )}
              {!refundsLoading && !refundsError && refunds.map((refund) => (
                <Link key={refund.refundId} to={`/refunds/${refund.refundId}`} className="flex items-center gap-md p-lg transition-colors hover:bg-surface-container-low">
                  <div className="w-11 h-11 rounded-lg flex items-center justify-center text-white flex-shrink-0" style={{ background: "linear-gradient(135deg,#667eea,#764ba2)" }}><Icon name="payments" className="text-[18px]" /></div>
                  <div className="min-w-0 flex-1">
                    <p className="font-body-strong truncate">{refund.eventName}</p>
                    <p className="text-caption text-ink-muted">환불 #{refund.refundId} · 주문 {refund.orderNo} · {formatMoney(refund.refundAmount)}</p>
                    <p className="text-[11px] text-ink-muted">
                      신청 {formatDateTime(refund.requestedAt)}
                      {refund.completedAt ? ` · 처리 ${formatDateTime(refund.completedAt)}` : ""}
                    </p>
                  </div>
                  <span className="text-[11px] font-bold px-sm py-1 rounded-full bg-primary-container/10 text-primary-focus">
                    {refundStatusLabel[refund.refundStatus] || refund.refundStatus}
                  </span>
                </Link>
              ))}
              {!refundsError && (
                <Pager
                  pageInfo={refundsPageInfo}
                  loading={refundsLoading}
                  onPrev={() => setRefundsPage((page) => Math.max(0, page - 1))}
                  onNext={() => setRefundsPage((page) => Math.min(refundsPageInfo.totalPages - 1, page + 1))}
                />
              )}
            </div>
          )}

          {/* QR */}
          {tab === "qr" && (
            <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
              <div className="p-lg font-body-strong">입장 티켓</div>
              {admissionTicketsLoading && (
                <p className="p-lg text-caption text-ink-muted">입장 티켓 목록을 불러오는 중입니다.</p>
              )}
              {admissionTicketsError && (
                <p className="p-lg text-caption text-error">{admissionTicketsError}</p>
              )}
              {!admissionTicketsLoading && !admissionTicketsError && admissionTickets.length === 0 && (
                <p className="p-lg text-caption text-ink-muted">발급된 입장 티켓이 없습니다.</p>
              )}
              {!admissionTicketsLoading && !admissionTicketsError && admissionTickets.map((ticket) => (
                <Link
                  key={ticket.admissionTicketId}
                  to={`/admission-tickets/${ticket.admissionTicketId}`}
                  className="flex items-center gap-md p-lg transition-colors hover:bg-surface-container-low"
                >
                  <div className="w-11 h-11 rounded-lg flex items-center justify-center text-white flex-shrink-0" style={{ background: "linear-gradient(135deg,#0f766e,#14b8a6)" }}>
                    <Icon name="qr_code_2" className="text-[18px]" />
                  </div>
                  <div className="min-w-0 flex-1">
                    <p className="font-body-strong truncate">{ticket.eventName}</p>
                    <p className="text-caption text-ink-muted">입장 티켓 ID {ticket.admissionTicketId}</p>
                    <p className="text-[11px] text-ink-muted">
                      발급 {formatDateTime(ticket.issuedAt)}
                      {ticket.usedAt ? ` · 사용 ${formatDateTime(ticket.usedAt)}` : ""}
                      {ticket.cancelledAt ? ` · 취소 ${formatDateTime(ticket.cancelledAt)}` : ""}
                    </p>
                  </div>
                  <span className="text-[11px] font-bold px-sm py-1 rounded-full bg-primary-container/10 text-primary-focus">
                    {admissionStatusLabel[ticket.status] || ticket.status}
                  </span>
                </Link>
              ))}
              {!admissionTicketsError && (
                <Pager
                  pageInfo={admissionTicketsPageInfo}
                  loading={admissionTicketsLoading}
                  onPrev={() => setAdmissionTicketsPage((page) => Math.max(0, page - 1))}
                  onNext={() => setAdmissionTicketsPage((page) => Math.min(admissionTicketsPageInfo.totalPages - 1, page + 1))}
                />
              )}
            </div>
          )}

          {/* BOOTH RESERVATIONS */}
          {tab === "boothReservations" && (
            <div className="space-y-md">
              {boothReservationsLoading && <p className="text-caption text-ink-muted">부스 예약을 불러오는 중입니다.</p>}
              {boothReservationsError && <p className="text-caption text-error">{boothReservationsError}</p>}
              {!boothReservationsLoading && !boothReservationsError && boothReservations.length === 0 && (
                <p className="text-center text-ink-muted py-xxl">
                  <Icon name="storefront" className="text-[32px] block mb-sm" />
                  예약한 부스가 없어요
                </p>
              )}
              {boothReservations.map((reservation) => (
                <div key={reservation.id} className="bg-white rounded-2xl border border-hairline p-lg">
                  <div className="flex justify-between items-start mb-sm">
                    <div className="min-w-0">
                      {reservation.eventName && <p className="text-[11px] text-ink-muted mb-1 truncate">{reservation.eventName}</p>}
                      <Link
                        to={`/booth-detail?eventId=${reservation.eventId}&boothId=${reservation.boothId}`}
                        className="font-body-strong hover:underline"
                      >
                        {reservation.boothDisplayName || reservation.boothCode}
                      </Link>
                    </div>
                    <span className={`flex-shrink-0 text-[11px] font-bold px-sm py-1 rounded-full bg-${boothReservationStatusColor[reservation.status]}/10 text-${boothReservationStatusColor[reservation.status]}`}>
                      {boothReservationStatusLabel[reservation.status] || reservation.status}
                    </span>
                  </div>
                  <p className="text-caption text-ink-muted">
                    {formatSlotRange(reservation.slotStartAt, reservation.slotEndAt)} · {reservation.partySize}명
                  </p>
                  {reservation.status === "RESERVED" && (
                    <button
                      onClick={() => handleCancelBoothReservation(reservation)}
                      disabled={cancellingReservationId === reservation.id}
                      className="mt-sm text-caption font-body-strong text-error disabled:opacity-40"
                    >
                      {cancellingReservationId === reservation.id ? "취소 중..." : "예약 취소"}
                    </button>
                  )}
                </div>
              ))}
              {!boothReservationsError && (
                <Pager
                  pageInfo={boothReservationsPageInfo}
                  loading={boothReservationsLoading}
                  onPrev={() => setBoothReservationsPage((page) => Math.max(0, page - 1))}
                  onNext={() => setBoothReservationsPage((page) => Math.min(boothReservationsPageInfo.totalPages - 1, page + 1))}
                />
              )}
            </div>
          )}
          {/* PROFILE */}
          {tab === "profile" && (
            <div className="space-y-lg">
              <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
                <div className="p-lg"><p className="text-[11px] text-ink-muted">닉네임</p><p className="text-body">{member.nickname}</p></div>
                <div className="p-lg"><p className="text-[11px] text-ink-muted">이메일</p><p className="text-body">{memberEmail}</p></div>
                <div className="p-lg"><p className="text-[11px] text-ink-muted">계정 유형</p><p className="text-body">{isBusinessMember ? "사업자 계정" : "일반 회원"}</p></div>
                {isBusinessMember && member.organization && (
                  <>
                    <div className="p-lg"><p className="text-[11px] text-ink-muted">회사명</p><p className="text-body">{member.organization.name}</p></div>
                    <div className="p-lg"><p className="text-[11px] text-ink-muted">사업자 유형</p><p className="text-body">{organizationTypeLabel}</p></div>
                    <div className="p-lg"><p className="text-[11px] text-ink-muted">조직 권한</p><p className="text-body">{organizationRoleLabel}</p></div>
                  </>
                )}
              </div>
              {!isBusinessMember && (
                <div className="bg-white rounded-2xl border border-hairline p-lg">
                  <h3 className="font-body-strong text-body-strong mb-md">내가 작성한 후기</h3>
                  {myReviewsLoading && <p className="text-caption text-ink-muted">후기를 불러오는 중입니다.</p>}
                  {myReviewsError && <p className="text-caption text-error">{myReviewsError}</p>}
                  {!myReviewsLoading && !myReviewsError && myReviews.length === 0 && (
                    <p className="text-center text-ink-muted py-lg">
                      <Icon name="rate_review" className="text-[28px] block mb-sm" />
                      아직 작성한 후기가 없어요
                    </p>
                  )}
                  {myReviews.length > 0 && (
                    <div className="divide-y divide-divider-soft">
                      {myReviews.map((review) => (
                        <Link
                          key={review.id}
                          to={`/booth-detail?eventId=${review.eventId}&boothId=${review.boothId}`}
                          className="block py-md hover:bg-surface-pearl transition-colors -mx-lg px-lg"
                        >
                          <div className="flex justify-between items-center mb-1">
                            <p className="font-body-strong text-caption">{review.boothDisplayName || review.boothCode}</p>
                            <span className="text-[11px] text-ink-muted">
                              {review.createdAt ? new Date(review.createdAt).toLocaleDateString("ko-KR") : ""}
                            </span>
                          </div>
                          {review.eventName && <p className="text-[11px] text-ink-muted mb-1">{review.eventName}</p>}
                          <div className="flex mb-1">
                            {[1, 2, 3, 4, 5].map((i) => (
                              <Icon key={i} name="star" fill={i <= review.rating} className={`text-[14px] ${i <= review.rating ? "text-amber-500" : "text-hairline"}`} />
                            ))}
                          </div>
                          {review.comment && <p className="text-caption text-on-surface-variant">{review.comment}</p>}
                        </Link>
                      ))}
                    </div>
                  )}
                  {!myReviewsError && (
                    <Pager
                      pageInfo={myReviewsPageInfo}
                      loading={myReviewsLoading}
                      onPrev={() => setMyReviewsPage((page) => Math.max(0, page - 1))}
                      onNext={() => setMyReviewsPage((page) => Math.min(myReviewsPageInfo.totalPages - 1, page + 1))}
                    />
                  )}
                </div>
              )}
              <button onClick={handleLogout} disabled={isLoggingOut} className="w-full h-[46px] border border-hairline rounded-full font-body-strong flex items-center justify-center gap-1 disabled:cursor-not-allowed disabled:opacity-50"><Icon name="logout" className="text-[18px]" />{isLoggingOut ? "처리 중" : "로그아웃"}</button>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}

function Pager({ pageInfo, loading, onPrev, onNext }) {
  const current = pageInfo.number + 1;
  const total = Math.max(1, pageInfo.totalPages);
  return (
    <div className="flex items-center justify-center gap-sm p-md text-caption">
      <button
        type="button"
        onClick={onPrev}
        disabled={loading || pageInfo.number <= 0}
        className="rounded-full border border-hairline px-md py-1 disabled:opacity-40"
      >
        이전
      </button>
      <span className="text-ink-muted">{current} / {total}</span>
      <button
        type="button"
        onClick={onNext}
        disabled={loading || current >= total}
        className="rounded-full border border-hairline px-md py-1 disabled:opacity-40"
      >
        다음
      </button>
    </div>
  );
}
