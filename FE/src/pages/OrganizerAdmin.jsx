import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import TopNav from "../components/TopNav.jsx";
import RecruitmentManagementPanel from "../components/RecruitmentManagementPanel.jsx";
import BoothManagementPanel from "../components/BoothManagementPanel.jsx";
import FloorplanManagementPanel from "../components/FloorplanManagementPanel.jsx";
import { eventApi } from "../api/eventApi.js";
import { getManagementRecruitment } from "../api/recruitmentApi.js";

const navItems = [
  { key: "dashboard", label: "대시보드", icon: "dashboard" },
  { key: "recruitment", label: "부스 모집 공고", icon: "campaign" },
  { key: "applications", label: "부스 신청서 검토", icon: "assignment" },
  { key: "assignment", label: "부스 관리", icon: "grid_view" },
  { key: "floorplan", label: "평면도 관리", icon: "map" },
  { key: "approval", label: "행사 등록 승인 요청", icon: "verified" },
];
const initialApplications = [
  { id: 1, company: "그린 키친랩", booth: "A03", submitted: "2026.07.20", files: 2, status: "pending" },
  { id: 2, company: "베이크하우스", booth: "A05", submitted: "2026.07.18", files: 3, status: "approved" },
  { id: 3, company: "콜드체인 솔루션", booth: "A07", submitted: "2026.07.19", files: 1, status: "rejected" },
  { id: 4, company: "스마트키친 로보틱스", booth: "A09", submitted: "2026.07.21", files: 2, status: "pending" },
];
const initialAssignBooths = [
  { id: "A01", status: "available" }, { id: "A02", status: "assigned" }, { id: "A03", status: "pending" },
  { id: "A04", status: "available" }, { id: "A05", status: "assigned" }, { id: "A06", status: "available" },
  { id: "A07", status: "blocked" }, { id: "A08", status: "assigned" }, { id: "A09", status: "available" }, { id: "A10", status: "assigned" },
];
const statusLabel = { available: "선택 가능", pending: "신청 대기", assigned: "배정 완료", rejected: "반려됨", approved: "승인 완료", blocked: "사용 불가" };

