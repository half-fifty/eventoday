import { useEffect, useState } from "react";
import { Link } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import { eventApi } from "../api/eventApi.js";
import { advertisementApi } from "../api/advertisementApi.js";

const heroSlides = [
  {
    tag: "메인 배너",
    title: "2026 서울 푸드테크 박람회",
    desc: "미래의 식탁을 미리 만나다",
    cta: "자세히 보기",
    to: "/event-ongoing",
    bg: "linear-gradient(135deg,#ff9966,#ff5e62)",
  },
  {
    tag: "부스 모집 중",
    title: "스마트팩토리 자동화 전시회",
    desc: "산업의 다음 단계를 지금 확인하세요",
    cta: "부스 모집 안내",
    to: "/recruitments",
    bg: "linear-gradient(135deg,#2b5876,#4e4376)",
  },
  {
    tag: "메인 배너",
    title: "반려동물 라이프스타일 박람회",
    desc: "우리 가족을 위한 모든 것",
    cta: "자세히 보기",
    to: "#",
    bg: "linear-gradient(135deg,#f7971e,#ffd200)",
  },
];

const upcoming = [
  {
    to: "/event-ongoing",
    bg: "linear-gradient(135deg,#ff9966,#ff5e62)",
    badge: { text: "진행중", cls: "bg-status-assigned text-white" },
    icon: "restaurant",
    category: "푸드테크",
    title: "2026 서울 푸드테크 박람회",
    place: "코엑스 · 08.12–08.14",
    price: "15,000원",
    priceCls: "",
  },
  {
    to: "/recruitments",
    bg: "linear-gradient(135deg,#2b5876,#4e4376)",
    badge: { text: "부스 모집중", cls: "bg-primary-container text-white" },
    icon: "precision_manufacturing",
    category: "산업기술",
    title: "스마트팩토리 자동화 전시회",
    place: "대구 엑스코 · 08.20–08.22",
    price: "20,000원",
    priceCls: "",
  },
  {
    to: "#",
    bg: "linear-gradient(135deg,#11998e,#38ef7d)",
    badge: { text: "예정", cls: "bg-surface-container-highest text-secondary" },
    icon: "eco",
    category: "세미나",
    title: "친환경 에너지 컨퍼런스",
    place: "송도 컨벤시아 · 09.02",
    price: "무료",
    priceCls: "text-primary",
  },
];

const recruiting = [
  { to: "/recruitments", badge: "마감 D-3", badgeCls: "bg-error/10 text-error", title: "스마트팩토리 자동화 전시회", desc: "산업설비·로봇 분야 모집" },
  { to: "#", badge: "모집 D-7", badgeCls: "bg-primary-container/10 text-primary-focus", title: "2026 서울 푸드테크 박람회", desc: "식품·조리기기 분야 모집" },
  { to: "#", badge: "모집 D-14", badgeCls: "bg-primary-container/10 text-primary-focus", title: "K-뷰티 & 코스메틱 전시회", desc: "뷰티·헬스케어 분야 모집" },
];

const popular = [
  { rank: 1, bg: "linear-gradient(135deg,#ff9966,#ff5e62)", name: "서울 푸드테크 박람회", rating: "4.8" },
  { rank: 2, bg: "linear-gradient(135deg,#f7971e,#ffd200)", name: "반려동물 라이프스타일", rating: "4.7" },
  { rank: 3, bg: "linear-gradient(135deg,#2b5876,#4e4376)", name: "스마트팩토리 전시회", rating: "4.6" },
  { rank: 4, bg: "linear-gradient(135deg,#5f2c82,#49a09d)", name: "글로벌 스타트업 서밋", rating: "4.5" },
];

const notices = [
  { tag: "공지", title: "하절기 행사장 이용 안내", date: "2026.07.20" },
  { tag: "점검", title: "8/1(토) 새벽 시스템 점검 안내", date: "2026.07.25" },
  { tag: "안내", title: "교환 코드 발급 정책 변경 안내", date: "2026.07.15" },
];

