import { useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import NotificationBell from "../components/NotificationBell.jsx";

const boothData = {
  A01: { name: "맛있는 식탁", zone: "A구역 1층 · A01", congestion: 62, icon: "lunch_dining", color: "linear-gradient(135deg,#ff9966,#ff5e62)", tags: ["시식 체험", "밀키트 판매"], desc: "제철 재료로 완성하는 가정식 밀키트 브랜드입니다. 현장에서 대표 메뉴 3종을 시식할 수 있어요.", rating: 4.6 },
  A02: { name: "그린 키친랩", zone: "A구역 1층 · A02", congestion: 30, icon: "blender", color: "linear-gradient(135deg,#11998e,#38ef7d)", tags: ["친환경 설비", "체험존"], desc: "친환경 조리기기로 완성하는 지속가능한 주방을 소개합니다. 에너지 효율을 높인 인덕션 라인업을 직접 체험해보세요.", rating: 4.7 },
  A03: { name: "베이크하우스", zone: "A구역 1층 · A03", congestion: 88, icon: "bakery_dining", color: "linear-gradient(135deg,#f7971e,#ffd200)", tags: ["시식 체험", "베이커리"], desc: "매일 아침 굽는 유러피안 베이커리입니다. 시그니처 크루아상과 소금빵을 현장에서 맛보실 수 있어요.", rating: 4.5 },
  A05: { name: "스마트키친 로보틱스", zone: "A구역 2층 · A05", congestion: 71, icon: "smart_toy", color: "linear-gradient(135deg,#2b5876,#4e4376)", tags: ["로봇 시연", "체험존"], desc: "조리 로봇이 만드는 1인분 레시피를 직접 시연합니다. 대기 인원이 많으니 예약을 추천드려요.", rating: 4.8 },
};

const levelInfo = (v) => {
  if (v >= 70) return { label: "혼잡 · 예약 권장", color: "status-visited", icon: "warning" };
  if (v >= 40) return { label: "보통 · 예약 가능", color: "status-pending", icon: "schedule" };
  return { label: "여유 · 바로 방문 가능", color: "status-available", icon: "event_available" };
};

const initialSlots = [
  { time: "13:00", full: false },
  { time: "13:30", full: false },
  { time: "14:00", full: true },
  { time: "14:30", full: false },
  { time: "15:00", full: false },
  { time: "15:30", full: false },
];

export default function BoothDetail() {
  const [params] = useSearchParams();
  const boothId = boothData[params.get("booth")] ? params.get("booth") : "A03";
  const b = boothData[boothId];
  const lvl = levelInfo(b.congestion);

  const [selectedSlot, setSelectedSlot] = useState(initialSlots.find((s) => !s.full).time);
  const [people, setPeople] = useState(1);
  const [reserved, setReserved] = useState(false);

  const changePeople = (d) => setPeople((p) => Math.max(1, Math.min(4, p + d)));

  return (
    <div className="bg-surface-container-lowest text-on-surface">
      {/* Top Nav */}
      <header className="fixed top-0 w-full h-[44px] z-[100] bg-black flex justify-between items-center px-lg">
        <Link to="/" className="font-hero-display text-tagline text-white">EvenToday</Link>
        <div className="flex items-center gap-sm">
          <NotificationBell />
          <Link to="/event-ongoing" className="text-white/80 hover:text-white text-nav-link font-nav-link flex items-center gap-1">
            <Icon name="arrow_back" className="text-[18px]" /> 행사로 돌아가기
          </Link>
        </div>
      </header>

      <main className="pt-[44px] pb-xxl">
        <div className="max-w-[1200px] mx-auto px-lg py-xl">
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-xl items-start">
            {/* Left: Images */}
            <div className="lg:col-span-7 space-y-lg">
              <div className="rounded-3xl overflow-hidden aspect-[16/10] flex items-center justify-center text-white relative" style={{ background: b.color }}>
                <Icon name={b.icon} className="text-[72px] opacity-90" />
                <span className={`absolute top-lg left-lg px-md py-1.5 text-caption font-bold rounded-full bg-white/90 text-${lvl.color}`}>
                  {lvl.label.split(" · ")[0]}
                </span>
              </div>
              <div className="grid grid-cols-3 gap-md">
                {[0, 1, 2].map((i) => (
                  <div key={i} className="rounded-2xl overflow-hidden bg-surface-pearl aspect-square flex items-center justify-center border border-hairline">
                    <Icon name="image" className="text-[28px] text-ink-muted" />
                  </div>
                ))}
              </div>

              <div className="border-t border-hairline pt-lg">
                <h3 className="font-body-strong text-body-strong mb-sm">부스 소개</h3>
                <p className="text-body text-on-surface-variant leading-relaxed">{b.desc}</p>
              </div>

              <div className="border-t border-hairline pt-lg">
                <h3 className="font-body-strong text-body-strong mb-sm">평균 별점</h3>
                <div className="flex items-center gap-sm">
                  <span className="font-display-md text-[26px]">{b.rating}</span>
                  <div className="flex">
                    {[1, 2, 3, 4, 5].map((i) => (
                      <Icon key={i} name="star" fill={i <= Math.round(b.rating)} className={`text-[18px] ${i <= Math.round(b.rating) ? "text-amber-500" : "text-hairline"}`} />
                    ))}
                  </div>
                  <span className="text-caption text-ink-muted">방문객 후기 82건</span>
                </div>
              </div>
            </div>

            {/* Right: Reservation panel */}
            <div className="lg:col-span-5">
              <div className="sticky top-[80px] bg-white rounded-2xl border border-hairline shadow-lg overflow-hidden">
                <div className="p-xl">
                  <p className="text-primary font-body-strong text-tagline mb-1">{b.zone.split(" · ")[0]}</p>
                  <h1 className="font-display-lg text-display-lg mb-sm leading-tight">{b.name}</h1>
                  <div className="flex flex-wrap gap-xs mb-lg">
                    {b.tags.map((t) => (
                      <span key={t} className="px-sm py-1 bg-surface-container rounded-lg text-caption text-on-surface-variant">{t}</span>
                    ))}
                  </div>

                  {/* Availability */}
                  <div className={`rounded-xl p-md mb-lg flex items-center gap-sm bg-${lvl.color}/10`}>
                    <Icon name={lvl.icon} className={`text-${lvl.color}`} />
                    <div>
                      <p className={`font-body-strong text-${lvl.color}`}>{lvl.label}</p>
                      <p className="text-caption text-ink-muted">실시간 혼잡도 기준 예약 가능 인원이 조정돼요</p>
                    </div>
                  </div>

                  {/* Time slots */}
                  <div className="mb-lg">
                    <h3 className="font-body-strong text-body-strong mb-sm">예약 시간 선택</h3>
                    <div className="grid grid-cols-3 gap-sm">
                      {initialSlots.map((s) => {
                        const active = selectedSlot === s.time;
                        return (
                          <button
                            key={s.time}
                            disabled={s.full}
                            onClick={() => setSelectedSlot(s.time)}
                            className={`py-sm rounded-lg border text-caption font-body-strong transition-all ${
                              s.full
                                ? "border-hairline text-ink-muted line-through opacity-50 cursor-not-allowed"
                                : active
                                ? "border-2 border-primary-focus text-primary bg-surface-pearl"
                                : "border-hairline hover:border-outline"
                            }`}
                          >
                            {s.full ? `${s.time} 마감` : s.time}
                          </button>
                        );
                      })}
                    </div>
                  </div>

                  {/* People stepper */}
                  <div className="flex items-center justify-between mb-lg border-t border-hairline pt-lg">
                    <span className="font-body-strong">인원 수</span>
                    <div className="flex items-center gap-md">
                      <button onClick={() => changePeople(-1)} className="w-9 h-9 rounded-full border border-hairline flex items-center justify-center"><Icon name="remove" className="text-[18px]" /></button>
                      <span className="font-body-strong text-[18px] w-6 text-center">{people}</span>
                      <button onClick={() => changePeople(1)} className="w-9 h-9 rounded-full border border-hairline flex items-center justify-center"><Icon name="add" className="text-[18px]" /></button>
                    </div>
                  </div>

                  <button
                    onClick={() => setReserved(true)}
                    disabled={reserved}
                    className={`w-full text-white h-[52px] rounded-xl font-body-strong text-body active:scale-[0.98] transition-all shadow-lg flex items-center justify-center gap-xs ${
                      reserved ? "bg-status-available shadow-status-available/20" : "bg-primary-focus shadow-primary-focus/20"
                    }`}
                  >
                    {reserved ? (
                      <><Icon name="check_circle" /> {selectedSlot} · {people}명 예약 완료</>
                    ) : (
                      "선택한 시간으로 예약하기"
                    )}
                  </button>
                  <p className="text-[12px] text-ink-muted mt-sm text-center">
                    {reserved
                      ? "예약 내역은 마이페이지 > 예약한 부스에서 확인할 수 있어요."
                      : "예약은 회원 전용 기능입니다. 로그인 후 이용해 주세요."}
                  </p>
                </div>
              </div>
            </div>
          </div>
        </div>
      </main>

      <footer className="w-full py-section bg-surface-container-low border-t border-hairline">
        <div className="max-w-[1200px] mx-auto px-lg text-center">
          <p className="text-[12px] text-ink-muted">© 2026 EvenToday. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}
