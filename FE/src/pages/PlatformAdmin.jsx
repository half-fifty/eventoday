import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import { advertisementApi } from "../api/advertisementApi.js";
import { eventApi } from "../api/eventApi.js";
import { organizationSignupApi } from "../api/organizationSignupApi.js";
import { platformAdminApi } from "../api/platformAdminApi.js";
import { AdminExchangeCodeRequestPanel } from "../components/ExchangeCodeRequestPanels.jsx";
import Icon from "../components/Icon.jsx";
import PlatformNoticePanel from "../components/PlatformNoticePanel.jsx";
import TopNav from "../components/TopNav.jsx";
import useAuth from "../hooks/useAuth.js";

const menuItems = [
  { key: "dashboard", label: "전체 대시보드", icon: "dashboard" },
  { key: "requests", label: "행사 등록 신청", icon: "verified" },
  { key: "organization-signups", label: "개최자 가입 심사", icon: "domain_verification" },
  { key: "exchange-codes", label: "외부 예매 티켓 관리", icon: "key" },
  { key: "notices", label: "공지 관리", icon: "article" },
  { key: "accounts", label: "계정 관리", icon: "group" },
  { key: "ads", label: "광고 승인 관리", icon: "campaign" },
  { key: "stats", label: "통합 통계", icon: "bar_chart" },
  { key: "audit", label: "감사 로그", icon: "history" },
];

const eventStatus = {
  PREPARING: ["작성 중", "bg-surface-container text-ink-muted"],
  SUBMITTED: ["승인 대기", "bg-status-pending/10 text-status-pending"],
  UNDER_REVIEW: ["검토 중", "bg-status-pending/10 text-status-pending"],
  APPROVED: ["승인", "bg-status-available/10 text-status-available"],
  PUBLISHED: ["공개 중", "bg-primary/10 text-primary"],
  REJECTED: ["반려", "bg-status-visited/10 text-status-visited"],
  SUSPENDED: ["공개 중단", "bg-error/10 text-error"],
  CANCELLED: ["취소", "bg-surface-container text-ink-muted"],
};

const adStatus = {
  PAYMENT_PENDING: "결제 대기", PAID: "결제 완료", REVIEW_PENDING: "검토 대기",
  APPROVED: "승인", REJECTED: "반려", SCHEDULED: "노출 예정", ACTIVE: "노출 중",
  ENDED: "종료", CANCELLED: "취소",
};

const orgSignupStatus = {
  PENDING: ["승인 대기", "bg-status-pending/10 text-status-pending"],
  APPROVED: ["승인", "bg-status-available/10 text-status-available"],
};
const orgSignupFilters = [
  ["PENDING", "승인 대기"], ["APPROVED", "승인"], ["", "전체"],
];

const accountFilters = [
  ["ALL", "전체"], ["ORGANIZER", "행사 개최자"], ["EXHIBITOR", "참가기업"], ["PERSONAL", "일반 회원"],
];

// 감사 로그 category별 아이콘.
// 매핑에 없는 category는 광고(campaign)로 떨어지므로, 새 category를 추가하면 여기에도 등록해야 한다.
const AUDIT_CATEGORY_ICON = { EVENT: "event", NOTICE: "article" };
const auditIcon = (category) => AUDIT_CATEGORY_ICON[category] || "campaign";

const formatDateTime = (value) => value
  ? new Date(value).toLocaleString("ko-KR", { dateStyle: "medium", timeStyle: "short" }) : "-";
const formatMoney = (value) => `${Number(value || 0).toLocaleString("ko-KR")}원`;
const dataOf = (result, fallback) => result?.data ?? fallback;

