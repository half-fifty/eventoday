import { useCallback, useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import { eventApi } from "../api/eventApi.js";
import { getEventApplications } from "../api/boothApplicationApi.js";
import { listBooths } from "../api/boothApi.js";
// STAT-API-001~004를 모두 제공하는 statisticsApi로 통일 (boothStatisticsApi는 overview만 보유)
import {
  getEventOverview,
  getPopularBooths,
  getHourlyStatistics,
  getPreviousDayStatistics,
} from "../api/statisticsApi.js";
import { getManagementRecruitment } from "../api/recruitmentApi.js";
import { listVenueMaps } from "../api/venueMapApi.js";
import BoothManagementPanel from "../components/BoothManagementPanel.jsx";
import { OrganizerExchangeCodeRequestPanel } from "../components/ExchangeCodeRequestPanels.jsx";
import FloorplanManagementPanel from "../components/FloorplanManagementPanel.jsx";
import Icon from "../components/Icon.jsx";
import OrganizerApplicationPanel from "../components/OrganizerApplicationPanel.jsx";
import RecruitmentManagementPanel from "../components/RecruitmentManagementPanel.jsx";
import TopNav from "../components/TopNav.jsx";
import useAuth from "../hooks/useAuth.js";

const navItems = [
  { key: "dashboard", label: "대시보드", icon: "dashboard" },
  { key: "recruitment", label: "부스 모집 공고", icon: "campaign" },
  { key: "applications", label: "부스 신청서 검토", icon: "assignment" },
  { key: "assignment", label: "부스 관리", icon: "grid_view" },
  { key: "floorplan", label: "평면도 관리", icon: "map" },
  { key: "exchange-codes", label: "외부 예매 티켓 연동", icon: "key" },
  { key: "approval", label: "행사 등록 승인 요청", icon: "verified" },
];

const EVENT_STATUS_LABEL = {
  PREPARING: "작성 중",
  SUBMITTED: "승인 요청",
  UNDER_REVIEW: "관리자 검토 중",
  APPROVED: "승인 완료",
  REJECTED: "반려",
  PUBLISHED: "공개 중",
  CANCELLED: "취소",
  SUSPENDED: "게시 중지",
};

const APPLICATION_STATUS_LABEL = {
  SUBMITTED: "검토 대기",
  UNDER_REVIEW: "검토 중",
};

const initialDashboard = {
  reviewRequired: null,
  assignedBooths: null,
  totalBooths: null,
  ticketSoldQuantity: null,
  todayBoothQrScans: null,
  applications: [],
};

const localDateString = (date = new Date()) => {
  const year = date.getFullYear();
  const month = String(date.getMonth() + 1).padStart(2, "0");
  const day = String(date.getDate()).padStart(2, "0");
  return `${year}-${month}-${day}`;
};

const displayCount = (value, loading) => loading || value == null ? "-" : Number(value).toLocaleString();
const formatDateTime = (value) => value
  ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" })
  : "-";

export default function OrganizerAdmin() {
  const { member } = useAuth();
  const [page, setPage] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [query, setQuery] = useSearchParams();
  const requestedOrganizationId = query.get("organizationId") || "";
  const requestedEventId = query.get("eventId") || "";
  const previousRequestedEventId = useRef(requestedEventId);
  const initialOrganizationId = requestedOrganizationId
    || String(member?.organization?.organizationId || "")
    || localStorage.getItem("organizationId")
    || "";

  const [organizationId, setOrganizationId] = useState(initialOrganizationId);
  const [managedOrganizations, setManagedOrganizations] = useState([]);
  const [managedEvents, setManagedEvents] = useState([]);
  const [selectedEventId, setSelectedEventId] = useState("");
  const [eventsLoading, setEventsLoading] = useState(true);
  const [eventLoadError, setEventLoadError] = useState("");

  const [selectedEventDetail, setSelectedEventDetail] = useState(null);
  const [dashboard, setDashboard] = useState(initialDashboard);
  const [dashboardLoading, setDashboardLoading] = useState(false);
  const [dashboardError, setDashboardError] = useState("");
  const [dashboardVersion, setDashboardVersion] = useState(0);

  // 운영 통계 (STAT-API-001~004) - 최근 30일 기준
  const [overview, setOverview] = useState(null);          // STAT-API-004 행사 운영 요약
  const [popularBooths, setPopularBooths] = useState([]);  // STAT-API-003 기간별 인기 부스
  const [statError, setStatError] = useState("");
  const [statLoading, setStatLoading] = useState(false);

  // 부스 단위 통계 - 부스를 선택해야 조회한다
  const [statBoothId, setStatBoothId] = useState("");
  const [hourlyDate, setHourlyDate] = useState(localDateString());
  const [hourlyStats, setHourlyStats] = useState([]);       // STAT-API-001 시간대별
  const [prevDayStat, setPrevDayStat] = useState(null);     // STAT-API-002 전날
  const [boothStatLoading, setBoothStatLoading] = useState(false);
  // 시간대·전날 통계는 서로 다른 API라 에러를 분리한다
  // 하나로 합치면 한쪽만 실패했을 때 성공한 패널까지 숨겨지거나, 실패를 "데이터 없음"으로 오인하게 된다
  const [hourlyError, setHourlyError] = useState("");
  const [prevDayError, setPrevDayError] = useState("");

  const [submittingEvent, setSubmittingEvent] = useState(false);
  const [publishingEvent, setPublishingEvent] = useState(false);
  const [recruitmentStatus, setRecruitmentStatus] = useState(null);
  const [recruitmentLoading, setRecruitmentLoading] = useState(false);
  const [approvalMaps, setApprovalMaps] = useState([]);
  const [approvalBooths, setApprovalBooths] = useState(null);
  const [approvalLoading, setApprovalLoading] = useState(false);
  const [approvalError, setApprovalError] = useState("");

  useEffect(() => {
    eventApi.managedOrganizations()
      .then((result) => {
        const list = result?.data || [];
        setManagedOrganizations(list);
        if (list.length === 0) {
          setOrganizationId("");
          localStorage.removeItem("organizationId");
          return;
        }
        const isValid = list.some((organization) => String(organization.id) === String(initialOrganizationId));
        setOrganizationId(isValid ? String(initialOrganizationId) : String(list[0].id));
      })
      .catch((error) => {
        setManagedOrganizations([]);
        setEventLoadError(error.message || "소속 조직 정보를 불러오지 못했습니다.");
        setEventsLoading(false);
      });
    // 최초 진입 시에만 소속 조직을 결정한다.
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (requestedOrganizationId
        && managedOrganizations.some((organization) => String(organization.id) === requestedOrganizationId)) {
      setOrganizationId(requestedOrganizationId);
    }
  }, [requestedOrganizationId, managedOrganizations]);

  useEffect(() => {
    if (organizationId) localStorage.setItem("organizationId", organizationId);
  }, [organizationId]);

  useEffect(() => {
    if (!organizationId) {
      setManagedEvents([]);
      setSelectedEventId("");
      setEventsLoading(false);
      return;
    }

    let active = true;
    setEventsLoading(true);
    setManagedEvents([]);
    setSelectedEventId("");
    setEventLoadError("");
    eventApi.organizationList(organizationId, { size: 100, sort: "createdAt,desc" })
      .then((result) => {
        if (!active) return;
        setManagedEvents(result?.data?.content || []);
      })
      .catch((error) => {
        if (!active) return;
        setManagedEvents([]);
        setEventLoadError(error.message || "행사 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (active) setEventsLoading(false);
      });
    return () => { active = false; };
  }, [organizationId]);

  useEffect(() => {
    if (managedEvents.length === 0) {
      setSelectedEventId("");
      return;
    }
    const requestedEventChanged = previousRequestedEventId.current !== requestedEventId;
    previousRequestedEventId.current = requestedEventId;
    const requestedEventExists = requestedEventId
      && managedEvents.some((event) => String(event.id) === requestedEventId);
    setSelectedEventId((currentEventId) => {
      if (requestedEventChanged && requestedEventExists) return requestedEventId;
      const currentEventExists = currentEventId
        && managedEvents.some((event) => String(event.id) === String(currentEventId));
      if (currentEventExists) return currentEventId;
      return requestedEventExists ? requestedEventId : String(managedEvents[0].id);
    });
  }, [managedEvents, requestedEventId]);

  const selectedEvent = managedEvents.find((event) => String(event.id) === String(selectedEventId)) || null;

  const refreshDashboard = useCallback(() => {
    setDashboardVersion((previous) => previous + 1);
  }, []);

  useEffect(() => {
    if (!organizationId || !selectedEventId) {
      setSelectedEventDetail(null);
      setDashboard(initialDashboard);
      setDashboardError("");
      return;
    }

    let active = true;
    const load = async () => {
      setDashboardLoading(true);
      setDashboardError("");
      setSelectedEventDetail(null);
      setDashboard(initialDashboard);
      const today = localDateString();
      const results = await Promise.allSettled([
        eventApi.managedDetail(organizationId, selectedEventId),
        getEventApplications(selectedEventId, { status: "SUBMITTED", page: 0, size: 3 }),
        getEventApplications(selectedEventId, { status: "UNDER_REVIEW", page: 0, size: 3 }),
        listBooths(selectedEventId, { page: 0, size: 1 }),
        listBooths(selectedEventId, { status: "ASSIGNED", page: 0, size: 1 }),
        getEventOverview(selectedEventId, today, today),
      ]);
      if (!active) return;

      const [detailResult, submittedResult, reviewingResult, boothsResult, assignedResult, statisticsResult] = results;
      const detail = detailResult.status === "fulfilled" ? detailResult.value?.data : null;
      const submitted = submittedResult.status === "fulfilled" ? submittedResult.value : null;
      const reviewing = reviewingResult.status === "fulfilled" ? reviewingResult.value : null;
      const booths = boothsResult.status === "fulfilled" ? boothsResult.value : null;
      const assigned = assignedResult.status === "fulfilled" ? assignedResult.value : null;
      const statistics = statisticsResult.status === "fulfilled" ? statisticsResult.value : null;

      setSelectedEventDetail(detail);
      setDashboard({
        reviewRequired: submitted && reviewing
          ? Number(submitted.totalElements || 0) + Number(reviewing.totalElements || 0)
          : null,
        assignedBooths: assigned?.totalElements ?? null,
        totalBooths: booths?.totalElements ?? null,
        ticketSoldQuantity: detail?.ticketSoldQuantity ?? null,
        todayBoothQrScans: statistics?.totalQrScanCount ?? null,
        applications: [
          ...(reviewing?.content || []).slice(0, 2),
          ...(submitted?.content || []).slice(0, 2),
        ].slice(0, 3),
      });

      if (results.some((result) => result.status === "rejected")) {
        setDashboardError("권한 또는 데이터 상태 때문에 일부 운영 정보를 불러오지 못했습니다.");
      }
      setDashboardLoading(false);
    };
    load();
    return () => { active = false; };
  }, [organizationId, selectedEventId, dashboardVersion]);

  // STAT-API-003/004: 최근 30일 운영 통계 요약 + 인기 부스
  // 대시보드 카드(오늘 기준)와 별개로 기간 추이를 보기 위한 조회
  useEffect(() => {
    if (!selectedEventId) {
      setOverview(null);
      setPopularBooths([]);
      setStatError("");
      return;
    }
    let active = true;
    const to = localDateString();
    const from = localDateString(new Date(Date.now() - 29 * 24 * 60 * 60 * 1000));
    setStatLoading(true);
    setStatError("");
    Promise.allSettled([
      getEventOverview(selectedEventId, from, to),
      getPopularBooths(selectedEventId, from, to),
    ]).then(([overviewResult, popularResult]) => {
      if (!active) return;
      setOverview(overviewResult.status === "fulfilled" ? overviewResult.value : null);
      setPopularBooths(popularResult.status === "fulfilled" ? (popularResult.value?.booths || []) : []);
      if (overviewResult.status === "rejected" || popularResult.status === "rejected") {
        setStatError("일부 통계를 불러오지 못했습니다.");
      }
      setStatLoading(false);
    });
    return () => { active = false; };
  }, [selectedEventId, dashboardVersion]);

  // 행사가 바뀌면 부스 선택을 초기화한다 (이전 행사 부스 ID가 남지 않도록)
  useEffect(() => {
    setStatBoothId("");
    setHourlyStats([]);
    setPrevDayStat(null);
  }, [selectedEventId]);

  // STAT-API-001/002: 선택한 부스의 시간대별 통계 + 전날 통계
  useEffect(() => {
    if (!statBoothId) {
      setHourlyStats([]);
      setPrevDayStat(null);
      setHourlyError("");
      setPrevDayError("");
      return;
    }
    let active = true;
    setBoothStatLoading(true);
    setHourlyError("");
    setPrevDayError("");
    Promise.allSettled([
      getHourlyStatistics(statBoothId, hourlyDate),
      getPreviousDayStatistics(statBoothId),
    ]).then(([hourlyResult, prevResult]) => {
      if (!active) return;
      // 각 통계의 성공·실패를 독립적으로 반영한다 (한쪽 실패가 다른 쪽 표시를 막지 않도록)
      if (hourlyResult.status === "fulfilled") {
        setHourlyStats(hourlyResult.value?.hourlyStats || []);
      } else {
        setHourlyStats([]);
        setHourlyError(hourlyResult.reason?.message || "시간대별 통계를 불러오지 못했습니다.");
      }
      if (prevResult.status === "fulfilled") {
        setPrevDayStat(prevResult.value);
      } else {
        setPrevDayStat(null);
        setPrevDayError(prevResult.reason?.message || "전날 통계를 불러오지 못했습니다.");
      }
      setBoothStatLoading(false);
    });
    return () => { active = false; };
  }, [statBoothId, hourlyDate]);

  useEffect(() => {
    if (!selectedEvent) {
      setRecruitmentStatus(null);
      setRecruitmentLoading(false);
      return;
    }
    if (!selectedEvent.boothRecruitmentEnabled) {
      setRecruitmentStatus("NOT_REQUIRED");
      setRecruitmentLoading(false);
      return;
    }

    let active = true;
    setRecruitmentStatus(null);
    setRecruitmentLoading(true);
    getManagementRecruitment(selectedEvent.id)
      .then((recruitment) => {
        if (active) setRecruitmentStatus(recruitment.status);
      })
      .catch((error) => {
        if (active) setRecruitmentStatus(error.status === 404 ? "NOT_CREATED" : "UNKNOWN");
      })
      .finally(() => {
        if (active) setRecruitmentLoading(false);
      });
    return () => { active = false; };
  }, [selectedEvent?.id, selectedEvent?.boothRecruitmentEnabled, dashboardVersion]);

  useEffect(() => {
    if (page !== "approval" || !selectedEventId) {
      setApprovalMaps([]);
      setApprovalBooths(null);
      setApprovalError("");
      return;
    }
    let active = true;
    setApprovalLoading(true);
    setApprovalError("");
    Promise.allSettled([
      listVenueMaps(selectedEventId),
      listBooths(selectedEventId, { page: 0, size: 100 }),
    ]).then(([mapsResult, boothsResult]) => {
      if (!active) return;
      setApprovalMaps(mapsResult.status === "fulfilled" ? mapsResult.value || [] : []);
      setApprovalBooths(boothsResult.status === "fulfilled" ? boothsResult.value : null);
      if (mapsResult.status === "rejected" || boothsResult.status === "rejected") {
        setApprovalError("평면도 또는 부스 준비 상태를 확인하지 못했습니다.");
      }
    }).finally(() => {
      if (active) setApprovalLoading(false);
    });
    return () => { active = false; };
  }, [page, selectedEventId, dashboardVersion]);

  const changeOrganization = (nextOrganizationId) => {
    setOrganizationId(nextOrganizationId);
    setSelectedEventId("");
    const next = new URLSearchParams(query);
    next.set("organizationId", nextOrganizationId);
    next.delete("eventId");
    setQuery(next, { replace: true });
  };

  const changeEvent = (nextEventId) => {
    setSelectedEventId(nextEventId);
    const next = new URLSearchParams(query);
    next.set("organizationId", organizationId);
    if (nextEventId) next.set("eventId", nextEventId);
    else next.delete("eventId");
    setQuery(next, { replace: true });
  };

  const submitSelectedEvent = async () => {
    if (!selectedEventId) return;
    setSubmittingEvent(true);
    setEventLoadError("");
    try {
      await eventApi.submit(selectedEventId);
      setManagedEvents((previous) => previous.map((event) =>
        String(event.id) === String(selectedEventId) ? { ...event, status: "SUBMITTED" } : event));
      setSelectedEventDetail((previous) => previous ? { ...previous, status: "SUBMITTED" } : previous);
    } catch (error) {
      setEventLoadError(error.message || "승인 요청에 실패했습니다.");
    } finally {
      setSubmittingEvent(false);
    }
  };

  const publishSelectedEvent = async () => {
    if (!selectedEventId || selectedEvent?.status !== "APPROVED") return;
    setPublishingEvent(true);
    setEventLoadError("");
    try {
      await eventApi.publish(selectedEventId);
      setManagedEvents((previous) => previous.map((event) =>
        String(event.id) === String(selectedEventId) ? { ...event, status: "PUBLISHED" } : event));
      setSelectedEventDetail((previous) => previous ? { ...previous, status: "PUBLISHED" } : previous);
    } catch (error) {
      setEventLoadError(error.message || "행사 공개에 실패했습니다.");
    } finally {
      setPublishingEvent(false);
    }
  };

  const requestableStatus = ["PREPARING", "REJECTED"].includes(selectedEvent?.status);
  const platformApproved = ["APPROVED", "PUBLISHED"].includes(selectedEvent?.status);
  const recruitmentReady = !selectedEvent?.boothRecruitmentEnabled || recruitmentStatus === "COMPLETED";
  const approvalRequestReady = Boolean(selectedEventId) && requestableStatus && recruitmentReady && !recruitmentLoading;
  const approvalGuide = !selectedEvent
    ? "행사를 선택해 주세요."
    : !requestableStatus
      ? ["SUBMITTED", "UNDER_REVIEW"].includes(selectedEvent.status)
        ? "플랫폼 관리자 검토를 기다리고 있습니다."
        : platformApproved
          ? selectedEvent.status === "PUBLISHED"
            ? "행사가 공개되어 사용자 행사 목록에 노출되고 있습니다."
            : "플랫폼 관리자 승인이 완료되었습니다. 행사를 공개할 수 있습니다."
          : `현재 ${EVENT_STATUS_LABEL[selectedEvent.status] || selectedEvent.status} 상태에서는 승인 요청을 할 수 없습니다.`
      : !selectedEvent.boothRecruitmentEnabled
        ? "부스 모집을 사용하지 않는 행사로, 승인 요청이 가능합니다."
        : recruitmentLoading
          ? "부스 모집 공고 상태를 확인하고 있습니다."
          : recruitmentStatus === "COMPLETED"
            ? "부스 모집 공고가 완료되어 승인 요청이 가능합니다."
            : recruitmentStatus === "NOT_CREATED"
              ? "부스 모집 공고를 등록하고 완료 처리해야 합니다."
              : recruitmentStatus === "UNKNOWN"
                ? "부스 모집 공고 상태를 확인하지 못했습니다."
                : `부스 모집 공고를 완료해야 합니다. (현재 ${recruitmentStatus || "확인 중"})`;

  const publishedMaps = approvalMaps.filter((map) => map.status === "PUBLISHED");
  const positionedBoothIds = new Set(publishedMaps.flatMap((map) => (map.positions || []).map((position) => position.boothId)));
  const boothCount = approvalBooths?.totalElements ?? null;
  const detail = selectedEventDetail || selectedEvent;
  const preparationChecks = useMemo(() => {
    if (!selectedEvent) return [];
    const checks = [
      { key: "basic", label: "행사 기본정보 등록", completed: Boolean(detail?.id), description: detail?.id ? "완료" : "확인 필요" },
      { key: "poster", label: "행사 대표 이미지 등록", completed: Boolean(detail?.representativeFileId), description: detail?.representativeFileId ? "완료" : "이미지 필요" },
    ];
    if (selectedEvent.boothRecruitmentEnabled) {
      checks.push({ key: "recruitment", label: "부스 모집 공고 완료", completed: recruitmentStatus === "COMPLETED", description: recruitmentStatus === "COMPLETED" ? "완료" : recruitmentStatus || "확인 중" });
      checks.push({ key: "booths", label: "행사 부스 등록", completed: Number(boothCount) > 0, description: boothCount == null ? "확인 중" : `${boothCount}개 등록` });
    }
    if (selectedEvent.venueMapEnabled) {
      checks.push({ key: "map", label: "관람객용 평면도 게시", completed: publishedMaps.some((map) => map.mapType === "VISITOR"), description: `${publishedMaps.length}개 게시` });
      checks.push({ key: "positions", label: "평면도 부스 좌표 등록", completed: positionedBoothIds.size > 0, description: `${positionedBoothIds.size}개 부스 배치` });
    }
    return checks;
  }, [selectedEvent, detail, recruitmentStatus, boothCount, publishedMaps.length, positionedBoothIds.size]);
  const completedPreparationCount = preparationChecks.filter((check) => check.completed).length;
  const preparationPercent = preparationChecks.length
    ? Math.round((completedPreparationCount / preparationChecks.length) * 100)
    : 0;

  const gotoPage = (key) => {
    setPage(key);
    if (window.innerWidth < 768) setSidebarOpen(false);
  };
  const navBtnCls = (active) => `group flex w-full items-center gap-sm rounded-r-xl border-l-[3px] px-md py-sm text-left font-body transition-all ${
    active
      ? "border-primary bg-primary/10 font-body-strong text-primary shadow-sm"
      : "border-transparent text-on-surface-variant hover:border-primary/30 hover:bg-surface-container"
  }`;

  const newEventPath = `/organizer-admin/events/new?organizationId=${organizationId || ""}`;
  const memberName = member?.nickname || member?.email || "개최자";
  const memberRole = member?.organization?.organizationRole || "ORGANIZER";

  return (
    <div className="min-h-screen bg-surface-container-lowest pt-[44px] text-on-surface">
      <TopNav active="organizer" />
      <aside className={`fixed bottom-0 left-0 top-[44px] z-50 flex w-[280px] flex-col border-r border-hairline bg-white shadow-[12px_0_40px_rgba(15,23,42,0.04)] transition-transform md:translate-x-0 ${sidebarOpen ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="flex items-start justify-between border-b border-hairline bg-gradient-to-br from-primary/10 via-white to-primary-container/10 px-lg py-lg">
          <div>
            <span className="text-[10px] font-bold tracking-[0.18em] text-primary">EVENTODAY</span>
            <h1 className="mt-1 font-display-md text-[20px]">개최자센터</h1>
            <p className="mt-1 text-[11px] text-ink-muted">행사 운영을 한곳에서 관리하세요</p>
          </div>
          <button type="button" aria-label="개최자센터 메뉴 닫기" onClick={() => setSidebarOpen(false)} className="md:hidden"><Icon name="close" /></button>
        </div>
        <div className="px-md pt-md">
          <Link to={newEventPath} className="flex w-full items-center justify-center gap-xs rounded-xl bg-primary px-md py-sm text-caption font-body-strong text-white shadow-sm transition hover:brightness-95">
            <Icon name="add_circle" className="text-[18px]" /> 새 행사 등록
          </Link>
        </div>
        <nav className="flex-1 space-y-1 overflow-y-auto px-md py-md" aria-label="개최자센터 메뉴">
          <p className="px-sm pb-xs text-[10px] font-bold tracking-[0.14em] text-ink-muted">행사 운영</p>
          {navItems.map((item) => (
            <button key={item.key} onClick={() => gotoPage(item.key)} className={navBtnCls(page === item.key)}>
              <Icon name={item.icon} /><span>{item.label}</span>
            </button>
          ))}
          <p className="px-sm pb-xs pt-md text-[10px] font-bold tracking-[0.14em] text-ink-muted">홍보</p>
          <Link to={`/organizer-admin/advertisements?organizationId=${organizationId || ""}&eventId=${selectedEventId || ""}`} className={navBtnCls(false)}>
            <Icon name="ads_click" /><span>광고 신청·관리</span>
          </Link>
        </nav>
        <div className="space-y-4 border-t border-hairline p-lg">
          <div className="flex items-center gap-sm">
            <div className="grid h-8 w-8 place-items-center rounded-full bg-surface-container-high"><Icon name="person" className="text-[18px]" /></div>
            <div className="flex min-w-0 flex-col"><span className="truncate text-caption font-body-strong">{memberName}</span><span className="text-[10px] text-ink-muted">{memberRole}</span></div>
          </div>
          <Link to="/" className="block w-full rounded-lg border border-hairline py-xs text-center text-caption text-secondary transition-colors hover:bg-surface-container">메인 사이트로</Link>
        </div>
      </aside>
      {sidebarOpen && <div onClick={() => setSidebarOpen(false)} className="fixed inset-0 z-40 bg-black/40 md:hidden" />}

      <main className="min-h-[calc(100vh-44px)] md:ml-[280px]">
        <header className="sticky top-[44px] z-30 flex h-[64px] items-center border-b border-hairline bg-white/70 px-lg backdrop-blur-xl">
          <div className="flex items-center gap-sm">
            <button onClick={() => setSidebarOpen(true)} className="md:hidden"><Icon name="menu" /></button>
            <h2 className="font-display-md text-[20px]">{navItems.find((item) => item.key === page)?.label}</h2>
          </div>
        </header>

        <div className="mx-auto max-w-[1200px] space-y-section p-lg md:p-xl">
          {managedOrganizations.length > 1 && (
            <div className="flex flex-wrap items-center gap-sm rounded-xl border border-hairline bg-white p-lg">
              <label className="whitespace-nowrap text-caption text-ink-muted">소속 조직</label>
              <select value={organizationId} onChange={(event) => changeOrganization(event.target.value)} className="min-w-[240px] rounded-lg border border-hairline bg-white px-md py-1.5 text-caption">
                {managedOrganizations.map((organization) => <option key={organization.id} value={organization.id}>{organization.name}</option>)}
              </select>
            </div>
          )}

          {eventsLoading ? (
            <div className="rounded-xl border border-hairline bg-white p-xl text-center text-caption text-ink-muted">등록한 행사를 불러오는 중입니다.</div>
          ) : managedEvents.length === 0 ? (
            <section className="rounded-2xl border border-hairline bg-white px-lg py-xxl text-center">
              <span className="mx-auto grid h-14 w-14 place-items-center rounded-full bg-primary/10 text-primary"><Icon name="event" className="text-[28px]" /></span>
              <h1 className="mt-md font-display-md text-[24px]">등록된 행사가 없습니다</h1>
              <p className="mt-xs text-caption text-ink-muted">행사를 등록하면 대시보드와 운영 메뉴를 사용할 수 있습니다.</p>
              {eventLoadError && <p className="mt-md text-caption text-error">{eventLoadError}</p>}
              <Link to={newEventPath} className="mt-lg inline-flex items-center gap-xs rounded-full bg-primary px-xl py-sm text-caption font-body-strong text-white"><Icon name="add" />첫 행사 등록하기</Link>
            </section>
          ) : !selectedEvent ? (
            <div className="rounded-xl border border-hairline bg-white p-xl text-center text-caption text-ink-muted">
              관리할 행사를 선택하는 중입니다.
            </div>
          ) : (
            <>
              <div className="flex flex-wrap items-center gap-sm rounded-xl border border-hairline bg-white p-lg">
                <label className="whitespace-nowrap text-caption text-ink-muted">관리 중인 행사</label>
                <select value={selectedEventId} onChange={(event) => changeEvent(event.target.value)} className="min-w-[260px] rounded-lg border border-hairline bg-white px-md py-1.5 text-caption">
                  {managedEvents.map((event) => <option key={event.id} value={event.id}>{event.name} · {EVENT_STATUS_LABEL[event.status] || event.status}</option>)}
                </select>
                {eventLoadError && <span className="text-caption text-error">{eventLoadError}</span>}
              </div>

              {page === "dashboard" && (
                <section className="space-y-xl">
                  <div>
                    <h1 className="font-display-lg text-[28px] md:text-display-lg">운영 현황</h1>
                    <p className="text-lead text-on-surface-variant">{selectedEvent?.name}</p>
                    <p className="mt-xs text-caption text-ink-muted">
                      {selectedEventDetail?.venueName || selectedEvent?.venueName} · {EVENT_STATUS_LABEL[selectedEvent?.status] || selectedEvent?.status}
                    </p>
                  </div>
                  {dashboardError && <p className="rounded-xl border border-status-pending/20 bg-status-pending/10 p-md text-caption text-status-pending">{dashboardError}</p>}
                  <div className="rounded-xl border border-hairline bg-white p-lg">
                    <div className="mb-md flex flex-wrap items-center justify-between gap-sm">
                      <div>
                        <p className="text-caption font-bold tracking-wider text-primary">SELECTED EVENT</p>
                        <h2 className="font-display-md text-[20px]">{selectedEvent.name}</h2>
                      </div>
                      <Link to={`/events/${selectedEventId}/admission`} className="inline-flex items-center gap-xs rounded-full border border-primary/30 px-md py-xs text-caption font-body-strong text-primary-focus hover:bg-primary-container/10">
                        <Icon name="qr_code_scanner" className="text-[16px]" /> 현장 입장 관리
                      </Link>
                      <Link to={`/organizer-admin/events/${selectedEventId}/edit?organizationId=${organizationId}`} className="inline-flex items-center gap-xs rounded-full border border-hairline px-md py-xs text-caption hover:bg-surface-container">
                        <Icon name="edit" className="text-[16px]" /> 행사 정보 수정
                      </Link>
                    </div>
                    <div className="grid gap-md text-caption sm:grid-cols-2 xl:grid-cols-4">
                      <p><span className="mb-1 block text-ink-muted">행사 기간</span>{formatDateTime(detail?.startAt)}<br />~ {formatDateTime(detail?.endAt)}</p>
                      <p><span className="mb-1 block text-ink-muted">행사 장소</span>{detail?.venueName || "-"}<br /><span className="text-ink-muted">{detail?.address || ""} {detail?.addressDetail || ""}</span></p>
                      <p><span className="mb-1 block text-ink-muted">입장권</span>{detail?.ticketPrice == null ? "-" : `${Number(detail.ticketPrice).toLocaleString()}원`}<br /><span className="text-ink-muted">판매 {displayCount(detail?.ticketSoldQuantity, dashboardLoading)} / 전체 {displayCount(detail?.ticketTotalQuantity, dashboardLoading)}</span></p>
                      <p><span className="mb-1 block text-ink-muted">문의</span>{detail?.contactEmail || "-"}<br /><span className="text-ink-muted">{detail?.contactPhone || "-"}</span></p>
                    </div>
                  </div>
                  <div className="grid grid-cols-1 gap-lg sm:grid-cols-2 xl:grid-cols-4">
                    {[
                      ["검토 필요 신청서", displayCount(dashboard.reviewRequired, dashboardLoading), "schedule", "text-status-pending"],
                      ["부스 배정 완료", dashboardLoading || dashboard.assignedBooths == null || dashboard.totalBooths == null ? "-" : `${dashboard.assignedBooths}/${dashboard.totalBooths}`, "grid_view", "text-status-assigned"],
                      ["판매된 입장권", displayCount(dashboard.ticketSoldQuantity, dashboardLoading), "confirmation_number", "text-status-available"],
                      ["오늘 부스 QR 스캔", displayCount(dashboard.todayBoothQrScans, dashboardLoading), "qr_code_scanner", "text-status-visited"],
                    ].map(([label, value, icon, color]) => (
                      <div key={label} className="rounded-xl border border-hairline bg-surface-pearl p-lg">
                        <div className="mb-md flex items-start justify-between"><span className="text-caption text-on-surface-variant">{label}</span><Icon name={icon} className={color} /></div>
                        <span className="font-display-md text-[26px]">{value}</span>
                      </div>
                    ))}
                  </div>
                  <div className="overflow-hidden rounded-xl border border-hairline bg-white">
                    <div className="flex items-center justify-between border-b border-hairline px-lg py-md">
                      <h3 className="font-body-strong">검토 필요 신청서</h3>
                      <button onClick={() => gotoPage("applications")} className="text-caption font-body-strong text-primary">전체 보기</button>
                    </div>
                    <div className="divide-y divide-divider-soft">
                      {!dashboardLoading && dashboard.applications.length === 0 ? (
                        <p className="p-lg text-caption text-ink-muted">검토할 신청서가 없습니다.</p>
                      ) : dashboard.applications.map((application) => (
                        <button key={application.id} type="button" onClick={() => gotoPage("applications")} className="flex w-full items-center gap-sm p-lg text-left hover:bg-surface-container-lowest">
                          <span className="grid h-10 w-10 place-items-center rounded-lg bg-surface-container"><Icon name="description" className="text-[18px] text-ink-muted" /></span>
                          <span className="min-w-0 flex-1"><span className="block truncate font-body-strong text-[14px]">{application.teamName}</span><span className="text-caption text-ink-muted">신청번호 {application.applicationNo} · 부스 #{application.boothId}</span></span>
                          <span className="text-caption text-status-pending">{APPLICATION_STATUS_LABEL[application.status] || application.status}</span>
                        </button>
                      ))}
                    </div>
                  </div>

                  {/* STAT-API-004: 행사 운영 통계 요약 (최근 30일) */}
                  <div className="overflow-hidden rounded-xl border border-hairline bg-white">
                    <div className="flex items-center justify-between border-b border-hairline px-lg py-md">
                      <h3 className="font-body-strong">행사 운영 통계 요약</h3>
                      <span className="text-caption text-ink-muted">최근 30일</span>
                    </div>
                    {statError && <p className="px-lg pt-md text-caption text-error">{statError}</p>}
                    <div className="grid grid-cols-1 gap-lg p-lg sm:grid-cols-3">
                      {[
                        ["누적 예약", overview?.totalReservationCount, "event_available", "text-status-available"],
                        ["누적 QR 스캔", overview?.totalQrScanCount, "qr_code_scanner", "text-status-visited"],
                        ["누적 노쇼", overview?.totalNoShowCount, "person_off", "text-status-pending"],
                      ].map(([label, value, icon, color]) => (
                        <div key={label} className="rounded-xl border border-hairline bg-surface-pearl p-lg">
                          <div className="mb-md flex items-start justify-between"><span className="text-caption text-on-surface-variant">{label}</span><Icon name={icon} className={color} /></div>
                          <span className="font-display-md text-[26px]">{displayCount(value, statLoading)}</span>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* STAT-API-003: 기간별 인기 부스 (예약 확정 수 기준) */}
                  <div className="overflow-hidden rounded-xl border border-hairline bg-white">
                    <div className="flex items-center justify-between border-b border-hairline px-lg py-md">
                      <h3 className="font-body-strong">인기 부스 TOP 5</h3>
                      <span className="text-caption text-ink-muted">최근 30일 예약 수 기준</span>
                    </div>
                    <div className="divide-y divide-divider-soft">
                      {statLoading ? (
                        <p className="p-lg text-caption text-ink-muted">통계를 불러오는 중입니다.</p>
                      ) : popularBooths.length === 0 ? (
                        <p className="p-lg text-caption text-ink-muted">집계된 부스 통계가 없습니다.</p>
                      ) : popularBooths.slice(0, 5).map((booth) => (
                        <div key={booth.boothId} className="flex items-center gap-md p-lg">
                          <span className={`grid h-7 w-7 place-items-center rounded-full text-[12px] font-bold ${booth.rank <= 3 ? "bg-primary text-white" : "bg-surface-container text-ink-muted"}`}>
                            {booth.rank}
                          </span>
                          <span className="flex-1 font-body-strong text-[14px]">{booth.boothCode} 부스</span>
                          <span className="text-caption text-ink-muted">예약 {Number(booth.totalReservationCount || 0).toLocaleString()}건</span>
                          <span className="text-caption text-ink-muted">방문 {Number(booth.totalQrScanCount || 0).toLocaleString()}건</span>
                        </div>
                      ))}
                    </div>
                  </div>

                  {/* STAT-API-001/002: 부스별 시간대 통계 + 전날 통계 */}
                  <div className="overflow-hidden rounded-xl border border-hairline bg-white">
                    <div className="flex flex-wrap items-center justify-between gap-sm border-b border-hairline px-lg py-md">
                      <h3 className="font-body-strong">부스별 상세 통계</h3>
                      <div className="flex items-center gap-sm">
                        {/* 부스 목록은 overview 응답의 boothSummaries 재사용 (추가 API 호출 없음) */}
                        {/* 시각적 레이아웃을 유지하면서 스크린 리더에 컨트롤 목적을 알리기 위해 sr-only label 사용 */}
                        <label htmlFor="stat-booth-select" className="sr-only">통계를 조회할 부스</label>
                        <select
                          id="stat-booth-select"
                          value={statBoothId}
                          onChange={(event) => setStatBoothId(event.target.value)}
                          className="h-[36px] rounded-lg border border-hairline bg-white px-sm text-caption"
                        >
                          <option value="">부스 선택</option>
                          {(overview?.boothSummaries || []).map((booth) => (
                            <option key={booth.boothId} value={booth.boothId}>{booth.boothCode} 부스</option>
                          ))}
                        </select>
                        <label htmlFor="stat-hourly-date" className="sr-only">시간대별 통계 조회 날짜</label>
                        <input
                          id="stat-hourly-date"
                          type="date"
                          value={hourlyDate}
                          onChange={(event) => setHourlyDate(event.target.value)}
                          className="h-[36px] rounded-lg border border-hairline bg-white px-sm text-caption"
                        />
                      </div>
                    </div>
                    <div className="space-y-lg p-lg">
                      {!statBoothId ? (
                        <p className="text-caption text-ink-muted">부스를 선택하면 전날 통계와 시간대별 추이가 표시됩니다.</p>
                      ) : boothStatLoading ? (
                        <p className="text-caption text-ink-muted">통계를 불러오는 중입니다.</p>
                      ) : (
                        <>
                          {/* STAT-API-002 전날 통계 - 조회 실패와 데이터 없음을 구분해 표시한다 */}
                          <div>
                            <p className="mb-sm text-caption font-bold tracking-wider text-primary">
                              전날 통계{prevDayStat?.statDate ? ` · ${prevDayStat.statDate}` : ""}
                            </p>
                            {prevDayError ? (
                              <p className="text-caption text-error">{prevDayError}</p>
                            ) : prevDayStat ? (
                              <div className="grid grid-cols-3 gap-md">
                                {[
                                  ["예약", prevDayStat.totalReservationCount],
                                  ["QR 스캔", prevDayStat.totalQrScanCount],
                                  ["노쇼", prevDayStat.totalNoShowCount],
                                ].map(([label, value]) => (
                                  <div key={label} className="rounded-lg border border-hairline bg-surface-pearl p-md text-center">
                                    <span className="block text-caption text-ink-muted">{label}</span>
                                    <span className="font-display-md text-[20px]">{Number(value ?? 0).toLocaleString()}</span>
                                  </div>
                                ))}
                              </div>
                            ) : (
                              <p className="text-caption text-ink-muted">전날 집계된 통계가 없습니다.</p>
                            )}
                          </div>

                          {/* STAT-API-001 시간대별 통계 - 조회 실패와 데이터 없음을 구분해 표시한다 */}
                          <div>
                            <p className="mb-sm text-caption font-bold tracking-wider text-primary">시간대별 추이 · {hourlyDate}</p>
                            {hourlyError ? (
                              <p className="text-caption text-error">{hourlyError}</p>
                            ) : hourlyStats.length === 0 ? (
                              <p className="text-caption text-ink-muted">해당 날짜에 집계된 통계가 없습니다.</p>
                            ) : (
                              <HourlyBarChart stats={hourlyStats} date={hourlyDate} />
                            )}
                          </div>
                        </>
                      )}
                    </div>
                  </div>
                </section>
              )}

              {page === "recruitment" && <RecruitmentManagementPanel eventId={selectedEventId} />}
              {page === "applications" && <OrganizerApplicationPanel eventId={selectedEventId} onDataChanged={refreshDashboard} />}
              {page === "assignment" && <BoothManagementPanel eventId={selectedEventId} />}
              {page === "floorplan" && <FloorplanManagementPanel eventId={selectedEventId} />}
              {page === "exchange-codes" && <OrganizerExchangeCodeRequestPanel eventId={selectedEventId} />}

              {page === "approval" && (
                <section className="space-y-lg">
                  <div className="flex flex-wrap items-end justify-between gap-md">
                    <div><h1 className="font-display-lg text-[26px]">행사 등록 승인 요청</h1><p className="mt-xs text-caption text-ink-muted">등록된 운영 정보를 기준으로 준비 상태를 확인합니다.</p></div>
                    <div className="flex gap-sm">
                      <Link to={`/organizer-admin/events/${selectedEventId}/edit?organizationId=${organizationId}`} className="rounded-full border border-hairline px-md py-xs text-caption">행사 수정</Link>
                      <Link to={`/organizer-admin/events/${selectedEventId}/members?organizationId=${organizationId}`} className="rounded-full border border-hairline px-md py-xs text-caption">담당자 관리</Link>
                    </div>
                  </div>

                  <div className="rounded-xl border border-primary-fixed bg-primary-fixed/20 p-lg">
                    <div className="flex flex-wrap items-center justify-between gap-lg">
                      <div>
                        <p className="font-body-strong">{selectedEvent.name} · {EVENT_STATUS_LABEL[selectedEvent.status] || selectedEvent.status}</p>
                        <p className="mt-1 text-caption text-ink-muted">{approvalGuide}</p>
                      </div>
                      {selectedEvent.status === "APPROVED" ? (
                        <button onClick={publishSelectedEvent} disabled={publishingEvent} className="rounded-full bg-primary px-xl py-sm font-body-strong text-white disabled:opacity-40">{publishingEvent ? "공개 중..." : "행사 공개하기"}</button>
                      ) : selectedEvent.status === "PUBLISHED" ? (
                        <button disabled className="cursor-default rounded-full bg-status-available px-xl py-sm font-body-strong text-white opacity-80">공개 중</button>
                      ) : (
                        <button onClick={submitSelectedEvent} disabled={!approvalRequestReady || submittingEvent} className="rounded-full bg-primary px-xl py-sm font-body-strong text-white disabled:cursor-not-allowed disabled:opacity-40">{submittingEvent ? "요청 중..." : "승인 요청하기"}</button>
                      )}
                    </div>
                    <div className="mt-lg h-2 overflow-hidden rounded-full bg-white/80"><div className="h-full rounded-full bg-primary transition-all" style={{ width: `${preparationPercent}%` }} /></div>
                    <p className="mt-xs text-right text-caption text-ink-muted">운영 정보 준비 {preparationPercent}% ({completedPreparationCount}/{preparationChecks.length})</p>
                  </div>

                  {approvalLoading && <p className="text-caption text-ink-muted">준비 상태를 확인하는 중입니다.</p>}
                  {approvalError && <p className="rounded-xl border border-error/20 bg-error/10 p-md text-caption text-error">{approvalError}</p>}
                  <div className="divide-y divide-divider-soft rounded-xl border border-hairline bg-white">
                    {preparationChecks.map((check) => (
                      <div key={check.key} className="flex items-center gap-sm p-lg">
                        <span className={`grid h-6 w-6 place-items-center rounded-full text-white ${check.completed ? "bg-status-available" : "bg-status-pending"}`}><Icon name={check.completed ? "check" : "schedule"} className="text-[14px]" /></span>
                        {check.label}<span className="ml-auto text-caption text-ink-muted">{check.description}</span>
                      </div>
                    ))}
                    <div className="flex items-center gap-sm p-lg">
                      <span className={`grid h-6 w-6 place-items-center rounded-full text-white ${platformApproved ? "bg-status-available" : "bg-status-blocked"}`}><Icon name={platformApproved ? "check" : "chevron_right"} className="text-[14px]" /></span>
                      플랫폼 관리자 승인
                      <span className="ml-auto text-caption text-ink-muted">{platformApproved ? "승인 완료" : selectedEvent.status === "REJECTED" ? "반려" : "대기"}</span>
                    </div>
                  </div>
                  <p className="text-[11px] text-ink-muted">준비도는 현재 조회 가능한 운영 정보의 표시값입니다. 최종 승인 요청 가능 여부는 서버의 행사 승인 규칙을 따릅니다.</p>
                </section>
              )}
            </>
          )}
        </div>
      </main>
    </div>
  );
}
// 시간대별 통계 막대 차트 (STAT-API-001)
// 별도 차트 라이브러리 없이 CSS 높이 비율로 표현 (예약=파랑, QR 방문=초록)
function HourlyBarChart({ stats, date }) {
  // 마우스를 올린 시간대 (커스텀 툴팁 표시용)
  const [hoveredHour, setHoveredHour] = useState(null);
  // 0~23시 전체 축을 만들고 데이터가 있는 시간대만 값 채움
  const byHour = new Map(stats.map((s) => [s.statHour, s]));
  const hours = Array.from({ length: 24 }, (_, h) => byHour.get(h) || { statHour: h, reservationCount: 0, noShowCount: 0, qrScanCount: 0 });
  const max = Math.max(1, ...hours.map((s) => Math.max(s.reservationCount, s.qrScanCount, s.noShowCount)));
  // 하루 합산 (예약·QR 방문·노쇼)
  const totals = stats.reduce(
    (acc, s) => ({
      reservation: acc.reservation + s.reservationCount,
      qrScan: acc.qrScan + s.qrScanCount,
      noShow: acc.noShow + s.noShowCount,
    }),
    { reservation: 0, qrScan: 0, noShow: 0 }
  );
  return (
    <div className="space-y-md">
      {/* 합산 카드: 선택한 날짜의 예약·방문·노쇼 총합 */}
      <div className="grid grid-cols-3 gap-sm">
        <div className="bg-surface-pearl rounded-lg p-md text-center">
          <p className="text-caption text-ink-muted">예약</p>
          <p className="font-display-md text-[20px] text-primary">{totals.reservation.toLocaleString()}</p>
        </div>
        <div className="bg-surface-pearl rounded-lg p-md text-center">
          <p className="text-caption text-ink-muted">QR 방문</p>
          <p className="font-display-md text-[20px] text-status-available">{totals.qrScan.toLocaleString()}</p>
        </div>
        <div className="bg-surface-pearl rounded-lg p-md text-center">
          <p className="text-caption text-ink-muted">노쇼</p>
          <p className="font-display-md text-[20px] text-status-visited">{totals.noShow.toLocaleString()}</p>
        </div>
      </div>
      {/* 범례 */}
      <div className="flex gap-lg text-caption text-ink-muted">
        <span className="flex items-center gap-xs"><span className="w-3 h-3 rounded-sm bg-primary inline-block" /> 예약</span>
        <span className="flex items-center gap-xs"><span className="w-3 h-3 rounded-sm bg-status-available inline-block" /> QR 방문</span>
        <span className="flex items-center gap-xs"><span className="w-3 h-3 rounded-sm bg-status-visited inline-block" /> 노쇼</span>
      </div>
      {/* 막대 차트: 시간대별 예약·방문 2개 막대
          %높이는 flex 안에서 계산이 불안정해 막대가 기준선을 벗어나는 문제가 있어
          픽셀 단위로 직접 계산한다 (최대값 = 120px)
          막대는 시각 표현이므로 aria-hidden 처리하고, 같은 수치를 아래 sr-only 표로 제공한다 */}
      <div className="flex gap-[3px] pt-[36px]">
        {hours.map((s) => (
          <div
            key={s.statHour}
            // 키보드 포커스와 터치에서도 툴팁 수치를 확인할 수 있도록 버튼처럼 포커스 가능하게 한다
            tabIndex={0}
            role="img"
            aria-label={`${s.statHour}시 예약 ${s.reservationCount}건, QR 방문 ${s.qrScanCount}건, 노쇼 ${s.noShowCount}건`}
            className="flex-1 min-w-0 relative focus:outline-none focus-visible:ring-2 focus-visible:ring-primary-focus rounded-sm"
            onMouseEnter={() => setHoveredHour(s.statHour)}
            onMouseLeave={() => setHoveredHour(null)}
            onFocus={() => setHoveredHour(s.statHour)}
            onBlur={() => setHoveredHour(null)}
          >
            {/* 마우스 오버·포커스 시 해당 시간대 숫자 툴팁 표시 */}
            {hoveredHour === s.statHour && (
              <div className="absolute -top-[34px] left-1/2 -translate-x-1/2 z-10 bg-on-surface text-white text-[11px] rounded-lg px-sm py-xs whitespace-nowrap pointer-events-none shadow-md">
                {s.statHour}시 · 예약 {s.reservationCount} · 방문 {s.qrScanCount} · 노쇼 {s.noShowCount}
              </div>
            )}
            <div aria-hidden="true" className={`flex items-end justify-center gap-[2px] h-[120px] border-b border-hairline transition-colors ${hoveredHour === s.statHour ? "bg-surface-pearl" : ""}`}>
              <div className="w-1/3 max-w-[8px] bg-primary rounded-t-sm" style={{ height: `${Math.round((s.reservationCount / max) * 120)}px` }} />
              <div className="w-1/3 max-w-[8px] bg-status-available rounded-t-sm" style={{ height: `${Math.round((s.qrScanCount / max) * 120)}px` }} />
              <div className="w-1/3 max-w-[8px] bg-status-visited rounded-t-sm" style={{ height: `${Math.round((s.noShowCount / max) * 120)}px` }} />
            </div>
            {/* 3시간 간격으로만 라벨 표시 (24개 전부 표시하면 좁아서 겹침) */}
            <p aria-hidden="true" className="text-[9px] text-ink-muted text-center mt-[2px] h-[12px]">{s.statHour % 3 === 0 ? s.statHour : ""}</p>
          </div>
        ))}
      </div>

      {/* 스크린 리더용 데이터 표 - 막대 높이로는 전달되지 않는 수치를 동일하게 제공한다 */}
      <table className="sr-only">
        <caption>{date ? `${date} ` : ""}시간대별 예약·QR 방문·노쇼 수</caption>
        <thead>
          <tr>
            <th scope="col">시간</th>
            <th scope="col">예약</th>
            <th scope="col">QR 방문</th>
            <th scope="col">노쇼</th>
          </tr>
        </thead>
        <tbody>
          {hours.map((s) => (
            <tr key={s.statHour}>
              <th scope="row">{s.statHour}시</th>
              <td>{s.reservationCount}</td>
              <td>{s.qrScanCount}</td>
              <td>{s.noShowCount}</td>
            </tr>
          ))}
        </tbody>
      </table>
    </div>
  );
}
