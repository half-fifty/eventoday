import { useMemo, useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import RecruitmentManagementPanel from "../components/RecruitmentManagementPanel.jsx";

const navItems = [
  { key: "dashboard", label: "대시보드", icon: "dashboard" },
  { key: "recruitment", label: "부스 모집 공고", icon: "campaign" },
  { key: "applications", label: "부스 신청서 검토", icon: "assignment" },
  { key: "assignment", label: "부스 배정 현황", icon: "grid_view" },
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
const statusCls = { available: "bg-status-available", pending: "bg-status-pending", assigned: "bg-status-assigned", blocked: "bg-status-blocked" };

export default function OrganizerAdmin() {
  const [page, setPage] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [applications, setApplications] = useState(initialApplications);
  const [assignBooths, setAssignBooths] = useState(initialAssignBooths);

  const pending = useMemo(() => applications.filter((a) => a.status === "pending"), [applications]);
  const assignedCount = assignBooths.filter((b) => b.status === "assigned").length;
  const allReviewed = pending.length === 0;

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
    `w-full flex items-center gap-sm px-md py-sm rounded-lg font-body text-left transition-colors ${
      active ? "bg-primary-container/10 text-primary-focus font-body-strong" : "text-on-surface-variant hover:bg-surface-container"
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
    <div className="bg-surface-container-lowest text-on-surface">
      {/* Sidebar */}
      <aside className={`fixed left-0 top-0 h-screen w-[260px] bg-white border-r border-hairline z-50 flex flex-col transition-transform md:translate-x-0 ${sidebarOpen ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="px-lg py-xl flex items-center justify-between">
          <span className="font-hero-display text-tagline text-primary tracking-tight">EXPO HUB</span>
          <button onClick={() => setSidebarOpen(false)} className="md:hidden"><Icon name="close" /></button>
        </div>
        <nav className="flex-1 px-sm space-y-1">
          {navItems.map((n) => (
            <button key={n.key} onClick={() => gotoPage(n.key)} className={navBtnCls(page === n.key)}>
              <Icon name={n.icon} /><span>{n.label}</span>
            </button>
          ))}
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
      <main className="md:ml-[260px] min-h-screen">
        <header className="sticky top-0 z-30 bg-white/70 backdrop-blur-xl border-b border-hairline px-lg h-[64px] flex items-center justify-between">
          <div className="flex items-center gap-sm">
            <button onClick={() => setSidebarOpen(true)} className="md:hidden"><Icon name="menu" /></button>
            <h2 className="font-display-md text-[20px] text-on-surface">{navItems.find((n) => n.key === page).label}</h2>
          </div>
          <button className="px-md py-xs bg-primary text-white text-caption rounded-full font-body-strong active:scale-95 transition-transform">+ 새 전시회 등록</button>
        </header>

        <div className="p-lg md:p-xl space-y-section max-w-[1200px] mx-auto">
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
          {page === "recruitment" && <RecruitmentManagementPanel />}

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
          {page === "assignment" && (
            <section className="space-y-lg">
              <h1 className="font-display-lg text-[26px]">부스 배정 현황</h1>
              <div className="bg-white border border-hairline rounded-xl p-lg">
                <div className="grid grid-cols-5 gap-sm">
                  {assignBooths.map((b) => (
                    <div key={b.id} className={`h-16 rounded-lg text-white text-[11px] font-bold flex flex-col items-center justify-center ${statusCls[b.status]}`}>
                      <span>{b.id}</span>
                      <span className="text-[9px] opacity-85 mt-0.5">{statusLabel[b.status]}</span>
                    </div>
                  ))}
                </div>
                <div className="flex flex-wrap gap-md mt-lg text-caption text-on-surface-variant">
                  <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-status-available" />선택 가능</span>
                  <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-status-pending" />신청 대기</span>
                  <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-status-assigned" />배정 완료</span>
                  <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-status-blocked" />사용 불가</span>
                </div>
              </div>
            </section>
          )}

          {/* FLOORPLAN */}
          {page === "floorplan" && (
            <section className="space-y-lg">
              <div className="flex justify-between items-center">
                <h1 className="font-display-lg text-[26px]">평면도 · 좌표 관리</h1>
                <button className="px-lg py-sm border border-hairline rounded-full text-caption font-body-strong">평면도 업로드</button>
              </div>
              <div className="bg-surface-pearl border-2 border-dashed border-hairline rounded-xl p-xxl text-center text-ink-muted">
                <Icon name="map" className="text-[32px] block mb-sm" />
                평면도 이미지가 아직 없습니다. 업로드하면 부스를 드래그해 좌표를 지정할 수 있어요.
              </div>
            </section>
          )}

          {/* APPROVAL */}
          {page === "approval" && (
            <section className="space-y-lg">
              <h1 className="font-display-lg text-[26px]">행사 등록 승인 요청</h1>
              <div className="bg-primary-fixed/20 border border-primary-fixed rounded-xl p-lg flex items-center justify-between gap-lg flex-wrap">
                <div>
                  <p className="font-body-strong">아직 승인 요청 가능 조건이 충족되지 않았습니다</p>
                  <p className="text-caption text-ink-muted mt-1">
                    {allReviewed ? "모든 신청서 검토가 완료되어 승인 요청이 가능합니다." : `검토되지 않은 신청서가 ${pending.length}건 있습니다.`}
                  </p>
                </div>
                <button disabled={!allReviewed} className="px-xl py-sm bg-primary text-white rounded-full font-body-strong disabled:opacity-40 disabled:cursor-not-allowed">승인 요청하기</button>
              </div>
              <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
                <div className="flex items-center gap-sm p-lg"><span className="w-6 h-6 rounded-full bg-status-available text-white flex items-center justify-center"><Icon name="check" className="text-[14px]" /></span>행사 기본정보 등록 <span className="text-ink-muted text-caption ml-auto">완료</span></div>
                <div className="flex items-center gap-sm p-lg"><span className="w-6 h-6 rounded-full bg-status-available text-white flex items-center justify-center"><Icon name="check" className="text-[14px]" /></span>행사장 평면도 및 부스 좌표 등록 <span className="text-ink-muted text-caption ml-auto">완료</span></div>
                <div className="flex items-center gap-sm p-lg">
                  <span className={`w-6 h-6 rounded-full text-white flex items-center justify-center ${allReviewed ? "bg-status-available" : "bg-status-pending"}`}><Icon name="schedule" className="text-[14px]" /></span>
                  부스 신청서 전체 검토 <span className="text-ink-muted text-caption ml-auto">{allReviewed ? "완료" : `${pending.length}건 대기 중`}</span>
                </div>
                <div className="flex items-center gap-sm p-lg"><span className="w-6 h-6 rounded-full bg-status-blocked text-white flex items-center justify-center"><Icon name="chevron_right" className="text-[14px]" /></span>플랫폼 관리자 승인 <span className="text-ink-muted text-caption ml-auto">대기</span></div>
              </div>
            </section>
          )}
        </div>
      </main>
    </div>
  );
}
