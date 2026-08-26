import { useEffect, useRef, useState } from "react";
import { Link } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import { eventApi } from "../api/eventApi.js";
import { advertisementApi } from "../api/advertisementApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import { listPublicRecruitments } from "../api/recruitmentApi.js";
import { platformNoticeApi } from "../api/platformNoticeApi.js";
import { EXHIBIT_CATEGORIES, EXHIBIT_CATEGORY_LABELS, REGION_OPTIONS } from "../constants/eventOptions.js";

const eventPhase = (event) => {
  const now = Date.now();
  if (now < new Date(event.startAt).getTime()) return "upcoming";
  if (now <= new Date(event.endAt).getTime()) return "ongoing";
  return "ended";
};

const eventCountdown = (event) => {
  const startAt = new Date(event.startAt);
  if (Number.isNaN(startAt.getTime())) return "예정";

  const now = new Date();
  const today = new Date(now.getFullYear(), now.getMonth(), now.getDate());
  const startDay = new Date(startAt.getFullYear(), startAt.getMonth(), startAt.getDate());
  const remainingDays = Math.ceil((startDay.getTime() - today.getTime()) / 86_400_000);

  return remainingDays <= 0 ? "D-DAY" : `D-${remainingDays}`;
};

export default function Home() {
  const eventRailRef = useRef(null);
  const eventRequestSequence = useRef(0);
  const [heroIdx, setHeroIdx] = useState(0);
  const [events, setEvents] = useState([]);
  const [recommendedEvents, setRecommendedEvents] = useState([]);
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
  const [notices, setNotices] = useState([]);
  const [noticeError, setNoticeError] = useState("");
  const eventFallbackSlides = events.slice(0, 3).map((event) => ({
    tag: eventPhase(event) === "ongoing" ? "NOW · 진행 중" : eventPhase(event) === "ended" ? "ENDED · 종료" : "UPCOMING · 추천 행사",
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
      : "추천 부스",
    cta: ad.eventId ? "자세히 보기" : "부스 상세 준비 중",
    to: ad.eventId ? `/events/${ad.eventId}` : null,
    bg: "linear-gradient(135deg,#2b5876,#4e4376)",
    imageUrl: ad.bannerFileId ? fileDownloadUrl(ad.bannerFileId) : null,
  })) : eventFallbackSlides.length > 0 ? eventFallbackSlides : brandFallbackSlides;
  const slideCount = displayedSlides.length;
  const displayedEvents = events.map((event) => ({
    to: `/events/${event.id}`,
    bg: "linear-gradient(135deg,#11998e,#38ef7d)",
    badge: eventPhase(event) === "ongoing"
      ? { text: "진행중", cls: "bg-status-assigned text-white" }
      : eventPhase(event) === "ended"
        ? { text: "종료", cls: "bg-surface-container-highest text-ink-muted" }
        : { text: eventCountdown(event), cls: "bg-primary-container text-white" },
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
    const sequence = ++eventRequestSequence.current;
    setLoadingEvents(true);
    setEventError("");
    try {
      const [eventResult, adResult] = await Promise.allSettled([
        eventApi.list({ size: 6, sort: "startAt,asc", ...filters }),
        advertisementApi.active(),
      ]);
      if (sequence !== eventRequestSequence.current) return;
      setEvents(eventResult.status === "fulfilled" ? eventResult.value?.data?.content || [] : []);
      setActiveAds(adResult.status === "fulfilled" ? adResult.value?.data || [] : []);
      if (eventResult.status === "rejected") throw eventResult.reason;
    } catch (error) {
      if (sequence !== eventRequestSequence.current) return;
      setEventError(error.message || "행사 정보를 불러오지 못했습니다.");
    } finally {
      if (sequence === eventRequestSequence.current) setLoadingEvents(false);
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

  const loadRecommendedEvents = async () => {
    try {
      const result = await eventApi.list({ size: 4, sort: "startAt,asc" });
      setRecommendedEvents(result?.data?.content || []);
    } catch {
      setRecommendedEvents([]);
    }
  };

  const loadNotices = async () => {
    try {
      const result = await platformNoticeApi.list({ page: 0, size: 3 });
      setNotices(result?.data?.content || []);
      setNoticeError("");
    } catch (error) {
      setNotices([]);
      setNoticeError(error.message || "공지사항을 불러오지 못했습니다.");
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

  useEffect(() => { loadEvents(); loadRecommendedEvents(); loadRecruitments(); loadNotices(); }, []);

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
          공개된 전시·행사와 참가 부스 모집 정보를 한곳에서 확인하세요.
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
                    onError={(imageEvent) => { imageEvent.currentTarget.style.display = "none"; }}
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
            {displayedEvents.map((e, i) => {
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

        {/* Recommended public events (actual API data, ordered by start date) */}
        <section className="max-w-[1200px] mx-auto px-lg py-xl">
          <div className="mb-md flex items-end justify-between gap-md">
            <div><h2 className="font-display-md text-[22px] text-on-surface">추천 행사</h2><p className="mt-xs text-caption text-ink-muted">공개 행사 중 개최일이 가까운 순서로 보여드립니다.</p></div>
            <Link to="/events" className="text-caption font-body-strong text-primary">전체 보기</Link>
          </div>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-lg">
            {recommendedEvents.map((event, index) => (
              <Link key={event.id} to={`/events/${event.id}`} className="group text-center">
                <div className="relative h-[150px] overflow-hidden rounded-2xl mb-sm bg-gradient-to-br from-[#24496b] to-[#51477d]">
                  {event.representativeFileId ? <img src={fileDownloadUrl(event.representativeFileId)} alt="" className="h-full w-full object-cover transition group-hover:scale-105" /> : <span className="grid h-full place-items-center text-white font-hero-display text-[28px]">{index + 1}</span>}
                  <span className="absolute left-sm top-sm rounded-full bg-black/65 px-sm py-1 text-[11px] font-bold text-white">{eventPhase(event) === "ongoing" ? "진행 중" : eventPhase(event) === "upcoming" ? eventCountdown(event) : "종료"}</span>
                </div>
                <p className="truncate text-caption font-body-strong">{event.name}</p>
                <p className="mt-1 text-[11px] text-ink-muted">{event.venueName || "장소 미정"}</p>
              </Link>
            ))}
          </div>
          {!recommendedEvents.length && !loadingEvents && <p className="text-caption text-ink-muted">표시할 공개 행사가 없습니다.</p>}
        </section>

        {/* Notice */}
        <section className="max-w-[1200px] mx-auto px-lg py-xl">
          <div className="mb-md flex items-center justify-between"><h2 className="font-display-md text-[22px] text-on-surface">공지사항</h2><Link to="/notices" className="text-caption font-body-strong text-primary">전체 보기</Link></div>
          <div className="bg-white border border-hairline rounded-2xl divide-y divide-divider-soft">
            {notices.map((n, i) => {
              const noticeId = n.noticeId ?? n.id;
              return noticeId ? (
              <Link key={noticeId} to={`/notices/${noticeId}`} className="flex items-center justify-between gap-md px-lg py-md hover:bg-surface-pearl">
                <span className="text-body">
                  <span className="text-primary font-bold text-[12px] mr-sm">공지</span>
                  {n.title}
                </span>
                <span className="shrink-0 text-caption text-ink-muted">{n.publishedAt || n.createdAt ? new Date(n.publishedAt || n.createdAt).toLocaleDateString("ko-KR") : ""}</span>
              </Link>) : (
                <div key={`notice-${i}`} className="flex items-center justify-between gap-md px-lg py-md">
                  <span className="text-body"><span className="text-primary font-bold text-[12px] mr-sm">공지</span>{n.title}</span>
                  <span className="text-caption text-error">상세 링크 준비 중</span>
                </div>
              );
            })}
            {!notices.length && <p className="px-lg py-xl text-center text-caption text-ink-muted">{noticeError || "등록된 공지사항이 없습니다."}</p>}
          </div>
        </section>
      </main>

      <Footer />
    </div>
  );
}
