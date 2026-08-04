import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import NotificationBell from "../components/NotificationBell.jsx";

const applyBooths = [
  { id: "A01", elec: true,  water: false, status: "available", price: 3500000 },
  { id: "A02", elec: true,  water: true,  status: "assigned",  price: 3500000 },
  { id: "A03", elec: false, water: false, status: "pending",   price: 3500000 },
  { id: "A04", elec: true,  water: false, status: "available", price: 3500000 },
  { id: "A05", elec: true,  water: true,  status: "available", price: 6000000 },
  { id: "A06", elec: false, water: true,  status: "blocked",   price: 3500000 },
  { id: "A07", elec: true,  water: false, status: "available", price: 3500000 },
  { id: "A08", elec: true,  water: true,  status: "assigned",  price: 6000000 },
  { id: "A09", elec: false, water: false, status: "available", price: 3500000 },
  { id: "A10", elec: true,  water: false, status: "assigned",  price: 3500000 },
];

export default function BoothApply() {
  const [params] = useSearchParams();
  const [filters, setFilters] = useState({ elec: false, water: false });
  const [selectedBooth, setSelectedBooth] = useState(null);
  const [fileOk, setFileOk] = useState(false);
  const [submitted, setSubmitted] = useState(false);

  const isEligible = (b) =>
    b.status === "available" && (!filters.elec || b.elec) && (!filters.water || b.water);

  // preselect via ?booth=
  useEffect(() => {
    const pre = params.get("booth");
    if (pre && applyBooths.some((b) => b.id === pre && isEligible(b))) {
      setSelectedBooth(pre);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const toggleFilter = (key) => {
    const next = { ...filters, [key]: !filters[key] };
    setFilters(next);
    if (selectedBooth) {
      const b = applyBooths.find((x) => x.id === selectedBooth);
      const eligibleNext =
        b.status === "available" && (!next.elec || b.elec) && (!next.water || b.water);
      if (!eligibleNext) setSelectedBooth(null);
    }
  };

  const selected = applyBooths.find((x) => x.id === selectedBooth);
  const canSubmit = selectedBooth && fileOk && !submitted;

  const filterBtnCls = (on) =>
    `px-lg py-sm rounded-full border font-body text-body flex items-center gap-xs transition-all ${
      on ? "border-2 border-primary-focus bg-surface-pearl font-body-strong" : "border-hairline"
    }`;

  return (
    <div className="bg-background text-on-surface">
      {/* Top Nav */}
      <header className="fixed top-0 w-full h-[44px] z-[100] bg-black flex justify-between items-center px-lg">
        <Link to="/" className="font-hero-display text-tagline text-white">EXPO HUB</Link>
        <div className="flex items-center gap-sm">
          <NotificationBell />
          <Link to="/event-recruiting" className="text-white/80 hover:text-white text-nav-link font-nav-link flex items-center gap-1">
            <Icon name="arrow_back" className="text-[18px]" /> 모집 공고로 돌아가기
          </Link>
        </div>
      </header>

      <main className="max-w-[1200px] mx-auto min-h-screen pt-[80px] pb-section px-lg">
        <div className="grid grid-cols-1 lg:grid-cols-12 gap-xl">
          {/* Left: Conditions + Booth map */}
          <div className="lg:col-span-7 flex flex-col gap-lg">
            <div className="flex flex-col gap-xs">
              <h1 className="font-hero-display text-[32px] md:text-[40px] font-semibold tracking-tight">참가 부스 선택</h1>
              <p className="text-secondary text-body">필요한 설비 조건을 선택하면 조건에 맞지 않는 부스는 선택할 수 없어요.</p>
            </div>

            {/* Condition filters */}
            <div className="bg-white rounded-xl border border-hairline p-lg flex flex-wrap gap-sm">
              <button onClick={() => toggleFilter("elec")} className={filterBtnCls(filters.elec)}>
                <Icon name="bolt" className="text-[18px]" /> 전기 사용 필요
              </button>
              <button onClick={() => toggleFilter("water")} className={filterBtnCls(filters.water)}>
                <Icon name="water_drop" className="text-[18px]" /> 급수·배수 필요
              </button>
            </div>

            <div className="bg-white rounded-xl border border-hairline p-lg relative">
              <div className="grid grid-cols-5 gap-sm">
                {applyBooths.map((b) => {
                  const eligible = isEligible(b);
                  const active = selectedBooth === b.id;
                  return (
                    <button
                      key={b.id}
                      disabled={!eligible}
                      onClick={eligible ? () => setSelectedBooth(b.id) : undefined}
                      className={`booth-cell h-16 rounded-lg text-[11px] font-bold flex items-center justify-center transition-all ${
                        !eligible
                          ? "bg-surface-container text-ink-muted cursor-not-allowed opacity-60"
                          : active
                          ? "bg-primary-container text-white ring-2 ring-primary-focus"
                          : "bg-white border border-hairline text-on-surface hover:border-primary"
                      }`}
                    >
                      {b.id}
                    </button>
                  );
                })}
              </div>
              <p className="text-[12px] text-primary-focus font-medium mt-md flex items-center gap-1">
                <Icon name="info" className="text-[16px]" /> 회색 부스는 조건 불일치 또는 이미 신청·배정되어 선택할 수 없습니다.
              </p>
            </div>

            {/* Docs upload */}
            <div className="bg-white rounded-xl border border-hairline p-lg">
              <h3 className="font-body-strong text-body mb-sm">필수 제출 서류</h3>
              <p className="text-[14px] text-secondary mb-lg">사업자등록증, 견적서 등 필수 서류를 제출하세요.</p>
              <label className={`flex items-center justify-center gap-xs border-2 border-dashed rounded-lg p-sm cursor-pointer hover:bg-surface-pearl transition-colors ${
                fileOk ? "border-status-available bg-status-available/5" : "border-outline-variant"
              }`}>
                <input type="file" className="hidden" onChange={() => setFileOk(true)} />
                <Icon name={fileOk ? "check_circle" : "cloud_upload"} className={fileOk ? "text-status-available" : "text-secondary"} />
                <span className="text-[14px] text-secondary font-medium">{fileOk ? "견적서.pdf 첨부 완료" : "견적서 파일 선택하기"}</span>
              </label>
            </div>
          </div>

          {/* Right: Registration form */}
          <div className="lg:col-span-5">
            <div className="sticky top-[120px] bg-white rounded-2xl border border-hairline shadow-xl overflow-hidden">
              <div className="p-xl bg-on-primary-fixed text-white">
                <h2 className="text-[28px] font-semibold tracking-tight">부스 신청서</h2>
                <p className="text-white/60 text-[14px]">기업 정보를 입력하고 설비 옵션을 확인하세요.</p>
              </div>
              <div className="p-xl flex flex-col gap-xl">
                <div className="space-y-sm">
                  <label className="block text-[14px] font-bold text-on-surface">참가 기업명</label>
                  <input type="text" placeholder="공식 기업명을 입력하세요" className="w-full h-[44px] rounded-lg border border-hairline px-sm focus:border-primary-focus focus:ring-2 focus:ring-primary-focus/20 outline-none transition-all" />
                </div>
                <div className="space-y-sm">
                  <label className="block text-[14px] font-bold text-on-surface">담당자 연락처</label>
                  <input type="text" placeholder="010-0000-0000" className="w-full h-[44px] rounded-lg border border-hairline px-sm focus:border-primary-focus focus:ring-2 focus:ring-primary-focus/20 outline-none transition-all" />
                </div>

                <div className="space-y-sm border-t border-hairline pt-lg">
                  <label className="block text-[14px] font-bold text-on-surface">선택한 부스</label>
                  <div className="relative">
                    {selected ? (
                      <div className="w-full h-[44px] rounded-lg border-2 border-primary-focus px-sm bg-white text-[14px] flex items-center font-body-strong text-primary">
                        {selected.id} 부스 · 9sqm 기본형
                      </div>
                    ) : (
                      <div className="w-full h-[44px] rounded-lg border border-hairline px-sm bg-surface-pearl text-[14px] flex items-center text-ink-muted">
                        왼쪽 배치도에서 부스를 선택해 주세요
                      </div>
                    )}
                  </div>
                </div>

                {/* Total Quote Summary */}
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-center mb-xs">
                    <span className="text-[14px] text-secondary">부스 임차료</span>
                    <span className="text-[14px] font-medium">₩ {selected ? selected.price.toLocaleString() : "0"}</span>
                  </div>
                  <div className="flex justify-between items-center mb-xs">
                    <span className="text-[14px] text-secondary">설비 추가 비용</span>
                    <span className="text-[14px] font-medium">₩ 0</span>
                  </div>
                  <div className="border-t border-hairline my-sm pt-sm flex justify-between items-center">
                    <span className="text-[17px] font-bold">총 예상 견적</span>
                    <span className="text-[21px] font-bold text-primary">₩ {selected ? selected.price.toLocaleString() : "0"}</span>
                  </div>
                </div>

                <button
                  onClick={() => setSubmitted(true)}
                  disabled={!canSubmit}
                  className={`w-full text-white h-[52px] rounded-xl font-bold text-[17px] active:scale-95 duration-200 shadow-lg disabled:opacity-40 disabled:cursor-not-allowed ${
                    submitted ? "bg-status-available shadow-status-available/20" : "bg-primary-focus shadow-primary-focus/20"
                  }`}
                >
                  {submitted ? "신청이 완료되었습니다" : "부스 신청 완료하기"}
                </button>
                <p className="text-[12px] text-ink-muted text-center">
                  {submitted
                    ? `${selectedBooth} 부스는 검토가 완료될 때까지 다른 기업이 선택할 수 없어요. 신청내역은 개최자 승인 후 이메일로 안내됩니다.`
                    : canSubmit
                    ? "모든 조건이 충족되었습니다. 신청서를 제출해 주세요."
                    : "부스 선택과 견적서 첨부를 완료하면 신청할 수 있어요."}
                </p>
              </div>
            </div>
          </div>
        </div>
      </main>

      <footer className="bg-surface-container-low text-on-surface py-section w-full">
        <div className="max-w-[1200px] mx-auto px-lg text-center">
          <p className="text-[14px] text-on-surface-variant opacity-60">© 2026 EXPO HUB. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}
