import { useNavigate, Link } from "react-router-dom";
import Icon from "../components/Icon.jsx";

const applyBooths = [
  { id: "A01", status: "assigned" }, { id: "A02", status: "assigned" }, { id: "A03", status: "pending" },
  { id: "A04", status: "available" }, { id: "A05", status: "assigned" }, { id: "A06", status: "available" },
  { id: "A07", status: "blocked" }, { id: "A08", status: "assigned" }, { id: "A09", status: "available" }, { id: "A10", status: "assigned" },
];
const statusMap = {
  available: { label: "선택 가능", cls: "bg-status-available text-white" },
  pending: { label: "신청 대기", cls: "bg-status-pending text-white" },
  assigned: { label: "배정 완료", cls: "bg-status-assigned text-white" },
  blocked: { label: "사용 불가", cls: "bg-status-blocked text-white" },
};

export default function EventRecruiting() {
  const navigate = useNavigate();

  return (
    <div className="bg-surface text-on-surface">
      {/* Top Nav */}
      <header className="fixed top-0 w-full h-[44px] z-[100] bg-black flex justify-between items-center px-lg">
        <Link to="/" className="font-hero-display text-tagline text-white">EXPO HUB</Link>
        <nav className="hidden md:flex gap-xl h-full items-center">
          <Link className="text-white/80 hover:text-white font-nav-link text-nav-link transition-colors" to="/">전체 행사</Link>
          <Link className="text-primary-on-dark font-bold border-b-2 border-primary-on-dark pb-1 font-nav-link text-nav-link" to="/event-recruiting">부스 모집 공고</Link>
        </nav>
        <Link to="/login" className="bg-primary-container text-white px-md py-1.5 rounded-full text-nav-link font-nav-link active:scale-95 transition-transform">로그인</Link>
      </header>

      {/* Secondary nav */}
      <nav className="sticky top-[44px] w-full h-[52px] z-[90] glass-nav border-b border-hairline flex justify-between items-center px-lg">
        <div className="flex items-center gap-lg max-w-[1200px] w-full mx-auto">
          <span className="text-primary font-bold text-[14px]">스마트팩토리 자동화 전시회</span>
          <div className="hidden md:flex gap-lg ml-auto">
            <a href="#overview" className="text-primary font-bold text-[12px]">모집 요강</a>
            <a href="#floorplan" className="text-secondary hover:text-primary-focus transition-colors text-[12px]">부스 배치도</a>
            <Link to="/booth-apply" className="bg-primary text-white px-lg py-1.5 rounded-full text-[12px] font-semibold active:scale-95 transition-transform">부스 신청하기</Link>
          </div>
        </div>
      </nav>

      <main className="max-w-[1200px] mx-auto px-lg py-xl">
        {/* Overview */}
        <section id="overview" className="grid grid-cols-1 lg:grid-cols-12 gap-xxl items-start mb-section">
          <div className="lg:col-span-7 space-y-lg">
            <div className="rounded-3xl overflow-hidden aspect-[16/9] flex items-center justify-center text-white" style={{ background: "linear-gradient(135deg,#2b5876,#4e4376)" }}>
              <Icon name="precision_manufacturing" className="text-[64px] opacity-90" />
            </div>
            <div>
              <p className="text-primary font-body-strong text-tagline mb-sm">산업기술 · IT/제조</p>
              <h1 className="font-display-lg text-display-lg mb-md leading-tight">스마트팩토리 자동화 전시회</h1>
              <p className="font-body text-lead text-secondary">차세대 산업 자동화 솔루션을 위한 국내 최대 규모의 제조기술 전시회입니다. 귀사의 자동화·로보틱스 기술을 현장에서 선보이세요.</p>
            </div>
            <div className="border-t border-hairline pt-md">
              <h4 className="font-body-strong text-body mb-sm">참가 자격</h4>
              <ul className="font-body text-caption text-secondary space-y-xxs">
                <li className="flex items-start gap-xs"><Icon name="check_circle" className="text-primary-focus text-[18px]" />산업설비·로보틱스 관련 법인 기업</li>
                <li className="flex items-start gap-xs"><Icon name="check_circle" className="text-primary-focus text-[18px]" />설립 3년 이상의 실적 보유 기업</li>
                <li className="flex items-start gap-xs"><Icon name="check_circle" className="text-primary-focus text-[18px]" />신규 자동화 솔루션 런칭 계획 보유 우대</li>
              </ul>
            </div>
            <div className="border-t border-hairline pt-md">
              <h4 className="font-body-strong text-body mb-sm">비용 안내</h4>
              <div className="space-y-xs">
                <div className="flex justify-between font-body text-caption"><span className="text-secondary">기본 부스 (9sqm)</span><span className="font-body-strong">3,500,000원</span></div>
                <div className="flex justify-between font-body text-caption"><span className="text-secondary">독립 부스 (최소 18sqm)</span><span className="font-body-strong">6,000,000원 ~</span></div>
                <p className="text-[12px] text-ink-muted">* VAT 별도, 조기 신청 시 10% 할인</p>
              </div>
            </div>
            <div className="border-t border-hairline pt-md">
              <h4 className="font-body-strong text-body mb-sm">필수 제출 서류</h4>
              <div className="flex flex-wrap gap-xs">
                {["사업자등록증", "참가신청서", "견적서", "제품 소개 카탈로그"].map((t) => (
                  <span key={t} className="px-sm py-1 bg-surface-container rounded-lg text-caption text-on-surface-variant">{t}</span>
                ))}
              </div>
            </div>
          </div>

          {/* Right: recruiting status card */}
          <div className="lg:col-span-5">
            <div className="sticky top-[120px] bg-white rounded-2xl border border-hairline shadow-xl overflow-hidden">
              <div className="p-xl bg-on-primary-fixed text-white">
                <span className="inline-block bg-error text-white text-[11px] font-bold px-sm py-1 rounded-full mb-sm">마감 D-3</span>
                <h2 className="text-[24px] font-semibold tracking-tight">부스 모집 현황</h2>
                <p className="text-white/60 text-[13px]">모집 기간 2026.07.01 – 08.01</p>
              </div>
              <div className="p-xl flex flex-col gap-lg">
                <div>
                  <div className="flex justify-between text-caption mb-xs"><span className="text-secondary">배정 진행률</span><span className="font-body-strong">6 / 10 부스</span></div>
                  <div className="w-full h-2 bg-surface-container rounded-full overflow-hidden"><div className="bg-primary h-full" style={{ width: "60%" }} /></div>
                </div>
                <div className="grid grid-cols-2 gap-sm text-center">
                  <div className="bg-surface-pearl rounded-xl p-md">
                    <p className="font-display-md text-[22px] text-status-assigned">6</p>
                    <p className="text-[11px] text-ink-muted">배정 완료</p>
                  </div>
                  <div className="bg-surface-pearl rounded-xl p-md">
                    <p className="font-display-md text-[22px] text-status-available">3</p>
                    <p className="text-[11px] text-ink-muted">선택 가능</p>
                  </div>
                </div>
                <Link to="/booth-apply" className="w-full bg-primary-focus text-white h-[52px] rounded-xl font-body-strong text-body active:scale-95 transition-transform shadow-lg shadow-primary-focus/20 flex items-center justify-center gap-xs">
                  부스 신청하러 가기 <Icon name="arrow_forward" className="text-[18px]" />
                </Link>
                <button className="w-full h-[48px] border border-primary text-primary rounded-xl font-body-strong text-caption hover:bg-surface-pearl transition-colors flex items-center justify-center gap-xs">
                  <Icon name="download" className="text-[18px]" /> 모집 공고문 다운로드 (.PDF)
                </button>
              </div>
            </div>
          </div>
        </section>

        {/* Floor plan */}
        <section id="floorplan" className="mb-section">
          <div className="flex items-center justify-between mb-md">
            <h2 className="font-display-md text-[22px]">부스 배치도 · 배정 현황</h2>
          </div>
          <div className="floor-map-container bg-surface-pearl border border-hairline rounded-2xl p-lg">
            <div className="grid grid-cols-5 gap-sm">
              {applyBooths.map((b) => {
                const m = statusMap[b.status];
                const clickable = b.status === "available";
                return (
                  <div
                    key={b.id}
                    onClick={clickable ? () => navigate(`/booth-apply?booth=${b.id}`) : undefined}
                    className={`h-20 rounded-lg flex flex-col items-center justify-center text-[10px] font-bold ${m.cls} ${clickable ? "cursor-pointer hover:opacity-90" : ""}`}
                  >
                    <span>{b.id}</span>
                    <span className="text-[9px] opacity-80 mt-1">{m.label}</span>
                  </div>
                );
              })}
            </div>
          </div>
          <div className="flex flex-wrap gap-md mt-md text-caption text-on-surface-variant">
            <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-status-available" />선택 가능</span>
            <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-status-pending" />신청 대기</span>
            <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-status-assigned" />배정 완료</span>
            <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-status-blocked" />사용 불가</span>
          </div>
          <div className="text-center mt-lg">
            <Link to="/booth-apply" className="inline-flex items-center gap-xs bg-primary text-white px-xl py-md rounded-full font-body-strong active:scale-95 transition-transform">
              원하는 부스로 신청하기 <Icon name="arrow_forward" className="text-[18px]" />
            </Link>
          </div>
        </section>
      </main>

      {/* Footer */}
      <footer className="w-full py-section bg-surface-container-low border-t border-hairline">
        <div className="grid grid-cols-2 md:grid-cols-4 gap-lg px-lg max-w-[1200px] mx-auto">
          <div className="col-span-2 md:col-span-1">
            <div className="font-hero-display text-tagline text-on-surface mb-md">EXPO HUB</div>
            <p className="text-caption text-on-surface-variant">© 2026 EXPO HUB. All rights reserved.</p>
          </div>
          <div className="flex flex-col gap-sm">
            <span className="font-body-strong text-caption">내비게이션</span>
            <Link className="text-caption text-on-surface-variant hover:underline" to="/">전체 행사</Link>
            <Link className="text-caption text-on-surface-variant hover:underline" to="/event-recruiting">부스 모집 공고</Link>
          </div>
          <div className="flex flex-col gap-sm">
            <span className="font-body-strong text-caption">고객 지원</span>
            <a className="text-caption text-on-surface-variant hover:underline" href="#">이용약관</a>
            <a className="text-caption text-on-surface-variant hover:underline" href="#">개인정보처리방침</a>
          </div>
        </div>
      </footer>
    </div>
  );
}
