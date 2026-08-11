import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import TopNav from "../components/TopNav.jsx";
import useAuth from "../hooks/useAuth.js";
import {
  cancelApplication,
  getOrganizationApplications,
} from "../api/boothApplicationApi.js";

const menuItems = [
  { key: "dashboard", label: "대시보드", icon: "dashboard" },
  { key: "applications", label: "부스 신청 현황", icon: "assignment" },
  { key: "booths", label: "운영 부스", icon: "storefront" },
  { key: "settings", label: "조직·계정 정보", icon: "manage_accounts" },
];

const statusMeta = {
  SUBMITTED: ["신청 완료", "bg-status-pending/10 text-status-pending"],
  UNDER_REVIEW: ["검토 중", "bg-primary/10 text-primary"],
  APPROVED: ["승인", "bg-status-available/10 text-status-available"],
  REJECTED: ["반려", "bg-status-visited/10 text-status-visited"],
  CANCELLED: ["취소", "bg-surface-container text-ink-muted"],
};

const formatDate = (value) => value
  ? new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium" }).format(new Date(value))
  : "-";

export default function ExhibitorAdmin() {
  const { member } = useAuth();
  const organization = member.organization;
  const [page, setPage] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [cancellingId, setCancellingId] = useState(null);

  const loadApplications = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getOrganizationApplications(organization.organizationId);
      setApplications(Array.isArray(data) ? data : []);
    } catch (requestError) {
      setError(requestError.message || "부스 신청 현황을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [organization.organizationId]);

  useEffect(() => {
    loadApplications();
  }, [loadApplications]);

  const counts = useMemo(() => ({
    total: applications.length,
    reviewing: applications.filter((item) => ["SUBMITTED", "UNDER_REVIEW"].includes(item.status)).length,
    approved: applications.filter((item) => item.status === "APPROVED").length,
    rejected: applications.filter((item) => item.status === "REJECTED").length,
  }), [applications]);

  const approvedApplications = applications.filter((item) => item.status === "APPROVED");

  const movePage = (nextPage) => {
    setPage(nextPage);
    setSidebarOpen(false);
  };

  const handleCancel = async (applicationId) => {
    if (!window.confirm("이 부스 신청을 취소하시겠습니까?")) return;
    setCancellingId(applicationId);
    try {
      await cancelApplication(applicationId);
      await loadApplications();
    } catch (requestError) {
      window.alert(requestError.message || "신청을 취소하지 못했습니다.");
    } finally {
      setCancellingId(null);
    }
  };

  const menuClass = (active) =>
    `flex w-full items-center gap-sm rounded-r-xl border-l-[3px] px-md py-sm text-left transition-colors ${active
      ? "border-primary bg-primary/10 font-body-strong text-primary"
      : "border-transparent text-on-surface-variant hover:bg-surface-container"}`;

  const ApplicationList = ({ items }) => {
    if (loading) return <p className="p-xl text-center text-caption text-ink-muted">불러오는 중입니다.</p>;
    if (error) return <div className="p-xl text-center"><p className="text-caption text-error">{error}</p><button type="button" onClick={loadApplications} className="mt-sm text-caption font-body-strong text-primary">다시 시도</button></div>;
    if (items.length === 0) return <p className="p-xl text-center text-caption text-ink-muted">표시할 신청이 없습니다.</p>;

    return (
      <div className="divide-y divide-divider-soft">
        {items.map((application) => {
          const [statusLabel, statusClass] = statusMeta[application.status] || [application.status, "bg-surface-container text-ink-muted"];
          return (
            <article key={application.id} className="flex flex-col gap-md p-lg sm:flex-row sm:items-center">
              <div className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"><Icon name="storefront" /></div>
              <div className="min-w-0 flex-1">
                <p className="font-body-strong">{application.teamName}</p>
                <p className="text-caption text-ink-muted">신청번호 {application.applicationNo} · 부스 #{application.boothId}</p>
                <p className="text-[11px] text-ink-muted">{formatDate(application.submittedAt)}</p>
              </div>
              <div className="flex items-center gap-sm">
                <span className={`rounded-full px-sm py-1 text-[11px] font-bold ${statusClass}`}>{statusLabel}</span>
                {application.status === "SUBMITTED" && (
                  <button type="button" onClick={() => handleCancel(application.id)} disabled={cancellingId === application.id} className="rounded-full border border-hairline px-sm py-1 text-[11px] font-bold text-error disabled:opacity-50">
                    {cancellingId === application.id ? "취소 중" : "신청 취소"}
                  </button>
                )}
              </div>
            </article>
          );
        })}
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-surface-container-lowest pt-[44px] text-on-surface">
      <TopNav active="exhibitor" />
      <aside className={`fixed bottom-0 left-0 top-[44px] z-50 flex w-[280px] flex-col border-r border-hairline bg-white shadow-[12px_0_40px_rgba(15,23,42,0.04)] transition-transform md:translate-x-0 ${sidebarOpen ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="flex items-start justify-between border-b border-hairline bg-gradient-to-br from-primary/10 via-white to-primary-container/10 px-lg py-lg">
          <div><span className="text-[10px] font-bold tracking-[0.18em] text-primary">EVENTODAY</span><h1 className="mt-1 font-display-md text-[20px]">부스 관리센터</h1><p className="mt-1 text-[11px] text-ink-muted">신청부터 부스 운영까지 관리하세요</p></div>
          <button type="button" onClick={() => setSidebarOpen(false)} aria-label="부스 관리센터 메뉴 닫기" className="md:hidden"><Icon name="close" /></button>
        </div>
        <div className="px-md pt-md"><Link to="/recruitments" className="flex w-full items-center justify-center gap-xs rounded-xl bg-primary px-md py-sm text-caption font-body-strong text-white"><Icon name="search" className="text-[18px]" /> 모집 공고 찾기</Link></div>
        <nav className="flex-1 space-y-1 overflow-y-auto px-md py-md" aria-label="부스 관리센터 메뉴">
          {menuItems.map((item) => <button key={item.key} type="button" onClick={() => movePage(item.key)} className={menuClass(page === item.key)}><Icon name={item.icon} /><span>{item.label}</span></button>)}
        </nav>
        <div className="border-t border-hairline p-lg"><div className="flex items-center gap-sm"><div className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-container-high"><Icon name="business" /></div><div className="min-w-0"><p className="truncate text-caption font-body-strong">{organization.name}</p><p className="text-[10px] text-ink-muted">{organization.organizationRole}</p></div></div></div>
      </aside>
      {sidebarOpen && <div className="fixed inset-0 z-40 bg-black/40 md:hidden" onClick={() => setSidebarOpen(false)} />}

      <main className="min-h-[calc(100vh-44px)] md:ml-[280px]">
        <header className="sticky top-[44px] z-30 flex h-[64px] items-center justify-between border-b border-hairline bg-white/70 px-lg backdrop-blur-xl"><div className="flex items-center gap-sm"><button type="button" onClick={() => setSidebarOpen(true)} className="md:hidden"><Icon name="menu" /></button><h2 className="font-display-md text-[20px]">{menuItems.find((item) => item.key === page)?.label}</h2></div><Link to="/recruitments" className="rounded-full bg-primary px-md py-xs text-caption font-body-strong text-white">+ 부스 신청</Link></header>
        <div className="mx-auto max-w-[1200px] space-y-section p-lg md:p-xl">
          {page === "dashboard" && <section className="space-y-xl"><div><h1 className="font-display-lg text-[28px]">{organization.name}</h1><p className="text-lead text-on-surface-variant">부스 참가 및 운영 현황</p></div><div className="grid grid-cols-1 gap-lg sm:grid-cols-2 lg:grid-cols-4">{[["전체 신청", counts.total, "assignment"], ["검토 중", counts.reviewing, "schedule"], ["승인된 부스", counts.approved, "storefront"], ["반려", counts.rejected, "cancel"]].map(([label, count, icon]) => <div key={label} className="rounded-xl border border-hairline bg-surface-pearl p-lg"><div className="mb-md flex justify-between"><span className="text-caption text-on-surface-variant">{label}</span><Icon name={icon} className="text-primary" /></div><span className="font-display-md text-[26px]">{loading ? "-" : count}</span></div>)}</div><div className="overflow-hidden rounded-xl border border-hairline bg-white"><div className="flex items-center justify-between border-b border-hairline px-lg py-md"><h3 className="font-body-strong">최근 신청 현황</h3><button type="button" onClick={() => movePage("applications")} className="text-caption font-body-strong text-primary">전체 보기</button></div><ApplicationList items={applications.slice(0, 3)} /></div></section>}
          {page === "applications" && <section className="space-y-lg"><div><h1 className="font-display-lg text-[26px]">부스 신청 현황</h1><p className="mt-xs text-caption text-ink-muted">우리 조직이 제출한 신청과 심사 상태입니다.</p></div><div className="overflow-hidden rounded-xl border border-hairline bg-white"><ApplicationList items={applications} /></div></section>}
          {page === "booths" && <section className="space-y-lg"><div><h1 className="font-display-lg text-[26px]">운영 부스</h1><p className="mt-xs text-caption text-ink-muted">승인되어 운영할 수 있는 부스입니다.</p></div><div className="overflow-hidden rounded-xl border border-hairline bg-white"><ApplicationList items={approvedApplications} /></div></section>}
          {page === "settings" && <section className="space-y-lg"><div><h1 className="font-display-lg text-[26px]">조직·계정 정보</h1></div><div className="divide-y divide-divider-soft rounded-xl border border-hairline bg-white"><div className="p-lg"><p className="text-[11px] text-ink-muted">회사명</p><p>{organization.name}</p></div><div className="p-lg"><p className="text-[11px] text-ink-muted">조직 유형</p><p>부스측</p></div><div className="p-lg"><p className="text-[11px] text-ink-muted">조직 권한</p><p>{organization.organizationRole}</p></div><div className="p-lg"><p className="text-[11px] text-ink-muted">닉네임</p><p>{member.nickname}</p></div><div className="p-lg"><p className="text-[11px] text-ink-muted">이메일</p><p>{member.email}</p></div></div></section>}
        </div>
      </main>
    </div>
  );
}