export default function PlatformAdmin() {
  const { member } = useAuth();
  const [page, setPage] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [dashboard, setDashboard] = useState(null);
  const [requests, setRequests] = useState([]);
  const [accounts, setAccounts] = useState([]);
  const [ads, setAds] = useState([]);
  const [statistics, setStatistics] = useState(null);
  const [audit, setAudit] = useState([]);
  const [eventPage, setEventPage] = useState(0);
  const [eventPageInfo, setEventPageInfo] = useState(null);
  const [organizationSignups, setOrganizationSignups] = useState([]);
  const [orgSignupPage, setOrgSignupPage] = useState(0);
  const [orgSignupPageInfo, setOrgSignupPageInfo] = useState(null);
  const [orgSignupStatusFilter, setOrgSignupStatusFilter] = useState("PENDING");
  const [orgSignupDetail, setOrgSignupDetail] = useState(null);
  const [orgSignupDetailLoading, setOrgSignupDetailLoading] = useState(false);
  const [adPage, setAdPage] = useState(0);
  const [adPageInfo, setAdPageInfo] = useState(null);
  const [accountFilter, setAccountFilter] = useState("ALL");
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [actionKey, setActionKey] = useState("");

  const loadData = useCallback(async () => {
    setLoading(true);
    setError("");
    const results = await Promise.allSettled([
      platformAdminApi.dashboard(), eventApi.adminList({ page: eventPage, size: 20, sort: "createdAt,desc" }),
      platformAdminApi.accounts(), advertisementApi.adminList({ page: adPage, size: 20, sort: "createdAt,desc" }),
      platformAdminApi.statistics(), platformAdminApi.audit(),
      organizationSignupApi.adminList({
        page: orgSignupPage, size: 20,
        ...(orgSignupStatusFilter ? { status: orgSignupStatusFilter } : {}),
      }),
    ]);
    const [dashboardResult, eventResult, accountResult, adResult, statsResult, auditResult, orgSignupResult] = results;
    if (dashboardResult.status === "fulfilled") setDashboard(dataOf(dashboardResult.value, null));
    if (eventResult.status === "fulfilled") {
      const result = dataOf(eventResult.value, {});
      setRequests(result?.content || []); setEventPageInfo(result);
    }
    if (accountResult.status === "fulfilled") setAccounts(dataOf(accountResult.value, []));
    if (adResult.status === "fulfilled") {
      const result = dataOf(adResult.value, {});
      setAds(result?.content || []); setAdPageInfo(result);
    }
    if (statsResult.status === "fulfilled") setStatistics(dataOf(statsResult.value, null));
    if (auditResult.status === "fulfilled") setAudit(dataOf(auditResult.value, []));
    if (orgSignupResult.status === "fulfilled") {
      const result = dataOf(orgSignupResult.value, {});
      setOrganizationSignups(result?.content || []); setOrgSignupPageInfo(result);
    }
    const failures = results.filter((result) => result.status === "rejected");
    if (failures.length) setError(failures[0].reason?.message || "관리 데이터를 불러오지 못했습니다.");
    setLoading(false);
  }, [eventPage, adPage, orgSignupPage, orgSignupStatusFilter]);

  useEffect(() => { loadData(); }, [loadData]);

  const pendingRequests = useMemo(() => requests.filter((event) =>
    ["SUBMITTED", "UNDER_REVIEW"].includes(event.status)), [requests]);
  const filteredAccounts = useMemo(() => accounts.filter((account) =>
    accountFilter === "ALL" || (accountFilter === "PERSONAL"
      ? !account.organizationType : account.organizationType === accountFilter)), [accounts, accountFilter]);

  const runAction = async (key, action) => {
    setActionKey(key); setError("");
    try { await action(); await loadData(); }
    catch (requestError) { setError(requestError.message || "요청 처리에 실패했습니다."); }
    finally { setActionKey(""); }
  };

  const approveEvent = (id) => runAction(`event-${id}`, () => eventApi.approve(id));
  const rejectEvent = (id) => {
    const reason = window.prompt("반려 사유를 입력하세요.");
    if (reason?.trim()) runAction(`event-${id}`, () => eventApi.reject(id, reason.trim()));
  };
  const decideAd = (id, approve) => {
    if (approve) return runAction(`ad-${id}`, () => advertisementApi.approve(id));
    const reason = window.prompt("반려 사유를 입력하세요.");
    if (reason?.trim()) runAction(`ad-${id}`, () => advertisementApi.reject(id, reason.trim()));
  };
  const toggleAccount = (account) => runAction(`account-${account.id}`, () =>
    platformAdminApi.changeAccountStatus(account.id, account.status === "ACTIVE" ? "BLOCKED" : "ACTIVE"));
  const approveOrgSignup = (id) => runAction(`org-signup-${id}`, () => organizationSignupApi.approve(id));
  const rejectOrgSignup = (id) => {
    const reason = window.prompt("반려 사유를 입력하세요.");
    if (reason?.trim()) runAction(`org-signup-${id}`, () => organizationSignupApi.reject(id, reason.trim()));
  };
  const openOrgSignupDetail = async (id) => {
    setOrgSignupDetailLoading(true);
    try {
      const response = await organizationSignupApi.adminDetail(id);
      setOrgSignupDetail(dataOf(response, null));
    } catch (detailError) {
      setError(detailError.message || "심사 상세 정보를 불러오지 못했습니다.");
    } finally {
      setOrgSignupDetailLoading(false);
    }
  };
  const movePage = (key) => { setPage(key); setSidebarOpen(false); };
  const menuClass = (active) => `flex w-full items-center gap-sm rounded-r-xl border-l-[3px] px-md py-sm text-left transition-colors ${active
    ? "border-primary bg-primary/10 font-body-strong text-primary"
    : "border-transparent text-on-surface-variant hover:bg-surface-container"}`;

  const Empty = ({ text }) => <p className="p-xl text-center text-caption text-ink-muted">{text}</p>;
  const StatusBadge = ({ status }) => {
    const [label, cls] = eventStatus[status] || [status, "bg-surface-container text-ink-muted"];
    return <span className={`rounded-full px-sm py-1 text-[11px] font-bold ${cls}`}>{label}</span>;
  };
  const Pagination = ({ result, current, onChange }) => {
    if (!result || result.totalPages <= 1) return null;
    return <div className="flex items-center justify-center gap-sm"><button type="button" disabled={result.first} onClick={() => onChange(current - 1)} className="rounded-full border border-hairline bg-white px-md py-xs text-caption disabled:opacity-40">이전</button><span className="text-caption text-ink-muted">{current + 1} / {result.totalPages}</span><button type="button" disabled={result.last} onClick={() => onChange(current + 1)} className="rounded-full border border-hairline bg-white px-md py-xs text-caption disabled:opacity-40">다음</button></div>;
  };

  return (
    <div className="min-h-screen bg-surface-container-lowest pt-[44px] text-on-surface">
      <TopNav active="platform" />
      <aside className={`fixed bottom-0 left-0 top-[44px] z-50 flex w-[280px] flex-col border-r border-hairline bg-white shadow-[12px_0_40px_rgba(15,23,42,0.04)] transition-transform md:translate-x-0 ${sidebarOpen ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="flex items-start justify-between border-b border-hairline bg-gradient-to-br from-primary/10 via-white to-primary-container/10 px-lg py-lg">
          <div><span className="text-[10px] font-bold tracking-[0.18em] text-primary">EVENTODAY</span><h1 className="mt-1 font-display-md text-[20px]">플랫폼 관리자센터</h1><p className="mt-1 text-[11px] text-ink-muted">서비스 운영 현황을 관리하세요</p></div>
          <button type="button" onClick={() => setSidebarOpen(false)} aria-label="메뉴 닫기" className="md:hidden"><Icon name="close" /></button>
        </div>
        <nav className="flex-1 space-y-1 overflow-y-auto px-md py-md" aria-label="플랫폼 관리자 메뉴">
          {menuItems.map((item) => <button key={item.key} type="button" onClick={() => movePage(item.key)} className={menuClass(page === item.key)}><Icon name={item.icon} /><span>{item.label}</span></button>)}
        </nav>
        <div className="border-t border-hairline p-lg"><div className="flex items-center gap-sm"><div className="grid h-8 w-8 place-items-center rounded-full bg-primary/10 text-primary"><Icon name="admin_panel_settings" /></div><div className="min-w-0"><p className="truncate text-caption font-body-strong">{member.nickname}</p><p className="text-[10px] text-ink-muted">PLATFORM_ADMIN</p></div></div></div>
      </aside>
      {sidebarOpen && <div className="fixed inset-0 z-40 bg-black/40 md:hidden" onClick={() => setSidebarOpen(false)} />}

      <main className="min-h-[calc(100vh-44px)] md:ml-[280px]">
        <header className="sticky top-[44px] z-30 flex h-[64px] items-center justify-between border-b border-hairline bg-white/70 px-lg backdrop-blur-xl"><div className="flex items-center gap-sm"><button type="button" onClick={() => setSidebarOpen(true)} className="md:hidden"><Icon name="menu" /></button><h2 className="font-display-md text-[20px]">{menuItems.find((item) => item.key === page)?.label}</h2></div><button type="button" onClick={loadData} disabled={loading} className="inline-flex items-center gap-xs rounded-full border border-hairline px-md py-xs text-caption font-body-strong"><Icon name="refresh" className="text-[16px]" />새로고침</button></header>
        <div className="mx-auto max-w-[1200px] space-y-section p-lg md:p-xl">
          {loading && <div className="rounded-xl border border-hairline bg-white p-lg text-caption text-ink-muted">관리 데이터를 불러오는 중입니다.</div>}
          {error && <div className="flex justify-between rounded-xl border border-error/20 bg-error/10 p-lg text-caption text-error"><span>{error}</span><button onClick={loadData} className="font-body-strong">다시 시도</button></div>}

          {page === "dashboard" && <section className="space-y-xl"><div><h1 className="font-display-lg text-[28px]">서비스 운영 현황</h1><p className="text-lead text-on-surface-variant">실시간 플랫폼 관리 지표입니다.</p></div><div className="grid grid-cols-1 gap-lg sm:grid-cols-2 xl:grid-cols-4">{[
            ["행사 승인 대기", dashboard?.pendingEventCount, "verified"], ["활성 계정", dashboard?.activeAccountCount, "group"], ["광고 검토 대기", dashboard?.pendingAdvertisementCount, "campaign"], ["공개 중인 행사", dashboard?.activeEventCount, "event"],
          ].map(([label, value, icon]) => <div key={label} className="rounded-xl border border-hairline bg-surface-pearl p-lg"><div className="mb-md flex justify-between"><span className="text-caption text-on-surface-variant">{label}</span><Icon name={icon} className="text-primary" /></div><span className="font-display-md text-[26px]">{loading || value == null ? "-" : Number(value).toLocaleString()}</span></div>)}</div><div className="overflow-hidden rounded-xl border border-hairline bg-white"><div className="flex items-center justify-between border-b border-hairline px-lg py-md"><h3 className="font-body-strong">최근 운영 활동</h3><button onClick={() => movePage("audit")} className="text-caption font-body-strong text-primary">전체 보기</button></div>{dashboard?.recentActivity?.length ? <div className="divide-y divide-divider-soft">{dashboard.recentActivity.map((entry) => <div key={entry.id} className="flex gap-md p-lg"><Icon name={auditIcon(entry.category)} className="text-primary" /><div className="flex-1"><p className="text-caption font-body-strong">{entry.target}</p><p className="text-[11px] text-ink-muted">{entry.action} · {formatDateTime(entry.occurredAt)}</p></div></div>)}</div> : <Empty text="기록된 운영 활동이 없습니다." />}</div></section>}

          {page === "requests" && <section className="space-y-lg"><div><h1 className="font-display-lg text-[26px]">행사 등록 신청</h1><p className="mt-xs text-caption text-ink-muted">개최자가 제출한 행사를 검토하고 승인합니다.</p></div><div className="space-y-md">{requests.length ? requests.map((event) => <article key={event.id} className="rounded-xl border border-hairline bg-white p-lg"><div className="flex flex-wrap items-start justify-between gap-md"><div><p className="font-body-strong">{event.name}</p><p className="text-caption text-ink-muted">조직 #{event.organizerOrganizationId} · {event.venueName || "장소 미정"}</p><p className="text-[11px] text-ink-muted">수정 {formatDateTime(event.updatedAt)}</p></div><div className="flex items-center gap-sm"><StatusBadge status={event.status} />{["SUBMITTED", "UNDER_REVIEW"].includes(event.status) && <><button disabled={actionKey === `event-${event.id}`} onClick={() => approveEvent(event.id)} className="rounded-full bg-status-available px-md py-xs text-caption font-body-strong text-white disabled:opacity-50">승인</button><button disabled={actionKey === `event-${event.id}`} onClick={() => rejectEvent(event.id)} className="rounded-full border border-error/30 px-md py-xs text-caption font-body-strong text-error disabled:opacity-50">반려</button></>}</div></div>{event.rejectionReason && <p className="mt-md rounded-lg bg-error/5 p-sm text-caption text-error">반려 사유: {event.rejectionReason}</p>}</article>) : <div className="rounded-xl border border-hairline bg-white"><Empty text="등록된 행사가 없습니다." /></div>}</div><Pagination result={eventPageInfo} current={eventPage} onChange={setEventPage} /></section>}

          {page === "organization-signups" && <section className="space-y-lg"><div><h1 className="font-display-lg text-[26px]">개최자 가입 심사</h1><p className="mt-xs text-caption text-ink-muted">행사 개최자로 가입 신청한 계정을 검토하고 승인·반려합니다.</p></div><div className="flex flex-wrap gap-xs">{orgSignupFilters.map(([key, label]) => <button key={key || "all"} onClick={() => { setOrgSignupPage(0); setOrgSignupStatusFilter(key); }} className={`rounded-full px-md py-1.5 text-caption font-body-strong ${orgSignupStatusFilter === key ? "bg-primary text-white" : "border border-hairline bg-white"}`}>{label}</button>)}</div><div className="space-y-md">{organizationSignups.length ? organizationSignups.map((review) => { const [label, cls] = orgSignupStatus[review.status] || [review.status, "bg-surface-container text-ink-muted"]; return <article key={review.reviewId} className="rounded-xl border border-hairline bg-white p-lg"><div className="flex flex-wrap items-start justify-between gap-md"><div><p className="font-body-strong">{review.organizationName}</p><p className="text-caption text-ink-muted">대표자 {review.representativeName} · {review.maskedBusinessNumber} · 담당자 {review.managerName || "-"}</p><p className="text-[11px] text-ink-muted">신청 {formatDateTime(review.submittedAt)}</p></div><div className="flex items-center gap-sm"><span className={`rounded-full px-sm py-1 text-[11px] font-bold ${cls}`}>{label}</span><button onClick={() => openOrgSignupDetail(review.reviewId)} className="rounded-full border border-hairline px-md py-xs text-caption font-body-strong">상세 보기</button>{review.status === "PENDING" && <><button disabled={actionKey === `org-signup-${review.reviewId}`} onClick={() => approveOrgSignup(review.reviewId)} className="rounded-full bg-status-available px-md py-xs text-caption font-body-strong text-white disabled:opacity-50">승인</button><button disabled={actionKey === `org-signup-${review.reviewId}`} onClick={() => rejectOrgSignup(review.reviewId)} className="rounded-full border border-error/30 px-md py-xs text-caption font-body-strong text-error disabled:opacity-50">반려</button></>}</div></div></article>; }) : <div className="rounded-xl border border-hairline bg-white"><Empty text="조건에 맞는 가입 신청이 없습니다." /></div>}</div><Pagination result={orgSignupPageInfo} current={orgSignupPage} onChange={setOrgSignupPage} /></section>}

          {page === "exchange-codes" && <AdminExchangeCodeRequestPanel />}

          {page === "notices" && <PlatformNoticePanel />}

          {page === "accounts" && <section className="space-y-lg"><div><h1 className="font-display-lg text-[26px]">계정 관리</h1><p className="mt-xs text-caption text-ink-muted">실제 가입 회원의 상태와 소속을 관리합니다.</p></div><div className="flex flex-wrap gap-xs">{accountFilters.map(([key, label]) => <button key={key} onClick={() => setAccountFilter(key)} className={`rounded-full px-md py-1.5 text-caption font-body-strong ${accountFilter === key ? "bg-primary text-white" : "border border-hairline bg-white"}`}>{label}</button>)}</div><div className="overflow-hidden rounded-xl border border-hairline bg-white">{filteredAccounts.length ? <div className="divide-y divide-divider-soft">{filteredAccounts.map((account) => <div key={account.id} className="flex flex-col gap-md p-lg sm:flex-row sm:items-center"><div className="grid h-10 w-10 place-items-center rounded-full bg-primary/10 text-primary"><Icon name="person" /></div><div className="min-w-0 flex-1"><p className="font-body-strong">{account.nickname} <span className="text-[10px] text-ink-muted">#{account.id}</span></p><p className="truncate text-caption text-ink-muted">{account.email}</p><p className="text-[11px] text-ink-muted">{account.organizationName || "개인 회원"} · {account.organizationType || account.platformRole}</p></div><div className="flex items-center gap-sm"><span className={`text-caption ${account.status === "ACTIVE" ? "text-status-available" : "text-error"}`}>{account.status === "ACTIVE" ? "활성" : "차단"}</span><button type="button" disabled={actionKey === `account-${account.id}` || account.id === member.id} onClick={() => toggleAccount(account)} className={`relative h-6 w-10 rounded-full disabled:opacity-40 ${account.status === "ACTIVE" ? "bg-status-available" : "bg-hairline"}`}><span className="absolute top-0.5 h-5 w-5 rounded-full bg-white transition-all" style={{ left: account.status === "ACTIVE" ? "18px" : "2px" }} /></button></div></div>)}</div> : <Empty text="조건에 맞는 계정이 없습니다." />}</div></section>}

          {page === "ads" && <section className="space-y-lg"><div><h1 className="font-display-lg text-[26px]">광고 승인 관리</h1><p className="mt-xs text-caption text-ink-muted">결제가 완료된 행사 광고와 부스 광고를 심사합니다.</p></div><div className="overflow-hidden rounded-xl border border-hairline bg-white">{ads.length ? <div className="divide-y divide-divider-soft">{ads.map((ad) => <article key={ad.id} className="flex flex-col gap-md p-lg sm:flex-row sm:items-center"><div className="grid h-10 w-10 place-items-center rounded-xl bg-primary/10 text-primary"><Icon name="campaign" /></div><div className="min-w-0 flex-1"><p className="font-body-strong">{ad.eventId ? `행사 광고 #${ad.eventId}` : `부스 광고 #${ad.boothId}`}</p><p className="truncate text-caption text-ink-muted">{ad.adText || "광고 문구 없음"}</p><p className="text-[11px] text-ink-muted">{formatDateTime(ad.startAt)} ~ {formatDateTime(ad.endAt)}</p></div><div className="flex items-center gap-sm"><span className="rounded-full bg-surface-container px-sm py-1 text-[11px] font-bold">{adStatus[ad.status] || ad.status}</span>{["PAID", "REVIEW_PENDING"].includes(ad.status) && <><button disabled={actionKey === `ad-${ad.id}`} onClick={() => decideAd(ad.id, true)} className="rounded-full bg-status-available px-md py-xs text-caption font-body-strong text-white disabled:opacity-50">승인</button><button disabled={actionKey === `ad-${ad.id}`} onClick={() => decideAd(ad.id, false)} className="rounded-full border border-error/30 px-md py-xs text-caption font-body-strong text-error disabled:opacity-50">반려</button></>}</div></article>)}</div> : <Empty text="등록된 광고가 없습니다." />}</div><Pagination result={adPageInfo} current={adPage} onChange={setAdPage} /></section>}

          {page === "stats" && <section className="space-y-lg"><div><h1 className="font-display-lg text-[26px]">통합 통계</h1><p className="mt-xs text-caption text-ink-muted">플랫폼에 저장된 운영 데이터를 실시간으로 집계합니다.</p></div><div className="grid grid-cols-1 gap-lg sm:grid-cols-2 xl:grid-cols-4">{[
            ["공개 중인 행사", statistics?.activeEventCount, "event", "건"], ["판매된 입장권", statistics?.totalTicketQuantity, "confirmation_number", "매"], ["등록 참가기업", statistics?.exhibitorOrganizationCount, "storefront", "곳"], ["누적 광고 매출", statistics?.advertisementRevenue, "payments", "money"],
          ].map(([label, value, icon, unit]) => <div key={label} className="rounded-xl border border-hairline bg-surface-pearl p-lg"><div className="mb-md flex justify-between"><span className="text-caption text-on-surface-variant">{label}</span><Icon name={icon} className="text-primary" /></div><span className="font-display-md text-[24px]">{loading || value == null ? "-" : unit === "money" ? formatMoney(value) : `${Number(value).toLocaleString()}${unit}`}</span></div>)}</div></section>}

          {page === "audit" && <section className="space-y-lg"><div><h1 className="font-display-lg text-[26px]">감사 로그</h1><p className="mt-xs text-caption text-ink-muted">행사, 광고, 공지의 실제 운영 상태 변경 내역입니다.</p></div><div className="overflow-hidden rounded-xl border border-hairline bg-white">{audit.length ? <div className="divide-y divide-divider-soft">{audit.map((entry) => <div key={entry.id} className="flex gap-md p-lg"><div className="grid h-9 w-9 place-items-center rounded-xl bg-primary/10 text-primary"><Icon name={auditIcon(entry.category)} className="text-[18px]" /></div><div className="min-w-0 flex-1"><p className="font-body-strong text-[14px]">{entry.target}</p><p className="text-caption text-ink-muted">{entry.action}{entry.detail ? ` · ${entry.detail}` : ""}</p></div><time className="text-[11px] text-ink-muted">{formatDateTime(entry.occurredAt)}</time></div>)}</div> : <Empty text="기록된 운영 활동이 없습니다." />}</div></section>}
        </div>
      </main>

      {(orgSignupDetail || orgSignupDetailLoading) && (
        <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-lg" onClick={() => setOrgSignupDetail(null)}>
          <div className="max-h-[85vh] w-full max-w-[560px] overflow-y-auto rounded-2xl bg-white p-xl shadow-xl" onClick={(event) => event.stopPropagation()}>
            <div className="mb-md flex items-center justify-between">
              <h3 className="font-display-md text-[18px]">가입 심사 상세</h3>
              <button onClick={() => setOrgSignupDetail(null)} aria-label="닫기"><Icon name="close" /></button>
            </div>
            {orgSignupDetailLoading && <p className="text-caption text-ink-muted">불러오는 중입니다.</p>}
            {orgSignupDetail && !orgSignupDetailLoading && (
              <div className="space-y-md text-caption">
                <div><p className="text-[11px] text-ink-muted">조직명</p><p className="font-body-strong">{orgSignupDetail.organizationName}</p></div>
                <div><p className="text-[11px] text-ink-muted">대표자명 / 사업자등록번호</p><p>{orgSignupDetail.representativeName} · {orgSignupDetail.maskedBusinessNumber}</p></div>
                <div><p className="text-[11px] text-ink-muted">담당자</p><p>{orgSignupDetail.managerName || "-"} · {orgSignupDetail.managerPhone || "-"}</p></div>
                <div><p className="text-[11px] text-ink-muted">로그인 이메일 / 회사 연락처</p><p>{orgSignupDetail.loginEmail} · {orgSignupDetail.contactPhone}</p></div>
                {orgSignupDetail.addressLine1 && <div><p className="text-[11px] text-ink-muted">주소</p><p>({orgSignupDetail.postalCode}) {orgSignupDetail.addressLine1} {orgSignupDetail.addressLine2}</p></div>}
                {orgSignupDetail.homepageUrl && <div><p className="text-[11px] text-ink-muted">홈페이지</p><a href={orgSignupDetail.homepageUrl} target="_blank" rel="noreferrer" className="text-primary underline">{orgSignupDetail.homepageUrl}</a></div>}
                {orgSignupDetail.introduction && <div><p className="text-[11px] text-ink-muted">회사 소개</p><p>{orgSignupDetail.introduction}</p></div>}
                <div><p className="text-[11px] text-ink-muted">사업자등록증</p>{orgSignupDetail.businessRegistrationFileDownloadUrl ? <a href={orgSignupDetail.businessRegistrationFileDownloadUrl} target="_blank" rel="noreferrer" className="text-primary underline">첨부 파일 열기</a> : <p className="text-ink-muted">첨부된 서류가 없습니다.</p>}</div>
                <div><p className="text-[11px] text-ink-muted">신청일 / 상태</p><p>{formatDateTime(orgSignupDetail.submittedAt)} · {orgSignupStatus[orgSignupDetail.status]?.[0] || orgSignupDetail.status}</p></div>
                {orgSignupDetail.rejectionReason && <div><p className="text-[11px] text-ink-muted">반려 사유</p><p className="text-error">{orgSignupDetail.rejectionReason}</p></div>}
                {orgSignupDetail.status === "PENDING" && (
                  <div className="flex gap-sm pt-sm">
                    <button onClick={() => { approveOrgSignup(orgSignupDetail.reviewId); setOrgSignupDetail(null); }} className="rounded-full bg-status-available px-md py-xs text-caption font-body-strong text-white">승인</button>
                    <button onClick={() => { rejectOrgSignup(orgSignupDetail.reviewId); setOrgSignupDetail(null); }} className="rounded-full border border-error/30 px-md py-xs text-caption font-body-strong text-error">반려</button>
                  </div>
                )}
              </div>
            )}
          </div>
        </div>
      )}
    </div>
  );
}