export default function Home() {
  const [heroIdx, setHeroIdx] = useState(0);
  const [events, setEvents] = useState([]);
  const [activeAds, setActiveAds] = useState([]);
  const [keyword, setKeyword] = useState("");
  const [eventType, setEventType] = useState("");
  const [loadingEvents, setLoadingEvents] = useState(true);
  const [eventError, setEventError] = useState("");
  const displayedSlides = activeAds.length > 0 ? activeAds.map((ad) => ({
    tag: ad.eventId ? "행사 광고" : "부스 광고",
    title: ad.adText || "진행 중인 행사 광고",
    desc: ad.eventId ? `행사 #${ad.eventId}` : `부스 #${ad.boothId}`,
    cta: "자세히 보기",
    to: ad.eventId ? `/event-ongoing?eventId=${ad.eventId}` : "/booth-detail",
    bg: "linear-gradient(135deg,#2b5876,#4e4376)",
  })) : heroSlides;
  const slideCount = displayedSlides.length;
  const displayedEvents = events.map((event) => ({
    to: `/events/${event.id}`,
    bg: "linear-gradient(135deg,#11998e,#38ef7d)",
    badge: { text: new Date(event.startAt) <= new Date() ? "진행중" : "예정", cls: "bg-status-assigned text-white" },
    icon: "event",
    category: event.eventType,
    title: event.name,
    place: `${event.venueName} · ${new Date(event.startAt).toLocaleDateString("ko-KR")}`,
    price: Number(event.ticketPrice) === 0 ? "무료" : `${Number(event.ticketPrice).toLocaleString("ko-KR")}원`,
    priceCls: Number(event.ticketPrice) === 0 ? "text-primary" : "",
  }));

  const loadEvents = async (filters = {}) => {
    setLoadingEvents(true);
    setEventError("");
    try {
      const [eventResult, adResult] = await Promise.allSettled([
        eventApi.list({ size: 6, sort: "startAt,asc", ...filters }),
        advertisementApi.active(),
      ]);
      if (eventResult.status === "rejected") throw eventResult.reason;
      setEvents(eventResult.value?.data?.content || []);
      setActiveAds(adResult.status === "fulfilled" ? adResult.value?.data || [] : []);
    } catch (error) {
      setEventError(error.message || "행사 정보를 불러오지 못했습니다.");
    } finally {
      setLoadingEvents(false);
    }
  };

  const moveHero = (dir) => setHeroIdx((i) => (i + dir + slideCount) % slideCount);

  useEffect(() => {
    const id = setInterval(() => setHeroIdx((i) => (i + 1) % slideCount), 5000);
    return () => clearInterval(id);
  }, [slideCount]);

  useEffect(() => {
    setHeroIdx((index) => index % slideCount);
  }, [slideCount]);

  useEffect(() => { loadEvents(); }, []);

  const searchEvents = () => loadEvents({
    ...(keyword.trim() ? { keyword: keyword.trim() } : {}),
    ...(eventType ? { eventType } : {}),
  });

  return (
    <div className="bg-surface text-on-surface">
      <TopNav active="events" />

      <main className="pt-[44px]">
        <div className="bg-on-primary-fixed text-white text-[12px] text-center py-xs px-lg font-nav-link">
          플랫폼 정식 오픈 기념, 첫 티켓 구매 시 3,000원 할인 · <a href="#" className="underline">자세히</a>
        </div>

        {/* Hero banner slider */}
        <section className="relative h-[360px] md:h-[440px] overflow-hidden bg-surface-tile-dark">
          <div
            className="flex h-full transition-transform duration-500 ease-out"
            style={{ transform: `translateX(-${heroIdx * 100}%)` }}
          >
            {displayedSlides.map((s, i) => (
              <div
                key={i}
                className="min-w-full h-full flex items-center justify-center text-center px-lg"
                style={{ background: s.bg }}
              >
                <div className="max-w-lg">
                  <p className="text-primary-on-dark text-[12px] font-bold tracking-widest uppercase mb-sm">{s.tag}</p>
                  <h1 className="font-hero-display text-display-lg-mobile md:text-display-lg text-white mb-sm">{s.title}</h1>
                  <p className="text-white/85 text-body mb-lg">{s.desc}</p>
                  {s.to === "#" ? (
                    <a href="#" className="inline-block bg-primary-container text-white px-xl py-sm rounded-full font-body-strong active:scale-95 transition-transform">
                      {s.cta}
                    </a>
                  ) : (
                    <Link to={s.to} className="inline-block bg-primary-container text-white px-xl py-sm rounded-full font-body-strong active:scale-95 transition-transform">
                      {s.cta}
                    </Link>
                  )}
                </div>
              </div>
            ))}
          </div>
          <button onClick={() => moveHero(-1)} className="absolute left-md top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-white/20 hover:bg-white/30 text-white flex items-center justify-center backdrop-blur-md">
            <Icon name="chevron_left" />
          </button>
          <button onClick={() => moveHero(1)} className="absolute right-md top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-white/20 hover:bg-white/30 text-white flex items-center justify-center backdrop-blur-md">
            <Icon name="chevron_right" />
          </button>
          <div className="absolute bottom-lg left-0 right-0 flex justify-center gap-xs">
            {displayedSlides.map((_, i) => (
              <div key={i} className={i === heroIdx ? "w-5 h-2 rounded-full bg-white transition-all" : "w-2 h-2 rounded-full bg-white/40 transition-all"} />
            ))}
          </div>
        </section>

        {/* Search */}
        <section className="max-w-[1200px] mx-auto px-lg py-xl">
          <h2 className="font-display-md text-[22px] text-on-surface mb-md">행사 맞춤 검색</h2>
          <div className="bg-white border border-hairline rounded-2xl p-lg shadow-sm">
            <div className="flex items-center gap-sm bg-surface-pearl border border-hairline rounded-full px-lg h-[48px] mb-md">
              <Icon name="search" className="text-ink-muted text-[20px]" />
              <input value={keyword} onChange={(event) => setKeyword(event.target.value)} onKeyDown={(event) => event.key === "Enter" && searchEvents()} type="text" placeholder="행사명으로 검색" className="flex-1 bg-transparent outline-none text-body" />
            </div>
            <div className="flex flex-wrap gap-sm">
              <select className="h-[40px] rounded-full border border-hairline px-md text-caption bg-white outline-none focus:border-primary-focus">
                <option>지역 전체</option><option>서울</option><option>경기</option><option>인천</option><option>대구</option><option>부산</option>
              </select>
              <select value={eventType} onChange={(event) => setEventType(event.target.value)} className="h-[40px] rounded-full border border-hairline px-md text-caption bg-white outline-none focus:border-primary-focus">
                <option value="">행사 유형</option><option value="EXPO">박람회</option><option value="EXHIBITION">전시회</option><option value="SEMINAR">세미나</option><option value="CONFERENCE">컨퍼런스</option>
              </select>
              <select className="h-[40px] rounded-full border border-hairline px-md text-caption bg-white outline-none focus:border-primary-focus">
                <option>무료·유료</option><option>무료</option><option>유료</option>
              </select>
              <button className="h-[40px] px-md rounded-full border border-hairline text-caption font-body-strong hover:bg-surface-container transition-colors flex items-center gap-1">
                <Icon name="storefront" className="text-[16px]" /> 부스 모집 중만
              </button>
              <button onClick={searchEvents} className="h-[40px] px-xl rounded-full bg-primary-container text-white text-caption font-body-strong active:scale-95 transition-transform ml-auto">검색</button>
            </div>
          </div>
        </section>

        {/* Upcoming events */}
        <section className="max-w-[1200px] mx-auto px-lg py-xl">
          <div className="flex items-end justify-between mb-md">
            <h2 className="font-display-md text-[22px] text-on-surface">다가오는 행사</h2>
            <a href="#" className="text-caption text-primary font-body-strong">전체 보기</a>
          </div>
          <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-lg">
            {loadingEvents && <p className="col-span-full text-caption text-ink-muted">행사를 불러오는 중입니다.</p>}
            {eventError && <p className="col-span-full text-caption text-error">{eventError}</p>}
            {!loadingEvents && !eventError && displayedEvents.length === 0 && <p className="col-span-full text-caption text-ink-muted">조건에 맞는 공개 행사가 없습니다.</p>}
            {(displayedEvents.length > 0 ? displayedEvents : (!loadingEvents && eventError ? upcoming : [])).map((e, i) => {
              const inner = (
                <>
                  <div className="h-[150px] relative flex items-center justify-center text-white" style={{ background: e.bg }}>
                    <span className={`absolute top-sm left-sm text-[11px] font-bold px-sm py-1 rounded-full ${e.badge.cls}`}>{e.badge.text}</span>
                    <Icon name={e.icon} className="text-[36px] opacity-90" />
                  </div>
                  <div className="p-lg">
                    <p className="text-[12px] text-primary font-bold mb-1">{e.category}</p>
                    <h3 className="font-body-strong text-body-strong mb-1">{e.title}</h3>
                    <p className="text-caption text-ink-muted">{e.place}</p>
                    <div className="flex items-center justify-between mt-sm">
                      <span className={`text-body-strong ${e.priceCls}`}>{e.price}</span>
                      <span className="text-caption text-primary font-bold">자세히 →</span>
                    </div>
                  </div>
                </>
              );
              const cls = "group bg-white border border-hairline rounded-2xl overflow-hidden hover:shadow-lg transition-all-custom";
              return e.to === "#" ? (
                <a key={i} href="#" className={cls}>{inner}</a>
              ) : (
                <Link key={i} to={e.to} className={cls}>{inner}</Link>
              );
            })}
          </div>
        </section>

        {/* Booth recruiting */}
        <section className="bg-surface-container-low py-xl">
          <div className="max-w-[1200px] mx-auto px-lg">
            <div className="flex items-end justify-between mb-md">
              <h2 className="font-display-md text-[22px] text-on-surface">현재 모집 중인 부스 공고</h2>
              <Link to="/recruitments" className="text-caption text-primary font-body-strong">전체 보기</Link>
            </div>
            <div className="flex gap-lg overflow-x-auto hide-scrollbar pb-sm">
              {recruiting.map((r, i) => {
                const inner = (
                  <>
                    <span className={`inline-block text-[11px] font-bold px-sm py-1 rounded-full mb-sm ${r.badgeCls}`}>{r.badge}</span>
                    <h3 className="font-body-strong text-body-strong mb-1">{r.title}</h3>
                    <p className="text-caption text-ink-muted">{r.desc}</p>
                  </>
                );
                const cls = "min-w-[260px] bg-white border border-hairline rounded-2xl p-lg flex-shrink-0 hover:shadow-lg transition-all-custom";
                return r.to === "#" ? (
                  <a key={i} href="#" className={cls}>{inner}</a>
                ) : (
                  <Link key={i} to={r.to} className={cls}>{inner}</Link>
                );
              })}
            </div>
          </div>
        </section>

        {/* Popular */}
        <section className="max-w-[1200px] mx-auto px-lg py-xl">
          <h2 className="font-display-md text-[22px] text-on-surface mb-md">인기 행사</h2>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-lg">
            {popular.map((p) => (
              <div key={p.rank} className="text-center">
                <div className="h-[100px] rounded-2xl mb-sm flex items-center justify-center text-white font-hero-display text-[28px]" style={{ background: p.bg }}>{p.rank}</div>
                <p className="text-caption font-body-strong">{p.name}</p>
                <p className="text-[11px] text-ink-muted flex items-center justify-center gap-1">
                  <Icon name="star" fill className="text-[13px] text-amber-500" />{p.rating}
                </p>
              </div>
            ))}
          </div>
        </section>

        {/* Notice */}
        <section className="max-w-[1200px] mx-auto px-lg py-xl">
          <h2 className="font-display-md text-[22px] text-on-surface mb-md">공지사항</h2>
          <div className="bg-white border border-hairline rounded-2xl divide-y divide-divider-soft">
            {notices.map((n, i) => (
              <div key={i} className="flex items-center justify-between px-lg py-md">
                <span className="text-body">
                  <span className="text-primary font-bold text-[12px] mr-sm">{n.tag}</span>
                  {n.title}
                </span>
                <span className="text-caption text-ink-muted">{n.date}</span>
              </div>
            ))}
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}
