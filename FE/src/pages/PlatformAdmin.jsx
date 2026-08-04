import { useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import { eventApi } from "../api/eventApi.js";
import { advertisementApi } from "../api/advertisementApi.js";

const navItems = [
  { key: "dashboard", label: "전체 대시보드", icon: "dashboard" },
  { key: "requests", label: "행사 등록 신청 관리", icon: "verified" },
  { key: "accounts", label: "계정 관리", icon: "group" },
  { key: "ads", label: "광고 승인 관리", icon: "campaign" },
  { key: "stats", label: "통합 통계", icon: "bar_chart" },
  { key: "audit", label: "감사 로그", icon: "history" },
];
const initialRequests = [
  { id: 1, name: "2026 서울 푸드테크 박람회", organizer: "코엑스 이벤트", submitted: "2026.07.22", place: "코엑스 3층 A홀", booth: "10/10 배정 완료", status: "pending" },
  { id: 2, name: "친환경 에너지 컨퍼런스", organizer: "그린포럼", submitted: "2026.07.18", place: "송도 컨벤시아", booth: "부스 모집 미사용", status: "approved" },
  { id: 3, name: "무자격 팝업 마켓", organizer: "미확인 주최", submitted: "2026.07.15", place: "정보 미기재", booth: "0/8 배정", status: "rejected", reason: "행사장 정보와 안전 계획이 확인되지 않아 반려" },
];
const initialAccounts = [
  { id: 1, name: "코엑스 이벤트", role: "행사 개최자", active: true },
  { id: 2, name: "그린포럼", role: "행사 개최자", active: true },
  { id: 3, name: "그린 키친랩", role: "참가기업", active: true },
  { id: 4, name: "김서연", role: "회원 관람객", active: true },
];
const initialAds = [
  { id: 1, target: "2026 서울 푸드테크 박람회", type: "행사 광고", amount: "500,000원", payment: "confirmed", status: "pending" },
  { id: 2, target: "A06 콜드체인 솔루션", type: "부스 광고", amount: "80,000원", payment: "pending", status: "pending" },
];
const accTabs = ["전체", "행사 개최자", "참가기업", "회원 관람객"];
const eventStatusLabel = {
  pending: "승인 대기",
  approved: "승인 완료",
  rejected: "반려됨",
  inactive: "제출 전",
};

export default function PlatformAdmin() {
  const [page, setPage] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [requests, setRequests] = useState([]);
  const [accounts, setAccounts] = useState(initialAccounts);
  const [accFilter, setAccFilter] = useState("전체");
  const [ads, setAds] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const pendingReq = useMemo(() => requests.filter((r) => r.status === "pending"), [requests]);
  const activeAccounts = accounts.filter((a) => a.active).length;
  const pendingAds = ads.filter((a) => a.status === "pending").length;
  const accountList = accFilter === "전체" ? accounts : accounts.filter((a) => a.role === accFilter);

  const loadAdminData = async () => {
    setLoading(true);
    setLoadError("");
    try {
      const [eventResult, adResult] = await Promise.all([
        eventApi.adminList({ size: 100, sort: "createdAt,desc" }),
        advertisementApi.adminList({ size: 100, sort: "createdAt,desc" }),
      ]);
      setRequests((eventResult?.data?.content || []).map((event) => ({
        id: event.id,
        name: event.name,
        organizer: `조직 #${event.organizerOrganizationId || "-"}`,
        submitted: event.updatedAt ? new Date(event.updatedAt).toLocaleDateString("ko-KR") : "-",
        place: `${event.venueName || "장소 미정"} · ${event.address || ""}`,
        booth: event.boothRecruitmentEnabled ? "부스 모집 사용" : "부스 모집 미사용",
        status: ["SUBMITTED", "UNDER_REVIEW"].includes(event.status) ? "pending"
          : ["APPROVED", "PUBLISHED"].includes(event.status) ? "approved"
            : event.status === "REJECTED" ? "rejected" : "inactive",
        rawStatus: event.status,
        reason: event.rejectionReason,
      })));
      setAds((adResult?.data?.content || []).map((ad) => ({
        id: ad.id,
        target: ad.eventId ? `행사 #${ad.eventId}` : `부스 #${ad.boothId}`,
        type: ad.eventId ? "행사 광고" : "부스 광고",
        amount: ad.eventId ? "결제 연동" : "무료",
        payment: ["PAID", "REVIEW_PENDING", "APPROVED", "SCHEDULED", "ACTIVE", "ENDED"].includes(ad.status)
          ? "confirmed" : "pending",
        status: ["APPROVED", "SCHEDULED", "ACTIVE", "ENDED"].includes(ad.status)
          ? "approved" : ad.status === "REJECTED" ? "rejected" : "pending",
        rawStatus: ad.status,
      })));
    } catch (error) {
      setLoadError(error.message || "관리 데이터를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => { loadAdminData(); }, []);

  const approveReq = async (id) => {
    setLoadError("");
    try {
      await eventApi.approve(id);
      await loadAdminData();
    } catch (error) {
      setLoadError(error.message || "행사 승인에 실패했습니다.");
    }
  };
  const rejectReq = async (id) => {
    const reason = window.prompt("반려 사유를 입력하세요");
    if (!reason) return;
    setLoadError("");
    try {
      await eventApi.reject(id, reason);
      await loadAdminData();
    } catch (error) {
      setLoadError(error.message || "행사 반려에 실패했습니다.");
    }
  };
  const toggleAccount = (id) => setAccounts((prev) => prev.map((a) => (a.id === id ? { ...a, active: !a.active } : a)));
  const decideAd = async (id, decision) => {
    if (decision === "approved") await advertisementApi.approve(id);
    else {
      const reason = window.prompt("반려 사유를 입력하세요");
      if (!reason) return;
      await advertisementApi.reject(id, reason);
    }
    await loadAdminData();
  };

  const gotoPage = (key) => {
    setPage(key);
    if (window.innerWidth < 768) setSidebarOpen(false);
  };

  const navBtnCls = (active) =>
    `w-full flex items-center gap-sm px-md py-sm rounded-lg font-body text-left transition-colors ${
      active ? "bg-white/10 text-white font-body-strong" : "text-white/60 hover:bg-white/10"
    }`;

  return (
    <div className="bg-surface-container-lowest text-on-surface">
      {/* Sidebar */}
      <aside className={`fixed left-0 top-0 h-screen w-[260px] bg-black text-white z-50 flex flex-col transition-transform md:translate-x-0 ${sidebarOpen ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="px-lg py-xl flex items-center justify-between">
          <span className="font-hero-display text-tagline text-white tracking-tight">EXPO HUB</span>
          <button onClick={() => setSidebarOpen(false)} className="md:hidden"><Icon name="close" /></button>
        </div>
        <p className="px-lg text-[11px] text-white/40 mb-sm">PLATFORM ADMIN</p>
        <nav className="flex-1 px-sm space-y-1">
          {navItems.map((n) => (
            <button key={n.key} onClick={() => gotoPage(n.key)} className={navBtnCls(page === n.key)}>
              <Icon name={n.icon} /><span>{n.label}</span>
            </button>
          ))}
        </nav>
        <div className="p-lg border-t border-white/10">
          <Link to="/" className="block w-full py-xs text-center text-caption text-white/70 border border-white/20 rounded-lg hover:bg-white/10 transition-colors">메인 사이트로</Link>
        </div>
      </aside>
      {sidebarOpen && <div onClick={() => setSidebarOpen(false)} className="fixed inset-0 bg-black/40 z-40 md:hidden" />}

      <main className="md:ml-[260px] min-h-screen">
        <header className="sticky top-0 z-30 bg-white/70 backdrop-blur-xl border-b border-hairline px-lg h-[64px] flex items-center justify-between">
          <div className="flex items-center gap-sm">
            <button onClick={() => setSidebarOpen(true)} className="md:hidden"><Icon name="menu" /></button>
            <h2 className="font-display-md text-[20px]">{navItems.find((n) => n.key === page).label}</h2>
          </div>
          <span className="text-caption text-ink-muted">EXPO HUB 플랫폼</span>
        </header>

        <div className="p-lg md:p-xl space-y-section max-w-[1200px] mx-auto">
          {loading && <div className="bg-white border border-hairline rounded-xl p-lg text-caption text-ink-muted">관리 데이터를 불러오는 중입니다.</div>}
          {loadError && (
            <div className="bg-error/10 border border-error/20 rounded-xl p-lg text-caption text-error flex justify-between">
              <span>{loadError}</span><button onClick={loadAdminData} className="font-body-strong">다시 시도</button>
            </div>
          )}
          {/* DASHBOARD */}
          {page === "dashboard" && (
            <section className="space-y-lg">
              <div className="grid grid-cols-1 md:grid-cols-4 gap-lg">
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">행사 승인 대기</span><Icon name="verified" className="text-status-pending" /></div>
                  <span className="font-display-md text-[26px]">{pendingReq.length}</span>
                </div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">활성 계정 수</span><Icon name="group" className="text-status-assigned" /></div>
                  <span className="font-display-md text-[26px]">{activeAccounts}</span>
                </div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">광고 검토 대기</span><Icon name="campaign" className="text-status-available" /></div>
                  <span className="font-display-md text-[26px]">{pendingAds}</span>
                </div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">진행 중인 행사</span><Icon name="event" className="text-status-visited" /></div>
                  <span className="font-display-md text-[26px]">6</span>
                </div>
              </div>
              <div className="bg-white border border-hairline rounded-xl overflow-hidden">
                <div className="flex justify-between items-center px-lg py-md border-b border-hairline">
                  <h3 className="font-body-strong">승인 대기 행사</h3>
                  <button onClick={() => gotoPage("requests")} className="text-caption text-primary font-body-strong">전체 보기</button>
                </div>
                <div className="divide-y divide-divider-soft">
                  {pendingReq.length === 0 ? (
                    <p className="p-lg text-caption text-ink-muted">대기 중인 요청이 없습니다.</p>
                  ) : (
                    pendingReq.map((r) => (
                      <div key={r.id} className="flex items-center justify-between p-lg gap-md">
                        <div><p className="font-body-strong text-[14px]">{r.name}</p><p className="text-caption text-ink-muted">{r.organizer} · 제출 {r.submitted}</p></div>
                        <div className="flex gap-xs flex-shrink-0">
                          <button onClick={() => approveReq(r.id)} className="w-8 h-8 rounded-full bg-status-available/10 text-status-available flex items-center justify-center"><Icon name="check" className="text-[16px]" /></button>
                          <button onClick={() => rejectReq(r.id)} className="w-8 h-8 rounded-full bg-status-visited/10 text-status-visited flex items-center justify-center"><Icon name="close" className="text-[16px]" /></button>
                        </div>
                      </div>
                    ))
                  )}
                </div>
              </div>
            </section>
          )}

          {/* REQUESTS */}
          {page === "requests" && (
            <section className="space-y-lg">
              <h1 className="font-display-lg text-[26px]">행사 등록 신청 관리</h1>
              <div className="space-y-md">
                {requests.map((r) => (
                  <div key={r.id} className="bg-white border border-hairline rounded-xl p-lg">
                    <div className="flex justify-between items-start mb-sm">
                      <div>
                        <p className="font-body-strong">{r.name}</p>
                        <p className="text-caption text-ink-muted">{r.organizer} · 제출 {r.submitted}</p>
                      </div>
                      {r.status === "pending" ? (
                        <div className="flex gap-xs">
                          <button onClick={() => approveReq(r.id)} className="w-9 h-9 rounded-full bg-status-available/10 text-status-available flex items-center justify-center"><Icon name="check" className="text-[18px]" /></button>
                          <button onClick={() => rejectReq(r.id)} className="w-9 h-9 rounded-full bg-status-visited/10 text-status-visited flex items-center justify-center"><Icon name="close" className="text-[18px]" /></button>
                        </div>
                      ) : (
                        <span className={`text-[11px] font-bold px-sm py-1 rounded-full ${r.status === "approved" ? "bg-status-available/10 text-status-available" : r.status === "rejected" ? "bg-status-visited/10 text-status-visited" : "bg-surface-container text-ink-muted"}`}>
                          {eventStatusLabel[r.status] || r.rawStatus}
                        </span>
                      )}
                    </div>
                    <div className="flex flex-wrap gap-md text-caption text-ink-muted bg-surface-container-low rounded-lg p-sm">
                      <span className="flex items-center gap-1"><Icon name="location_on" className="text-[15px]" />{r.place}</span>
                      <span className="flex items-center gap-1"><Icon name="grid_view" className="text-[15px]" />{r.booth}</span>
                      {r.status === "rejected" && r.reason && <span className="text-error">반려 사유: {r.reason}</span>}
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* ACCOUNTS */}
          {page === "accounts" && (
            <section className="space-y-lg">
              <h1 className="font-display-lg text-[26px]">전체 계정 관리</h1>
              <div className="flex gap-xs">
                {accTabs.map((t) => (
                  <button key={t} onClick={() => setAccFilter(t)} className={`px-md py-1.5 rounded-full text-caption font-body-strong ${accFilter === t ? "bg-black text-white" : "bg-white border border-hairline text-on-surface-variant"}`}>{t}</button>
                ))}
              </div>
              <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
                {accountList.map((a) => (
                  <div key={a.id} className="flex items-center gap-md p-lg">
                    <div className="w-9 h-9 rounded-lg bg-surface-container flex items-center justify-center flex-shrink-0"><Icon name="group" className="text-[16px] text-ink-muted" /></div>
                    <div className="flex-1"><p className="font-body-strong text-[14px]">{a.name}</p><p className="text-caption text-ink-muted">{a.role}</p></div>
                    <span className={`text-caption ${a.active ? "text-status-available" : "text-ink-muted"}`}>{a.active ? "활성" : "비활성"}</span>
                    <button onClick={() => toggleAccount(a.id)} className={`w-10 h-6 rounded-full relative ${a.active ? "bg-status-available" : "bg-hairline"}`}>
                      <span className="absolute top-0.5 w-5 h-5 bg-white rounded-full transition-all" style={{ left: a.active ? "18px" : "2px" }} />
                    </button>
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* ADS */}
          {page === "ads" && (
            <section className="space-y-lg">
              <h1 className="font-display-lg text-[26px]">광고 입금 확인 및 승인</h1>
              <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
                {ads.map((a) => (
                  <div key={a.id} className="flex items-center gap-md p-lg">
                    <div className="w-9 h-9 rounded-lg bg-surface-container flex items-center justify-center flex-shrink-0"><Icon name="payments" className="text-[16px] text-ink-muted" /></div>
                    <div className="flex-1">
                      <p className="font-body-strong text-[14px]">{a.target}</p>
                      <p className="text-caption text-ink-muted">
                        {a.type} · {a.amount} · <span className={a.payment === "confirmed" ? "text-status-available" : "text-status-pending"}>{a.payment === "confirmed" ? "입금 확인됨" : "입금 대기"}</span>
                      </p>
                    </div>
                    <div>
                      {a.status === "pending" ? (
                        a.payment === "confirmed" ? (
                          <div className="flex gap-xs">
                            <button onClick={() => decideAd(a.id, "approved")} className="w-8 h-8 rounded-full bg-status-available/10 text-status-available flex items-center justify-center"><Icon name="check" className="text-[16px]" /></button>
                            <button onClick={() => decideAd(a.id, "rejected")} className="w-8 h-8 rounded-full bg-status-visited/10 text-status-visited flex items-center justify-center"><Icon name="close" className="text-[16px]" /></button>
                          </div>
                        ) : (
                          <span className="px-md py-1.5 border border-hairline rounded-full text-caption text-ink-muted">결제 대기</span>
                        )
                      ) : (
                        <span className={`text-[11px] font-bold px-sm py-1 rounded-full ${a.status === "approved" ? "bg-status-available/10 text-status-available" : "bg-status-visited/10 text-status-visited"}`}>
                          {a.status === "approved" ? "승인됨" : "반려됨"}
                        </span>
                      )}
                    </div>
                  </div>
                ))}
              </div>
            </section>
          )}

          {/* STATS */}
          {page === "stats" && (
            <section className="space-y-lg">
              <div className="flex justify-between items-center">
                <h1 className="font-display-lg text-[26px]">통합 통계</h1>
                <button className="px-lg py-sm border border-hairline rounded-full text-caption font-body-strong">CSV로 다운로드</button>
              </div>
              <div className="grid grid-cols-1 md:grid-cols-4 gap-lg">
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline"><span className="font-display-md text-[24px] block">6</span><span className="text-caption text-ink-muted">진행 중 행사</span></div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline"><span className="font-display-md text-[24px] block">1,284</span><span className="text-caption text-ink-muted">누적 예매자 수</span></div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline"><span className="font-display-md text-[24px] block">96</span><span className="text-caption text-ink-muted">등록 참가기업 수</span></div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline"><span className="font-display-md text-[24px] block">₩12.4M</span><span className="text-caption text-ink-muted">누적 광고 매출</span></div>
              </div>
            </section>
          )}

          {/* AUDIT */}
          {page === "audit" && (
            <section className="space-y-lg">
              <h1 className="font-display-lg text-[26px]">감사 로그</h1>
              <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft text-caption">
                <div className="flex gap-md p-lg"><span className="text-ink-muted w-24 flex-shrink-0">07.29 10:12</span>관리자(admin01)가 '친환경 에너지 컨퍼런스' 행사를 승인했습니다.</div>
                <div className="flex gap-md p-lg"><span className="text-ink-muted w-24 flex-shrink-0">07.29 09:40</span>관리자(admin01)가 '무자격 팝업 마켓' 행사를 반려했습니다.</div>
                <div className="flex gap-md p-lg"><span className="text-ink-muted w-24 flex-shrink-0">07.28 18:02</span>개최자(코엑스 이벤트)가 견적서 파일을 다운로드했습니다.</div>
                <div className="flex gap-md p-lg"><span className="text-ink-muted w-24 flex-shrink-0">07.28 15:20</span>회원(김서연)에게 입장 QR이 발급되었습니다.</div>
              </div>
            </section>
          )}
        </div>
      </main>
    </div>
  );
}
