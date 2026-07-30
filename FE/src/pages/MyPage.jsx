import { useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../components/Icon.jsx";

const tabs = [
  { key: "tickets", label: "예매내역", icon: "confirmation_number" },
  { key: "qr", label: "입장 QR", icon: "qr_code_2" },
  { key: "booths", label: "부스 활동", icon: "favorite" },
  { key: "notif", label: "알림", icon: "notifications" },
  { key: "profile", label: "회원정보", icon: "person" },
];
const subtabs = [
  { key: "interest", label: "관심 부스" },
  { key: "reserved", label: "예약 내역" },
  { key: "visited", label: "방문한 부스" },
];
const qrPixels = Array.from({ length: 100 }, (_, i) => (i * 41 + 7) % 7 < 3);

export default function MyPage() {
  const [tab, setTab] = useState("tickets");
  const [sub, setSub] = useState("interest");
  const [code, setCode] = useState("");
  const [redeemMsg, setRedeemMsg] = useState(null); // { ok, text }
  const [switches, setSwitches] = useState({ soon: true, empty: true });
  const [rating, setRating] = useState(0);

  const redeemCode = () => {
    const val = code.trim();
    if (!val) return setRedeemMsg({ ok: false, text: "교환 코드를 입력해 주세요." });
    if (val.toUpperCase() === "USED123") {
      setRedeemMsg({ ok: false, text: "이미 사용 완료된 교환 코드입니다." });
    } else {
      setRedeemMsg({ ok: true, text: "교환 코드가 확인되어 입장 QR이 발급되었습니다." });
      setCode("");
    }
  };

  const tabBtnCls = (active) =>
    `flex-shrink-0 px-md py-sm rounded-full text-caption font-body-strong flex items-center gap-1 transition-colors ${
      active ? "bg-white text-on-surface" : "text-white/60"
    }`;
  const subBtnCls = (active) =>
    `px-md py-1.5 rounded-full text-caption font-body-strong ${
      active ? "bg-black text-white" : "bg-white border border-hairline text-on-surface-variant"
    }`;

  return (
    <div className="bg-surface-container-low text-on-surface">
      {/* Top Nav */}
      <header className="fixed top-0 w-full h-[44px] z-[100] bg-black flex justify-between items-center px-lg">
        <Link to="/" className="font-hero-display text-tagline text-white">EXPO HUB</Link>
        <Link to="/" className="text-white/80 hover:text-white text-nav-link font-nav-link flex items-center gap-1">
          <Icon name="home" className="text-[18px]" /> 메인으로
        </Link>
      </header>

      <main className="pt-[44px] pb-xxl">
        {/* Profile header */}
        <section className="bg-black text-white px-lg pt-xl pb-lg">
          <div className="max-w-[900px] mx-auto">
            <div className="flex items-center gap-md mb-lg">
              <div className="w-12 h-12 rounded-full bg-surface-tile-dark-alt flex items-center justify-center"><Icon name="person" /></div>
              <div>
                <p className="font-body-strong">김서연 님</p>
                <p className="text-[12px] text-white/50">소셜 로그인 · 카카오 연동</p>
              </div>
            </div>
            <div className="flex gap-xs overflow-x-auto hide-scrollbar">
              {tabs.map((t) => (
                <button key={t.key} onClick={() => setTab(t.key)} className={tabBtnCls(tab === t.key)}>
                  <Icon name={t.icon} className="text-[16px]" />{t.label}
                </button>
              ))}
            </div>
          </div>
        </section>

        <div className="max-w-[900px] mx-auto px-lg py-xl">
          {/* TICKETS */}
          {tab === "tickets" && (
            <div className="space-y-lg">
              <div className="bg-white rounded-2xl border border-hairline p-lg">
                <h3 className="font-body-strong text-body-strong mb-1">교환 코드 등록</h3>
                <p className="text-caption text-ink-muted mb-md">외부 예매처에서 받은 코드를 입력하면 입장 QR이 발급돼요.</p>
                <div className="flex gap-sm">
                  <input
                    value={code}
                    onChange={(e) => setCode(e.target.value)}
                    type="text"
                    placeholder="교환 코드 입력 (예: ABCD-1234)"
                    className="flex-1 h-[44px] rounded-lg border border-hairline px-sm outline-none focus:border-primary-focus"
                  />
                  <button onClick={redeemCode} className="px-lg h-[44px] rounded-lg bg-primary text-white font-body-strong flex items-center gap-1">
                    <Icon name="key" className="text-[18px]" />등록
                  </button>
                </div>
                {redeemMsg && (
                  <p className={`text-caption mt-sm ${redeemMsg.ok ? "text-status-available" : "text-error"}`}>{redeemMsg.text}</p>
                )}
              </div>

              <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
                <div className="p-lg font-body-strong">예매한 행사</div>
                <div className="flex items-center gap-md p-lg">
                  <div className="w-11 h-11 rounded-lg flex items-center justify-center text-white flex-shrink-0" style={{ background: "linear-gradient(135deg,#ff9966,#ff5e62)" }}><Icon name="confirmation_number" className="text-[18px]" /></div>
                  <div className="flex-1">
                    <p className="font-body-strong">2026 서울 푸드테크 박람회</p>
                    <p className="text-caption text-ink-muted">코엑스 · 08.12–08.14</p>
                  </div>
                  <span className="text-[11px] font-bold px-sm py-1 rounded-full bg-status-blocked/10 text-status-blocked">입장 완료</span>
                </div>
                <div className="flex items-center gap-md p-lg">
                  <div className="w-11 h-11 rounded-lg flex items-center justify-center text-white flex-shrink-0" style={{ background: "linear-gradient(135deg,#ee9ca7,#ffdde1)" }}><Icon name="confirmation_number" className="text-[18px]" /></div>
                  <div className="flex-1">
                    <p className="font-body-strong">K-뷰티 & 코스메틱 전시회</p>
                    <p className="text-caption text-ink-muted">코엑스 · 09.10–09.13</p>
                  </div>
                  <span className="text-[11px] font-bold px-sm py-1 rounded-full bg-primary-container/10 text-primary-focus">사용 전</span>
                </div>
              </div>
            </div>
          )}

          {/* QR */}
          {tab === "qr" && (
            <div className="text-center">
              <div className="bg-black rounded-2xl p-xl flex flex-col items-center">
                <div className="grid grid-cols-10 gap-[2px] w-[180px] mb-lg">
                  {qrPixels.map((on, i) => (
                    <div key={i} className={`w-full aspect-square ${on ? "bg-black" : "bg-white"}`} />
                  ))}
                </div>
                <p className="text-white font-display-md text-[18px]">2026 서울 푸드테크 박람회</p>
                <span className="text-white/70 text-caption mt-xs px-md py-1 bg-white/10 rounded-full">회원 입장 QR · 입장 완료</span>
              </div>
            </div>
          )}

          {/* BOOTHS */}
          {tab === "booths" && (
            <div>
              <div className="flex gap-sm mb-md">
                {subtabs.map((s) => (
                  <button key={s.key} onClick={() => setSub(s.key)} className={subBtnCls(sub === s.key)}>{s.label}</button>
                ))}
              </div>
              {sub === "interest" && (
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-md">
                  <div className="bg-white border border-hairline rounded-xl p-md flex items-center gap-sm">
                    <div className="w-10 h-10 rounded-lg flex items-center justify-center text-white" style={{ background: "#2b5876" }}><Icon name="smart_toy" className="text-[16px]" /></div>
                    <div className="flex-1"><p className="text-caption font-body-strong">스마트키친 로보틱스</p></div>
                    <button className="text-ink-muted"><Icon name="close" className="text-[18px]" /></button>
                  </div>
                </div>
              )}
              {sub === "reserved" && (
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-md">
                  <div className="bg-white border border-hairline rounded-xl p-md flex items-center gap-sm">
                    <div className="w-10 h-10 rounded-lg flex items-center justify-center text-white" style={{ background: "#ff5e62" }}><Icon name="bakery_dining" className="text-[16px]" /></div>
                    <div className="flex-1"><p className="text-caption font-body-strong">베이크하우스</p><p className="text-[11px] text-ink-muted">14:00 · 2명</p></div>
                  </div>
                </div>
              )}
              {sub === "visited" && (
                <div className="grid grid-cols-2 sm:grid-cols-3 gap-md">
                  <div className="bg-white border border-hairline rounded-xl p-md flex items-center gap-sm">
                    <div className="w-10 h-10 rounded-lg flex items-center justify-center text-white" style={{ background: "#5f2c82" }}><Icon name="delivery_dining" className="text-[16px]" /></div>
                    <div className="flex-1"><p className="text-caption font-body-strong">푸드 딜리버리 테크</p></div>
                  </div>
                </div>
              )}
            </div>
          )}

          {/* NOTIF */}
          {tab === "notif" && (
            <div className="space-y-lg">
              <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
                {[
                  { key: "soon", label: "예약 임박 알림" },
                  { key: "empty", label: "빈자리 알림" },
                ].map((row) => {
                  const on = switches[row.key];
                  return (
                    <div key={row.key} className="flex items-center justify-between p-lg">
                      <span className="text-body">{row.label}</span>
                      <button
                        onClick={() => setSwitches((s) => ({ ...s, [row.key]: !s[row.key] }))}
                        className={`w-10 h-6 rounded-full relative ${on ? "bg-status-available" : "bg-hairline"}`}
                      >
                        <span className="absolute top-0.5 w-5 h-5 bg-white rounded-full transition-all" style={{ left: on ? "18px" : "2px" }} />
                      </button>
                    </div>
                  );
                })}
              </div>
              <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
                <div className="p-lg flex gap-sm"><span className="w-2 h-2 rounded-full bg-primary mt-1.5 flex-shrink-0" /><div><p className="text-body">예약하신 A03 부스 시간이 30분 후 시작돼요.</p><p className="text-[11px] text-ink-muted mt-1">3분 전</p></div></div>
                <div className="p-lg flex gap-sm"><span className="w-2 h-2 rounded-full bg-primary mt-1.5 flex-shrink-0" /><div><p className="text-body">관심 등록한 A05 부스에 빈자리가 생겼어요.</p><p className="text-[11px] text-ink-muted mt-1">1시간 전</p></div></div>
                <div className="p-lg flex gap-sm"><span className="w-2 h-2 rounded-full bg-transparent mt-1.5 flex-shrink-0" /><div><p className="text-body">K-뷰티 & 코스메틱 전시회 티켓 결제가 완료됐어요.</p><p className="text-[11px] text-ink-muted mt-1">어제</p></div></div>
              </div>
            </div>
          )}

          {/* PROFILE */}
          {tab === "profile" && (
            <div className="space-y-lg">
              <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
                <div className="flex items-center justify-between p-lg"><div><p className="text-[11px] text-ink-muted">닉네임</p><p className="text-body">김서연</p></div><Icon name="chevron_right" className="text-ink-muted" /></div>
                <div className="p-lg"><p className="text-[11px] text-ink-muted">연동 계정</p><p className="text-body">카카오 (kakao_9284@kakao.com)</p></div>
                <div className="flex items-center justify-between p-lg"><div><p className="text-[11px] text-ink-muted">연락처</p><p className="text-body">010-****-1234</p></div><Icon name="chevron_right" className="text-ink-muted" /></div>
              </div>
              <div className="bg-white rounded-2xl border border-hairline p-lg">
                <h3 className="font-body-strong text-body-strong mb-md">행사 참여내역 · 후기</h3>
                <div className="flex justify-between items-center mb-sm">
                  <p className="font-body-strong text-caption">2026 서울 푸드테크 박람회</p>
                  <span className="text-caption text-ink-muted">08.14 방문</span>
                </div>
                <div className="flex gap-1 mb-sm">
                  {[1, 2, 3, 4, 5].map((i) => (
                    <button key={i} onClick={() => setRating(i)} className={`material-symbols-outlined text-[20px] ${i <= rating ? "icon-fill text-amber-500" : "text-hairline"}`}>
                      star
                    </button>
                  ))}
                </div>
                <div className="flex gap-sm">
                  <input type="text" placeholder="한 줄 후기를 남겨보세요" className="flex-1 h-[40px] rounded-lg border border-hairline px-sm text-caption outline-none focus:border-primary-focus" />
                  <button className="w-10 h-10 rounded-lg bg-primary text-white flex items-center justify-center"><Icon name="check" className="text-[18px]" /></button>
                </div>
              </div>
              <button className="w-full h-[46px] border border-hairline rounded-full font-body-strong flex items-center justify-center gap-1"><Icon name="logout" className="text-[18px]" />로그아웃</button>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
