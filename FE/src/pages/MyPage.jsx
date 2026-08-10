import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import TopNav from "../components/TopNav.jsx";
import useAuth from "../hooks/useAuth.js";
import { useNotifications } from "../notifications/NotificationContext.jsx";

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
  const { member, logout } = useAuth();
  const { openPanel, unreadCount } = useNotifications();
  const isBusinessMember = member.accountType === "BUSINESS";
  const organizationType = member.organization?.organizationType;
  const isOrganizer = organizationType === "ORGANIZER";
  const isExhibitor = organizationType === "EXHIBITOR";
  const businessActivityLabel = isOrganizer
    ? "행사 관리"
    : isExhibitor
      ? "부스 신청"
      : "활동 관리";
  const businessActivityIcon = isOrganizer
    ? "event"
    : isExhibitor
      ? "storefront"
      : "business_center";
  const businessTabs = [
    { key: "business-overview", label: "사업자 홈", icon: "dashboard" },
    {
      key: "business-activity",
      label: businessActivityLabel,
      icon: businessActivityIcon,
    },
    { key: "profile", label: "회원정보", icon: "person" },
  ];
  const visibleTabs = isBusinessMember
    ? businessTabs
    : tabs;
  const [tab, setTab] = useState(
    isBusinessMember ? "business-overview" : "tickets"
  );
  const [sub, setSub] = useState("interest");
  const [code, setCode] = useState("");
  const [redeemMsg, setRedeemMsg] = useState(null); // { ok, text }
  const [rating, setRating] = useState(0);
  const [isLoggingOut, setIsLoggingOut] = useState(false);
  const loginDescription = isBusinessMember
    ? `${member.organization?.name || "사업자"} · 사업자 계정`
    : "일반 회원";
  const memberEmail = member.email?.endsWith("@oauth.invalid")
    ? "이메일 정보 없음"
    : member.email;

  const organizationTypeLabel = {
    ORGANIZER: "박람회 개최측",
    EXHIBITOR: "부스 참가측",
  }[organizationType] || "조직 정보 없음";

  const organizationRoleLabel = {
    OWNER: "소유자",
    MANAGER: "관리자",
    STAFF: "실무자",
  }[member.organization?.organizationRole] || "권한 정보 없음";

  useEffect(() => {
    const visibleTabKeys = isBusinessMember
      ? ["business-overview", "business-activity", "profile"]
      : tabs.map((item) => item.key);

    if (!visibleTabKeys.includes(tab)) {
      setTab(visibleTabKeys[0]);
    }
  }, [isBusinessMember, tab]);

  const handleLogout = async () => {
    setIsLoggingOut(true);

    try {
      await logout();
    } catch {
      window.alert("로그아웃에 실패했습니다. 다시 시도해 주세요.");
    } finally {
      setIsLoggingOut(false);
    }
  };

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
      <TopNav active="mypage" />

      <main className="pt-[44px] pb-xxl">
        {/* Profile header */}
        <section className="bg-black text-white px-lg pt-xl pb-lg">
          <div className="max-w-[900px] mx-auto">
            <div className="flex items-center gap-md mb-lg">
              <div className="w-12 h-12 rounded-full bg-surface-tile-dark-alt flex items-center justify-center"><Icon name="person" /></div>
              <div>
                <p className="font-body-strong">{member.nickname} 님</p>
                <p className="text-[12px] text-white/50">{loginDescription}</p>
              </div>
            </div>
            <div className="flex gap-xs overflow-x-auto hide-scrollbar">
              {visibleTabs.map((t) => (
                <button
                  key={t.key}
                  onClick={() => t.key === "notif" ? openPanel() : setTab(t.key)}
                  className={tabBtnCls(tab === t.key)}
                >
                  <Icon name={t.icon} className="text-[16px]" />{t.label}
                  {t.key === "notif" && unreadCount > 0 && (
                    <span className="ml-0.5 rounded-full bg-[#ff3b30] px-1.5 text-[9px] font-bold leading-4 text-white">
                      {unreadCount > 99 ? "99+" : unreadCount}
                    </span>
                  )}
                </button>
              ))}
            </div>
          </div>
        </section>

        <div className="max-w-[900px] mx-auto px-lg py-xl">
          {tab === "business-overview" && (
            <div className="space-y-lg">
              <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
                <div className="p-lg font-body-strong">사업자 정보</div>
                <div className="grid gap-0 sm:grid-cols-3 sm:divide-x sm:divide-divider-soft">
                  <div className="p-lg">
                    <p className="text-[11px] text-ink-muted">회사명</p>
                    <p className="mt-1 font-body-strong">{member.organization?.name || "조직 정보 없음"}</p>
                  </div>
                  <div className="p-lg">
                    <p className="text-[11px] text-ink-muted">사업자 유형</p>
                    <p className="mt-1 font-body-strong">{organizationTypeLabel}</p>
                  </div>
                  <div className="p-lg">
                    <p className="text-[11px] text-ink-muted">조직 권한</p>
                    <p className="mt-1 font-body-strong">{organizationRoleLabel}</p>
                  </div>
                </div>
              </div>

              <div className="bg-white rounded-2xl border border-hairline p-xl text-center">
                <div className="mx-auto mb-sm flex h-12 w-12 items-center justify-center rounded-full bg-surface-container">
                  <Icon name={businessActivityIcon} className="text-[22px] text-ink-muted" />
                </div>
                <p className="font-body-strong">
                  {isOrganizer ? "등록한 행사가 없습니다." : isExhibitor ? "부스 신청 내역이 없습니다." : "조직 정보를 확인할 수 없습니다."}
                </p>
                <p className="mt-xs text-caption text-ink-muted">
                  {isOrganizer ? "행사를 등록하면 여기에서 관리할 수 있어요." : isExhibitor ? "부스 참가를 신청하면 여기에 현황이 표시돼요." : "조직 관리자에게 소속 정보를 확인해 주세요."}
                </p>
              </div>
            </div>
          )}

          {tab === "business-activity" && (
            <div className="bg-white rounded-2xl border border-hairline p-xl text-center">
              <Icon name={businessActivityIcon} className="mb-sm text-[28px] text-ink-muted" />
              <h2 className="font-body-strong">
                {isExhibitor ? "부스 신청 현황" : businessActivityLabel}
              </h2>
              <p className="mt-xs text-caption text-ink-muted">
                {isOrganizer ? "개최자센터에서 등록한 행사를 관리할 수 있습니다." : isExhibitor ? "부스 신청 내역과 검토 상태를 확인할 수 있습니다." : "조직 정보를 확인한 후 이용해 주세요."}
              </p>
              {/* 참가기업: 내 부스 신청 현황 페이지로 연결 (WBS-196) */}
              {isExhibitor && (
                <Link
                  to="/my-applications"
                  className="mt-md inline-flex items-center gap-xs px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong"
                >
                  <Icon name="storefront" className="text-[16px]" /> 내 부스 신청 현황 보기
                </Link>
              )}
              {/* 개최자: 개최자센터로 연결 */}
              {isOrganizer && (
                <Link
                  to="/organizer-admin"
                  className="mt-md inline-flex items-center gap-xs px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong"
                >
                  <Icon name="event" className="text-[16px]" /> 개최자센터로 이동
                </Link>
              )}
            </div>
          )}

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

          {/* PROFILE */}
          {tab === "profile" && (
            <div className="space-y-lg">
              <div className="bg-white rounded-2xl border border-hairline divide-y divide-divider-soft">
                <div className="p-lg"><p className="text-[11px] text-ink-muted">닉네임</p><p className="text-body">{member.nickname}</p></div>
                <div className="p-lg"><p className="text-[11px] text-ink-muted">이메일</p><p className="text-body">{memberEmail}</p></div>
                <div className="p-lg"><p className="text-[11px] text-ink-muted">계정 유형</p><p className="text-body">{isBusinessMember ? "사업자 계정" : "일반 회원"}</p></div>
                {isBusinessMember && member.organization && (
                  <>
                    <div className="p-lg"><p className="text-[11px] text-ink-muted">회사명</p><p className="text-body">{member.organization.name}</p></div>
                    <div className="p-lg"><p className="text-[11px] text-ink-muted">사업자 유형</p><p className="text-body">{organizationTypeLabel}</p></div>
                    <div className="p-lg"><p className="text-[11px] text-ink-muted">조직 권한</p><p className="text-body">{organizationRoleLabel}</p></div>
                  </>
                )}
              </div>
              {!isBusinessMember && <div className="bg-white rounded-2xl border border-hairline p-lg">
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
              </div>}
              <button onClick={handleLogout} disabled={isLoggingOut} className="w-full h-[46px] border border-hairline rounded-full font-body-strong flex items-center justify-center gap-1 disabled:cursor-not-allowed disabled:opacity-50"><Icon name="logout" className="text-[18px]" />{isLoggingOut ? "처리 중" : "로그아웃"}</button>
            </div>
          )}
        </div>
      </main>
    </div>
  );
}
