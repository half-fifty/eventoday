import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import { eventApi } from "../api/eventApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import { EXHIBIT_CATEGORIES, EXHIBIT_CATEGORY_LABELS, REGION_OPTIONS } from "../constants/eventOptions.js";

const typeLabel = {
  EXPO: "박람회",
  EXHIBITION: "전시회",
  SEMINAR: "세미나",
  CONFERENCE: "컨퍼런스",
};

const formatDate = (value) => new Date(value).toLocaleDateString("ko-KR", {
  year: "numeric", month: "short", day: "numeric",
});

export default function EventList() {
  const [searchParams] = useSearchParams();
  const venueName = searchParams.get("venue") || "";
  const requestSequence = useRef(0);
  const [events, setEvents] = useState([]);
  const [keyword, setKeyword] = useState("");
  const [eventType, setEventType] = useState("");
  const [regionCode, setRegionCode] = useState("");
  const [categoryCode, setCategoryCode] = useState("");
  const [page, setPage] = useState(0);
  const [totalPages, setTotalPages] = useState(0);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");

  const load = async (targetPage = 0) => {
    const sequence = ++requestSequence.current;
    setLoading(true);
    setError("");
    try {
      const result = await eventApi.list({
        page: targetPage,
        size: 12,
        sort: "startAt,asc",
        ...(keyword.trim() ? { keyword: keyword.trim() } : {}),
        ...(eventType ? { eventType } : {}),
        ...(regionCode ? { regionCode } : {}),
        ...(categoryCode ? { exhibitCategoryCodes: categoryCode } : {}),
        ...(venueName ? { venueName } : {}),
      });
      if (sequence !== requestSequence.current) return;
      setEvents(result?.data?.content || []);
      setPage(result?.data?.number || 0);
      setTotalPages(result?.data?.totalPages || 0);
    } catch (requestError) {
      if (sequence !== requestSequence.current) return;
      setError(requestError.message || "행사 목록을 불러오지 못했습니다.");
    } finally {
      if (sequence === requestSequence.current) setLoading(false);
    }
  };

  useEffect(() => { load(0); }, [venueName]);

  return <div className="min-h-screen bg-surface text-on-surface">
    <TopNav active="events" />
    <main className="pt-[76px] pb-xxl">
      <section className="max-w-[1200px] mx-auto px-lg py-xl">
        <p className="text-caption text-primary font-bold tracking-wider">ALL EVENTS</p>
        <h1 className="font-display-lg text-[34px] mt-xs">전체 행사</h1>
        <p className="text-ink-muted mt-xs">현재 공개된 박람회와 전시·세미나를 한곳에서 찾아보세요.</p>

        <form onSubmit={(e) => { e.preventDefault(); load(0); }} className="mt-xl bg-white border border-hairline rounded-2xl p-md flex flex-col md:flex-row gap-sm shadow-sm">
          <label className="flex-1 flex items-center gap-sm h-12 px-md rounded-xl bg-surface-container-low border border-hairline focus-within:border-primary">
            <Icon name="search" className="text-ink-muted" />
            <input value={keyword} onChange={(e) => setKeyword(e.target.value)} className="w-full bg-transparent outline-none" placeholder="행사명으로 검색" />
          </label>
          <select value={eventType} onChange={(e) => setEventType(e.target.value)} className="h-12 px-md bg-white border border-hairline rounded-xl outline-none focus:border-primary">
            <option value="">모든 행사 유형</option>
            <option value="EXPO">박람회</option>
            <option value="EXHIBITION">전시회</option>
            <option value="SEMINAR">세미나</option>
            <option value="CONFERENCE">컨퍼런스</option>
          </select>
          <select value={regionCode} onChange={(e) => setRegionCode(e.target.value)} className="h-12 px-md bg-white border border-hairline rounded-xl outline-none focus:border-primary"><option value="">모든 지역</option>{REGION_OPTIONS.map(([code,label]) => <option key={code} value={code}>{label}</option>)}</select>
          <select value={categoryCode} onChange={(e) => setCategoryCode(e.target.value)} className="h-12 px-md bg-white border border-hairline rounded-xl outline-none focus:border-primary"><option value="">모든 전시품목</option>{EXHIBIT_CATEGORIES.map(([code,label]) => <option key={code} value={code}>{label}</option>)}</select>
          <button className="h-12 px-xl rounded-xl bg-primary text-white font-body-strong">검색</button>
        </form>

        {loading && <div className="py-xxl text-center text-ink-muted">공개 행사를 불러오는 중입니다.</div>}
        {error && <div className="mt-xl bg-error/10 border border-error/20 text-error rounded-xl p-lg">{error}</div>}
        {!loading && !error && events.length === 0 && <div className="mt-xl bg-white border border-hairline rounded-2xl py-xxl text-center"><Icon name="event_busy" className="text-[40px] text-ink-muted" /><p className="mt-sm text-ink-muted">조건에 맞는 공개 행사가 없습니다.</p></div>}

        {!loading && !error && events.length > 0 && <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-lg mt-xl">
          {events.map((event) => <Link key={event.id} to={`/events/${event.id}`} className="group overflow-hidden bg-white border border-hairline rounded-2xl hover:-translate-y-1 hover:shadow-lg transition-all">
            <div className="relative h-52 bg-gradient-to-br from-primary-focus to-secondary overflow-hidden">
              {event.representativeFileId ? <img src={fileDownloadUrl(event.representativeFileId)} alt={`${event.name} 포스터`} onError={(imageEvent) => { imageEvent.currentTarget.style.display = "none"; }} className="w-full h-full object-cover group-hover:scale-105 transition-transform duration-300" /> : <div className="h-full grid place-items-center text-white/80"><Icon name="event" className="text-[48px]" /></div>}
              <span className="absolute top-sm left-sm px-sm py-xs rounded-full bg-black/55 text-white text-caption backdrop-blur">{typeLabel[event.eventType] || event.eventType}</span>
            </div>
            <div className="p-lg">
              <h2 className="font-body-strong text-[18px] line-clamp-1">{event.name}</h2>
              {event.exhibitCategoryCodes?.length > 0 && <p className="text-caption text-primary mt-xs line-clamp-1">{event.exhibitCategoryCodes.map((code) => EXHIBIT_CATEGORY_LABELS[code] || code).join(" · ")}</p>}
              <p className="text-caption text-ink-muted mt-sm flex gap-xs"><Icon name="calendar_month" className="text-[16px]" />{formatDate(event.startAt)} ~ {formatDate(event.endAt)}</p>
              <p className="text-caption text-ink-muted mt-xs flex gap-xs"><Icon name="location_on" className="text-[16px]" />{event.venueName}</p>
              <div className="mt-md pt-md border-t border-hairline flex justify-between items-center"><strong className={Number(event.ticketPrice) === 0 ? "text-primary" : ""}>{Number(event.ticketPrice) === 0 ? "무료" : `${Number(event.ticketPrice).toLocaleString("ko-KR")}원`}</strong><span className="text-caption text-primary font-bold">자세히 보기 →</span></div>
            </div>
          </Link>)}
        </div>}

        {totalPages > 1 && <div className="flex justify-center items-center gap-sm mt-xl">
          <button disabled={page === 0} onClick={() => load(page - 1)} className="px-md py-sm border border-hairline rounded-full disabled:opacity-40">이전</button>
          <span className="text-caption text-ink-muted">{page + 1} / {totalPages}</span>
          <button disabled={page + 1 >= totalPages} onClick={() => load(page + 1)} className="px-md py-sm border border-hairline rounded-full disabled:opacity-40">다음</button>
        </div>}
      </section>
    </main>
    <Footer />
  </div>;
}
