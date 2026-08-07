import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import { eventApi } from "../api/eventApi.js";
import { advertisementApi } from "../api/advertisementApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import { listPublicRecruitments } from "../api/recruitmentApi.js";
import { EXHIBIT_CATEGORIES, EXHIBIT_CATEGORY_LABELS, REGION_OPTIONS } from "../constants/eventOptions.js";

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
  const eventRailRef = useRef(null);
  const [heroIdx, setHeroIdx] = useState(0);
  const [events, setEvents] = useState([]);
  const [activeAds, setActiveAds] = useState(null);
  const [heroVisualReady, setHeroVisualReady] = useState(false);
  const [keyword, setKeyword] = useState("");
  const [eventType, setEventType] = useState("");
  const [regionCode, setRegionCode] = useState("");
  const [categoryCode, setCategoryCode] = useState("");
  const [loadingEvents, setLoadingEvents] = useState(true);
  const [eventError, setEventError] = useState("");
  const [recruitments, setRecruitments] = useState([]);
  const [loadingRecruitments, setLoadingRecruitments] = useState(true);
  const [recruitmentError, setRecruitmentError] = useState("");
  const eventFallbackSlides = events.slice(0, 3).map((event) => ({
    tag: new Date(event.startAt) <= new Date() ? "NOW · 진행 중" : "UPCOMING · 추천 행사",
    title: event.name,
    desc: `${event.venueName} · ${new Date(event.startAt).toLocaleDateString("ko-KR")}`,
    cta: "행사 자세히 보기",
    to: `/events/${event.id}`,
    bg: "linear-gradient(135deg,#111827,#334155)",
    imageUrl: event.representativeFileId ? fileDownloadUrl(event.representativeFileId) : null,
  }));
  const brandFallbackSlides = [{
    tag: "EVENTODAY",
    title: "오늘의 설렘을 발견하세요",
    desc: "새로운 전시와 행사를 준비하고 있습니다.",
    cta: "전시장 둘러보기",
    to: "/venues",
    bg: "linear-gradient(135deg,#101827 0%,#123d68 55%,#0b6d78 100%)",
  }];
  const displayedSlides = activeAds === null ? [] : activeAds.length > 0 ? activeAds.map((ad) => ({
    tag: ad.eventId ? "PROMOTED · 행사 광고" : "PROMOTED · 부스 광고",
    title: ad.adText || "진행 중인 행사 광고",
    desc: ad.eventId
      ? events.find((event) => String(event.id) === String(ad.eventId))?.name || "EvenToday 추천 행사"
      : `부스 #${ad.boothId}`,
    cta: ad.eventId ? "자세히 보기" : "부스 상세 준비 중",
    to: ad.eventId ? `/events/${ad.eventId}` : null,
    bg: "linear-gradient(135deg,#2b5876,#4e4376)",
    imageUrl: ad.bannerFileId ? fileDownloadUrl(ad.bannerFileId) : null,
  })) : eventFallbackSlides.length > 0 ? eventFallbackSlides : brandFallbackSlides;
  const slideCount = displayedSlides.length;
  const displayedEvents = events.map((event) => ({
    to: `/events/${event.id}`,
    bg: "linear-gradient(135deg,#11998e,#38ef7d)",
    badge: { text: new Date(event.startAt) <= new Date() ? "진행중" : "예정", cls: "bg-status-assigned text-white" },
    icon: "event",
    category: event.exhibitCategoryCodes?.map((code) => EXHIBIT_CATEGORY_LABELS[code] || code).join(" · ") || event.eventType,
    title: event.name,
    place: `${event.venueName} · ${new Date(event.startAt).toLocaleDateString("ko-KR")}`,
    price: Number(event.ticketPrice) === 0 ? "무료" : `${Number(event.ticketPrice).toLocaleString("ko-KR")}원`,
    priceCls: Number(event.ticketPrice) === 0 ? "text-primary" : "",
    imageUrl: event.representativeFileId ? fileDownloadUrl(event.representativeFileId) : null,
  }));
  const displayedRecruiting = recruitments.map((r) => {
    const dDay = Math.ceil((new Date(r.recruitmentEndAt) - new Date()) / (1000 * 60 * 60 * 24));
    return {
      to: `/recruitments/${r.id}`,
      badge: dDay <= 3 ? `마감 D-${Math.max(dDay, 0)}` : `모집 D-${dDay}`,
      badgeCls: dDay <= 3 ? "bg-error/10 text-error" : "bg-primary-container/10 text-primary-focus",
      title: r.title,
      desc: r.participantTarget,
    };
  });

  const loadEvents = async (filters = {}) => {
    setLoadingEvents(true);
    setEventError("");
    try {
      const [eventResult, adResult] = await Promise.allSettled([
        eventApi.list({ size: 6, sort: "startAt,asc", ...filters }),
        advertisementApi.active(),
      ]);
      setEvents(eventResult.status === "fulfilled" ? eventResult.value?.data?.content || [] : []);
      setActiveAds(adResult.status === "fulfilled" ? adResult.value?.data || [] : []);
      if (eventResult.status === "rejected") throw eventResult.reason;
    } catch (error) {
      setEventError(error.message || "행사 정보를 불러오지 못했습니다.");
    } finally {
      setLoadingEvents(false);
    }
  };

  const loadRecruitments = async () => {
    setLoadingRecruitments(true);
    setRecruitmentError("");
    try {
      const data = await listPublicRecruitments("OPEN");
      setRecruitments(Array.isArray(data) ? data.slice(0, 6) : []);
    } catch (error) {
      setRecruitmentError(error.message || "모집 공고를 불러오지 못했습니다.");
    } finally {
      setLoadingRecruitments(false);
    }
  };

  const moveHero = (dir) => setHeroIdx((i) => (i + dir + slideCount) % slideCount);
  const moveEventRail = (direction) => {
    eventRailRef.current?.scrollBy({
      left: direction * Math.min(eventRailRef.current.clientWidth * 0.82, 920),
      behavior: "smooth",
    });
  };

  useEffect(() => {
    if (slideCount < 2) return undefined;
    const id = setInterval(() => setHeroIdx((i) => (i + 1) % slideCount), 5000);
    return () => clearInterval(id);
  }, [slideCount]);

  useEffect(() => {
    setHeroIdx((index) => slideCount > 0 ? index % slideCount : 0);
  }, [slideCount]);

  useEffect(() => {
    if (activeAds === null || displayedSlides.length === 0) {
      setHeroVisualReady(false);
      return undefined;
    }
    const firstImageUrl = displayedSlides[0]?.imageUrl;
    if (!firstImageUrl) {
      setHeroVisualReady(true);
      return undefined;
    }
    let cancelled = false;
    setHeroVisualReady(false);
    const image = new Image();
    image.onload = image.onerror = () => !cancelled && setHeroVisualReady(true);
    image.src = firstImageUrl;
    return () => { cancelled = true; };
  }, [activeAds, displayedSlides[0]?.imageUrl]);

  useEffect(() => { loadEvents(); loadRecruitments(); }, []);

  const searchEvents = () => loadEvents({
    ...(keyword.trim() ? { keyword: keyword.trim() } : {}),
    ...(eventType ? { eventType } : {}),
    ...(regionCode ? { regionCode } : {}),
    ...(categoryCode ? { exhibitCategoryCodes: categoryCode } : {}),
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
          {(!heroVisualReady || activeAds === null) && (
            <div className="absolute inset-0 z-30 overflow-hidden bg-gradient-to-br from-slate-950 via-slate-900 to-blue-950">
              <div className="absolute inset-0 animate-pulse bg-[radial-gradient(circle_at_70%_40%,rgba(59,130,246,.22),transparent_36%)]" />
              <div className="relative flex h-full flex-col items-center justify-center gap-md px-lg">
                <div className="h-3 w-28 rounded-full bg-white/10" />
                <div className="h-10 w-[min(72%,520px)] rounded-xl bg-white/10" />
                <div className="h-4 w-[min(48%,340px)] rounded-full bg-white/10" />
                <div className="mt-sm h-10 w-32 rounded-full bg-white/10" />
              </div>
            </div>
          )}
          <div
            className={`flex h-full transition-[transform,opacity] duration-500 ease-out ${heroVisualReady ? "opacity-100" : "opacity-0"}`}
            style={{ transform: `translateX(-${heroIdx * 100}%)` }}
          >
            {displayedSlides.map((s, i) => (
              <div
                key={i}
                className="relative min-w-full h-full flex items-center justify-center text-center px-lg overflow-hidden"
                style={{ background: s.bg }}
              >
                {s.imageUrl && (
                  <img
                    src={s.imageUrl}
                    alt=""
                    className="absolute inset-0 h-full w-full object-cover"
                  />
                )}
                {s.imageUrl && <div className="absolute inset-0 bg-black/45" />}
                <div className="relative z-10 max-w-lg">
                  <p className="text-primary-on-dark text-[12px] font-bold tracking-widest uppercase mb-sm">{s.tag}</p>
                  <h1 className="font-hero-display text-display-lg-mobile md:text-display-lg text-white mb-sm">{s.title}</h1>
                  <p className="text-white/85 text-body mb-lg">{s.desc}</p>
                  {!s.to ? (
                    <span className="inline-block bg-white/15 text-white/70 px-xl py-sm rounded-full font-body-strong cursor-not-allowed">
                      {s.cta}
                    </span>
                  ) : s.to === "#" ? (
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
          {heroVisualReady && slideCount > 1 && <button onClick={() => moveHero(-1)} className="absolute left-md top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-white/20 hover:bg-white/30 text-white flex items-center justify-center backdrop-blur-md">
            <Icon name="chevron_left" />
          </button>}
          {heroVisualReady && slideCount > 1 && <button onClick={() => moveHero(1)} className="absolute right-md top-1/2 -translate-y-1/2 w-10 h-10 rounded-full bg-white/20 hover:bg-white/30 text-white flex items-center justify-center backdrop-blur-md">
            <Icon name="chevron_right" />
          </button>}
          {heroVisualReady && slideCount > 1 && <div className="absolute bottom-lg left-0 right-0 flex justify-center gap-xs">
            {displayedSlides.map((_, i) => (
              <div key={i} className={i === heroIdx ? "w-5 h-2 rounded-full bg-white transition-all" : "w-2 h-2 rounded-full bg-white/40 transition-all"} />
            ))}
          </div>}
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
              <select value={regionCode} onChange={(event) => setRegionCode(event.target.value)} className="h-[40px] rounded-full border border-hairline px-md text-caption bg-white outline-none focus:border-primary-focus">
                <option value="">지역 전체</option>{REGION_OPTIONS.map(([code,label]) => <option key={code} value={code}>{label}</option>)}
              </select>
              <select value={categoryCode} onChange={(event) => setCategoryCode(event.target.value)} className="h-[40px] rounded-full border border-hairline px-md text-caption bg-white outline-none focus:border-primary-focus"><option value="">전시품목 전체</option>{EXHIBIT_CATEGORIES.map(([code,label]) => <option key={code} value={code}>{label}</option>)}</select>
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
        <section className="overflow-hidden border-y border-hairline bg-white py-xl md:py-xxl">
          <div className="max-w-[1280px] mx-auto px-lg">
          <div className="mb-lg flex items-end justify-between gap-md">
            <div>
              <p className="mb-xs text-[11px] font-bold tracking-[0.18em] text-primary">UPCOMING EVENTS</p>
              <h2 className="font-display-lg text-[28px] text-on-surface md:text-[34px]">다가오는 행사</h2>
              <p className="mt-xs text-caption text-ink-muted">지금 주목할 만한 전시와 행사를 포스터로 만나보세요.</p>
            </div>
            <div className="flex items-center gap-xs">
              <button type="button" onClick={() => moveEventRail(-1)} aria-label="이전 행사" className="hidden h-11 w-11 items-center justify-center rounded-full border border-hairline bg-white text-on-surface transition hover:border-on-surface hover:bg-surface-container md:flex"><Icon name="chevron_left" /></button>
              <button type="button" onClick={() => moveEventRail(1)} aria-label="다음 행사" className="hidden h-11 w-11 items-center justify-center rounded-full border border-hairline bg-white text-on-surface transition hover:border-on-surface hover:bg-surface-container md:flex"><Icon name="chevron_right" /></button>
              <Link to="/events" className="ml-sm whitespace-nowrap text-caption text-primary font-body-strong">전체 보기 →</Link>
            </div>
          </div>
          <div ref={eventRailRef} className="hide-scrollbar -mx-lg flex snap-x snap-mandatory gap-md overflow-x-auto px-lg pb-sm md:gap-lg">
            {loadingEvents && <p className="col-span-full text-caption text-ink-muted">행사를 불러오는 중입니다.</p>}
            {eventError && <p className="col-span-full text-caption text-error">{eventError}</p>}
            {!loadingEvents && !eventError && displayedEvents.length === 0 && <p className="col-span-full text-caption text-ink-muted">조건에 맞는 공개 행사가 없습니다.</p>}
            {(displayedEvents.length > 0 ? displayedEvents : (!loadingEvents && eventError ? upcoming : [])).map((e, i) => {
              const inner = (
                <>
                  <div className="relative aspect-[3/4] overflow-hidden rounded-[3px] bg-surface-container flex items-center justify-center text-white" style={{ background: e.bg }}>
                    <Icon name={e.icon} className="text-[48px] opacity-60" />
                    {e.imageUrl && <img src={e.imageUrl} alt={`${e.title} 포스터`} onError={(event) => { event.currentTarget.style.display = "none"; }} className="absolute inset-0 w-full h-full object-cover transition duration-500 group-hover:scale-[1.025]" />}
                    <div className="pointer-events-none absolute inset-0 bg-gradient-to-t from-black/35 via-transparent to-black/5 opacity-70" />
                    <span className={`absolute left-sm top-sm rounded-full px-sm py-1 text-[10px] font-bold shadow-sm ${e.badge.cls}`}>{e.badge.text}</span>
                  </div>
                  <div className="flex min-h-[150px] flex-col pt-md">
                    <p className="mb-1 truncate text-[11px] font-bold text-primary">{e.category}</p>
                    <h3 className="line-clamp-2 min-h-[44px] font-body-strong text-body-strong leading-snug">{e.title}</h3>
                    <p className="mt-xs truncate text-caption text-ink-muted">{e.place}</p>
                    <div className="mt-auto flex items-center justify-between border-t border-divider-soft pt-sm">
                      <span className={`text-body-strong font-bold ${e.priceCls}`}>{e.price}</span>
                      <span className="rounded-full border border-hairline px-sm py-1 text-[11px] font-bold text-on-surface transition group-hover:border-primary group-hover:bg-primary group-hover:text-white">자세히</span>
                    </div>
                  </div>
                </>
              );
              const cls = "group block w-[210px] flex-none snap-start md:w-[232px] transition-all-custom hover:-translate-y-1";
              return e.to === "#" ? (
                <a key={i} href="#" className={cls}>{inner}</a>
              ) : (
                <Link key={i} to={e.to} className={cls}>{inner}</Link>
              );
            })}
          </div>
          <p className="mt-sm text-[11px] text-ink-muted md:hidden">옆으로 밀어 더 많은 행사를 확인하세요.</p>
          </div>
        </section>

        {/* Booth recruiting */}
        <section className="bg-surface-container-low py-xl">
          <div className="max-w-[1200px] mx-auto px-lg">
            <div className="flex items-end justify-between mb-md">
              <h2 className="font-display-md text-[22px] text-on-surface">현재 모집 중인 부스 공고</h2>
              <Link to="/recruitments" className="text-caption text-primary font-body-strong">전체 보기</Link>
            </div>
            {loadingRecruitments && <p className="text-caption text-ink-muted">모집 공고를 불러오는 중입니다.</p>}
            {recruitmentError && <p className="text-caption text-error">{recruitmentError}</p>}
            {!loadingRecruitments && !recruitmentError && displayedRecruiting.length === 0 && (
              <p className="text-caption text-ink-muted">현재 모집 중인 부스 공고가 없습니다.</p>
            )}
            <div className="flex gap-lg overflow-x-auto hide-scrollbar pb-sm">
              {displayedRecruiting.map((r, i) => (
                <Link
                  key={i}
                  to={r.to}
                  className="min-w-[260px] bg-white border border-hairline rounded-2xl p-lg flex-shrink-0 hover:shadow-lg transition-all-custom"
                >
                  <span className={`inline-block text-[11px] font-bold px-sm py-1 rounded-full mb-sm ${r.badgeCls}`}>{r.badge}</span>
                  <h3 className="font-body-strong text-body-strong mb-1">{r.title}</h3>
                  <p className="text-caption text-ink-muted">{r.desc}</p>
                </Link>
              ))}
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