export default function OrganizerAdmin() {
  const [page, setPage] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [applications, setApplications] = useState(initialApplications);
  const [assignBooths, setAssignBooths] = useState(initialAssignBooths);
  const [query] = useSearchParams();
  const requestedOrganizationId = query.get("organizationId") || "";
  const requestedEventId = query.get("eventId") || "";
  const [organizationId, setOrganizationId] = useState(requestedOrganizationId || localStorage.getItem("organizationId") || "");
  const [managedOrganizations, setManagedOrganizations] = useState([]);
  const [managedEvents, setManagedEvents] = useState([]);
  const [selectedEventId, setSelectedEventId] = useState("");
  const [eventLoadError, setEventLoadError] = useState("");
  const [submittingEvent, setSubmittingEvent] = useState(false);
  const [publishingEvent, setPublishingEvent] = useState(false);
  const [recruitmentStatus, setRecruitmentStatus] = useState(null);
  const [recruitmentLoading, setRecruitmentLoading] = useState(false);

  // 내 계정이 속한 조직 목록을 불러와, URL/localStorage의 organizationId가 없거나
  // 더 이상 내 소속이 아니면(다른 계정으로 로그인 등) 자동으로 첫 번째 소속 조직으로 교체한다.
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
        const isValid = list.some((org) => String(org.id) === String(organizationId));
        if (!isValid) {
          setOrganizationId(String(list[0].id));
          setSelectedEventId("");
        }
      })
      .catch((error) => setEventLoadError(error.message || "소속 조직 정보를 불러오지 못했습니다."));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (requestedOrganizationId
        && managedOrganizations.some((org) => String(org.id) === requestedOrganizationId)) {
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
      return;
    }
    let active = true;
    setManagedEvents([]);
    setSelectedEventId("");
    setEventLoadError("");
    eventApi.organizationList(organizationId, { size: 100, sort: "createdAt,desc" })
      .then((result) => {
        if (!active) return;
        const list = result?.data?.content || [];
        setManagedEvents(list);
      })
      .catch((error) => {
        if (active) setEventLoadError(error.message || "행사 목록을 불러오지 못했습니다.");
      });
    return () => { active = false; };
  }, [organizationId]);

  useEffect(() => {
    if (managedEvents.length === 0) {
      setSelectedEventId("");
      return;
    }
    const requestedEventExists = requestedEventId
      && managedEvents.some((event) => String(event.id) === requestedEventId);
    setSelectedEventId(requestedEventExists
      ? requestedEventId
      : String(managedEvents[0].id));
  }, [managedEvents, requestedEventId]);

  const selectedEvent = managedEvents.find((event) => String(event.id) === String(selectedEventId));

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
  }, [selectedEvent?.id, selectedEvent?.boothRecruitmentEnabled]);

  const submitSelectedEvent = async () => {
    if (!selectedEventId) return;
    setSubmittingEvent(true);
    setEventLoadError("");
    try {
      await eventApi.submit(selectedEventId);
      setManagedEvents((previous) => previous.map((event) =>
        String(event.id) === String(selectedEventId) ? { ...event, status: "SUBMITTED" } : event));
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
    } catch (error) {
      setEventLoadError(error.message || "행사 공개에 실패했습니다.");
    } finally {
      setPublishingEvent(false);
    }
  };

  const pending = useMemo(() => applications.filter((a) => a.status === "pending"), [applications]);
  const assignedCount = assignBooths.filter((b) => b.status === "assigned").length;
  const allReviewed = pending.length === 0;
  const requestableStatus = ["PREPARING", "REJECTED"].includes(selectedEvent?.status);
  const platformApproved = ["APPROVED", "PUBLISHED"].includes(selectedEvent?.status);
  const recruitmentReady = !selectedEvent?.boothRecruitmentEnabled || recruitmentStatus === "COMPLETED";
  const approvalRequestReady = Boolean(selectedEventId) && requestableStatus && recruitmentReady && !recruitmentLoading;
  const approvalGuide = !selectedEvent
    ? "행사를 선택해 주세요."
    : !requestableStatus
      ? selectedEvent.status === "SUBMITTED" || selectedEvent.status === "UNDER_REVIEW"
        ? "플랫폼 관리자 검토를 기다리고 있습니다."
        : platformApproved
          ? selectedEvent.status === "PUBLISHED"
            ? "행사가 공개되어 사용자 행사 목록에 노출되고 있습니다."
            : "플랫폼 관리자 승인이 완료되었습니다. 행사를 공개할 수 있습니다."
          : `현재 ${selectedEvent.status} 상태에서는 승인 요청을 할 수 없습니다.`
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

  const decide = (id, decision) => {
    setApplications((prev) => prev.map((a) => (a.id === id ? { ...a, status: decision } : a)));
    const app = applications.find((a) => a.id === id);
    if (app) {
      setAssignBooths((prev) =>
        prev.map((b) => (b.id === app.booth ? { ...b, status: decision === "approved" ? "assigned" : "available" } : b))
      );
    }
  };

  const gotoPage = (key) => {
    setPage(key);
    if (window.innerWidth < 768) setSidebarOpen(false);
  };

  const navBtnCls = (active) =>
    `group w-full flex items-center gap-sm border-l-[3px] px-md py-sm rounded-r-xl font-body text-left transition-all ${
      active ? "border-primary bg-primary/10 text-primary font-body-strong shadow-sm" : "border-transparent text-on-surface-variant hover:border-primary/30 hover:bg-surface-container"
    }`;

  const AppRow = ({ a }) => (
    <div className="flex items-center gap-sm p-lg">
      <div className="w-10 h-10 rounded-lg bg-surface-container flex items-center justify-center flex-shrink-0"><Icon name="description" className="text-[18px] text-ink-muted" /></div>
      <div className="flex-1 min-w-0">
        <p className="font-body-strong text-[14px]">{a.company} · {a.booth} 부스</p>
        <p className="text-caption text-ink-muted">제출일 {a.submitted} · 첨부 {a.files}건</p>
      </div>
      {a.status === "pending" ? (
        <div className="flex gap-xs ml-auto">
          <button onClick={() => decide(a.id, "approved")} className="w-8 h-8 rounded-full bg-status-available/10 text-status-available flex items-center justify-center"><Icon name="check" className="text-[16px]" /></button>
          <button onClick={() => decide(a.id, "rejected")} className="w-8 h-8 rounded-full bg-status-visited/10 text-status-visited flex items-center justify-center"><Icon name="close" className="text-[16px]" /></button>
        </div>
      ) : (
        <span className={`ml-auto text-[11px] font-bold px-sm py-1 rounded-full ${a.status === "approved" ? "bg-status-available/10 text-status-available" : "bg-status-visited/10 text-status-visited"}`}>
          {statusLabel[a.status]}
        </span>
      )}
    </div>
  );

  return (
    <div className="min-h-screen bg-surface-container-lowest pt-[44px] text-on-surface">
      <TopNav active="organizer" />
      {/* Sidebar */}
      <aside className={`fixed bottom-0 left-0 top-[44px] w-[280px] bg-white border-r border-hairline z-50 flex flex-col shadow-[12px_0_40px_rgba(15,23,42,0.04)] transition-transform md:translate-x-0 ${sidebarOpen ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="border-b border-hairline bg-gradient-to-br from-primary/10 via-white to-primary-container/10 px-lg py-lg flex items-start justify-between">
          <div>
            <span className="text-[10px] font-bold tracking-[0.18em] text-primary">EVENTODAY</span>
            <h1 className="mt-1 font-display-md text-[20px]">개최자센터</h1>
            <p className="mt-1 text-[11px] text-ink-muted">행사 운영을 한곳에서 관리하세요</p>
          </div>
          <button type="button" aria-label="개최자센터 메뉴 닫기" onClick={() => setSidebarOpen(false)} className="md:hidden"><Icon name="close" /></button>
        </div>
        <div className="px-md pt-md">
          <Link to={`/organizer-admin/events/new?organizationId=${organizationId || ""}`} className="flex w-full items-center justify-center gap-xs rounded-xl bg-primary px-md py-sm text-caption font-body-strong text-white shadow-sm transition hover:brightness-95">
            <Icon name="add_circle" className="text-[18px]" /> 새 행사 등록
          </Link>
        </div>
        <nav className="flex-1 overflow-y-auto px-md py-md space-y-1" aria-label="개최자센터 메뉴">
          <p className="px-sm pb-xs text-[10px] font-bold tracking-[0.14em] text-ink-muted">행사 운영</p>
          {navItems.map((n) => (
            <button key={n.key} onClick={() => gotoPage(n.key)} className={navBtnCls(page === n.key)}>
              <Icon name={n.icon} /><span>{n.label}</span>
            </button>
          ))}
          <p className="px-sm pb-xs pt-md text-[10px] font-bold tracking-[0.14em] text-ink-muted">홍보</p>
          <Link to={`/organizer-admin/advertisements?organizationId=${organizationId || ""}`} className={navBtnCls(false)}>
            <Icon name="ads_click" /><span>광고 신청·관리</span>
          </Link>
        </nav>
        <div className="p-lg border-t border-hairline space-y-4">
          <div className="flex items-center gap-sm">
            <div className="w-8 h-8 rounded-full bg-surface-container-high flex items-center justify-center"><Icon name="person" className="text-[18px]" /></div>
            <div className="flex flex-col"><span className="text-caption font-body-strong">김운영 매니저</span><span className="text-[10px] text-ink-muted">Organizer Admin</span></div>
          </div>
          <Link to="/" className="block w-full py-xs text-center text-caption text-secondary border border-hairline rounded-lg hover:bg-surface-container transition-colors">메인 사이트로</Link>
        </div>
      </aside>
      {sidebarOpen && <div onClick={() => setSidebarOpen(false)} className="fixed inset-0 bg-black/40 z-40 md:hidden" />}

      {/* Main */}
      <main className="min-h-[calc(100vh-44px)] md:ml-[280px]">
        <header className="sticky top-[44px] z-30 bg-white/70 backdrop-blur-xl border-b border-hairline px-lg h-[64px] flex items-center justify-between">
          <div className="flex items-center gap-sm">
            <button onClick={() => setSidebarOpen(true)} className="md:hidden"><Icon name="menu" /></button>
            <h2 className="font-display-md text-[20px] text-on-surface">{navItems.find((n) => n.key === page).label}</h2>
          </div>
          <Link to={`/organizer-admin/events/new?organizationId=${organizationId || ""}`} className="px-md py-xs bg-primary text-white text-caption rounded-full font-body-strong active:scale-95 transition-transform">+ 새 전시회 등록</Link>
        </header>

        <div className="p-lg md:p-xl space-y-section max-w-[1200px] mx-auto">
          {managedOrganizations.length > 1 && (
            <div className="bg-white border border-hairline rounded-xl p-lg flex items-center gap-sm flex-wrap">
              <label className="text-caption text-ink-muted whitespace-nowrap">소속 조직</label>
              <select
                value={organizationId}
                onChange={(event) => {
                  setOrganizationId(event.target.value);
                  setSelectedEventId("");
                }}
                className="border border-hairline rounded-lg px-md py-1.5 text-caption bg-white min-w-[240px]"
              >
                {managedOrganizations.map((org) => (
                  <option key={org.id} value={org.id}>{org.name}</option>
                ))}
              </select>
            </div>
          )}
          {managedOrganizations.length === 0 && eventLoadError && (
            <div className="bg-white border border-hairline rounded-xl p-lg text-caption text-error">
              {eventLoadError}
            </div>
          )}
          {page !== "dashboard" && (
            <div className="bg-white border border-hairline rounded-xl p-lg flex items-center gap-sm flex-wrap">
              <label className="text-caption text-ink-muted whitespace-nowrap">관리 중인 행사</label>
              <select
                value={selectedEventId}
                onChange={(event) => setSelectedEventId(event.target.value)}
                className="border border-hairline rounded-lg px-md py-1.5 text-caption bg-white min-w-[240px]"
              >
                <option value="">행사를 선택하세요</option>
                {managedEvents.map((event) => (
                  <option key={event.id} value={event.id}>{event.name} · {event.status}</option>
                ))}
              </select>
              {eventLoadError && <span className="text-caption text-error">{eventLoadError}</span>}
            </div>
          )}

          {/* DASHBOARD */}
          {page === "dashboard" && (
            <section className="space-y-xl">
              <div>
                <h1 className="font-display-lg text-[28px] md:text-display-lg text-on-surface">운영 현황</h1>
                <p className="text-lead text-on-surface-variant">2026 서울 푸드테크 박람회</p>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-lg">
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">검토 대기 신청서</span><Icon name="schedule" className="text-status-pending" /></div>
                  <span className="font-display-md text-[26px]">{pending.length}</span>
                </div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">부스 배정 완료</span><Icon name="grid_view" className="text-status-assigned" /></div>
                  <span className="font-display-md text-[26px]">{assignedCount}/{assignBooths.length}</span>
                </div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">누적 예매자 수</span><Icon name="group" className="text-status-available" /></div>
                  <span className="font-display-md text-[26px]">482</span>
                </div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">오늘 방문자 수</span><Icon name="trending_up" className="text-status-visited" /></div>
                  <span className="font-display-md text-[26px]">128</span>
                </div>
              </div>
              <div className="bg-white border border-hairline rounded-xl overflow-hidden">
                <div className="flex justify-between items-center px-lg py-md border-b border-hairline">
                  <h3 className="font-body-strong">검토 대기 신청서</h3>
                  <button onClick={() => gotoPage("applications")} className="text-caption text-primary font-body-strong">전체 보기</button>
                </div>
                <div className="divide-y divide-divider-soft">
                  {pending.length === 0 ? (
                    <p className="p-lg text-caption text-ink-muted">검토할 신청서가 없습니다.</p>
                  ) : (
                    pending.map((a) => <AppRow key={a.id} a={a} />)
                  )}
                </div>
              </div>
            </section>
          )}

          {/* RECRUITMENT */}
          {page === "recruitment" && <RecruitmentManagementPanel eventId={selectedEventId} />}

          {/* APPLICATIONS */}
          {page === "applications" && (
            <section className="space-y-lg">
              <div>
                <h1 className="font-display-lg text-[26px]">부스 신청서 검토</h1>
                <p className="text-caption text-ink-muted mt-xs">승인하면 부스가 자동 배정되고, 반려하면 다시 선택 가능 상태로 복원됩니다.</p>
              </div>
              <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
                {applications.map((a) => <AppRow key={a.id} a={a} />)}
              </div>
            </section>
          )}

          {/* ASSIGNMENT */}
          {page === "assignment" && <BoothManagementPanel eventId={selectedEventId} />}

          {/* FLOORPLAN */}
          {page === "floorplan" && <FloorplanManagementPanel eventId={selectedEventId} />}

          {/* APPROVAL */}
          {page === "approval" && (
            <section className="space-y-lg">
              <h1 className="font-display-lg text-[26px]">행사 등록 승인 요청</h1>
              {selectedEventId && (
                <div className="bg-white border border-hairline rounded-xl p-lg flex gap-sm">
                  <Link to={`/organizer-admin/events/${selectedEventId}/edit?organizationId=${organizationId || ""}`} className="text-caption px-md py-xs border border-hairline rounded-full">행사 수정</Link>
                  <Link to={`/organizer-admin/events/${selectedEventId}/members?organizationId=${organizationId || ""}`} className="text-caption px-md py-xs border border-hairline rounded-full">담당자 관리</Link>
                </div>
              )}
              <div className="bg-primary-fixed/20 border border-primary-fixed rounded-xl p-lg flex items-center justify-between gap-lg flex-wrap">
                <div>
                  <p className="font-body-strong">{selectedEvent ? `${selectedEvent.name} · ${selectedEvent.status}` : "행사를 선택해주세요"}</p>
                  <p className="text-caption text-ink-muted mt-1">
                    {approvalGuide}
                  </p>
                </div>
                {selectedEvent?.status === "APPROVED" ? (
                  <button onClick={publishSelectedEvent} disabled={publishingEvent} className="px-xl py-sm bg-primary text-white rounded-full font-body-strong disabled:opacity-40 disabled:cursor-not-allowed">
                    {publishingEvent ? "공개 중..." : "행사 공개하기"}
                  </button>
                ) : selectedEvent?.status === "PUBLISHED" ? (
                  <button disabled className="px-xl py-sm bg-status-available text-white rounded-full font-body-strong opacity-80 cursor-default">공개 중</button>
                ) : (
                  <button onClick={submitSelectedEvent} disabled={!approvalRequestReady || submittingEvent} className="px-xl py-sm bg-primary text-white rounded-full font-body-strong disabled:opacity-40 disabled:cursor-not-allowed">{submittingEvent ? "요청 중..." : "승인 요청하기"}</button>
                )}
              </div>
              <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
                <div className="flex items-center gap-sm p-lg"><span className="w-6 h-6 rounded-full bg-status-available text-white flex items-center justify-center"><Icon name="check" className="text-[14px]" /></span>행사 기본정보 등록 <span className="text-ink-muted text-caption ml-auto">완료</span></div>
                <div className="flex items-center gap-sm p-lg">
                  <span className={`w-6 h-6 rounded-full text-white flex items-center justify-center ${selectedEvent?.venueMapEnabled ? "bg-status-pending" : "bg-status-available"}`}>
                    <Icon name={selectedEvent?.venueMapEnabled ? "schedule" : "check"} className="text-[14px]" />
                  </span>
                  행사장 평면도 및 부스 좌표 등록
                  <span className="text-ink-muted text-caption ml-auto">{selectedEvent?.venueMapEnabled ? "평면도 파트 연동 필요" : "해당 없음"}</span>
                </div>
                <div className="flex items-center gap-sm p-lg">
                  <span className={`w-6 h-6 rounded-full text-white flex items-center justify-center ${recruitmentReady ? "bg-status-available" : "bg-status-pending"}`}><Icon name={recruitmentReady ? "check" : "schedule"} className="text-[14px]" /></span>
                  부스 모집 공고 완료 <span className="text-ink-muted text-caption ml-auto">{!selectedEvent?.boothRecruitmentEnabled ? "해당 없음" : recruitmentStatus === "COMPLETED" ? "완료" : recruitmentStatus || "확인 중"}</span>
                </div>
                <div className="flex items-center gap-sm p-lg">
                  <span className={`w-6 h-6 rounded-full text-white flex items-center justify-center ${platformApproved ? "bg-status-available" : "bg-status-blocked"}`}>
                    <Icon name={platformApproved ? "check" : "chevron_right"} className="text-[14px]" />
                  </span>
                  플랫폼 관리자 승인
                  <span className="text-ink-muted text-caption ml-auto">{platformApproved ? "승인 완료" : selectedEvent?.status === "REJECTED" ? "반려" : "대기"}</span>
                </div>
              </div>
            </section>
          )}
        </div>
      </main>
    </div>
  );
}
