import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import NotificationBell from "../components/NotificationBell.jsx";
import VenueMapPins from "../components/VenueMapPins.jsx";
import { ApiError } from "../api/apiClient.js";
import { eventApi } from "../api/eventApi.js";
import { listPublicVenueMaps } from "../api/venueMapApi.js";

const formatEventPeriod = (event) => {
  if (!event) return "";
  const start = new Date(event.startAt);
  const end = new Date(event.endAt);
  const fmt = (d) => `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
  return `${fmt(start)} – ${fmt(end)} · ${event.venueName ?? ""}`;
};

const initialBooths = [
  { id: "A01", name: "맛있는 식탁", zone: "A구역 1층", congestion: 62, interest: false, icon: "lunch_dining" },
  { id: "A02", name: "그린 키친랩", zone: "A구역 1층", congestion: 30, interest: false, icon: "blender" },
  { id: "A03", name: "베이크하우스", zone: "A구역 1층", congestion: 88, interest: false, icon: "bakery_dining" },
  { id: "A04", name: "브루잉 스튜디오", zone: "A구역 2층", congestion: 45, interest: false, icon: "coffee" },
  { id: "A05", name: "스마트키친 로보틱스", zone: "A구역 2층", congestion: 71, interest: true, icon: "smart_toy" },
  { id: "A06", name: "콜드체인 솔루션", zone: "A구역 2층", congestion: 20, interest: false, icon: "ac_unit" },
  { id: "A07", name: "비건 델리", zone: "B구역 1층", congestion: 55, interest: false, icon: "eco" },
  { id: "A08", name: "프레시 로스터리", zone: "B구역 1층", congestion: 40, interest: false, icon: "coffee_maker" },
  { id: "A09", name: "패키징 이노베이션", zone: "B구역 2층", congestion: 66, interest: false, icon: "inventory_2" },
  { id: "A10", name: "푸드 딜리버리 테크", zone: "B구역 2층", congestion: 33, interest: false, icon: "delivery_dining" },
];

const levelLabel = (v) => (v >= 70 ? "혼잡" : v >= 40 ? "보통" : "여유");
const levelColor = (v) => (v >= 70 ? "status-visited" : v >= 40 ? "status-pending" : "status-available");

const tabButtons = [
  { key: "map", label: "행사장 평면도", mobile: "평면도", icon: "map" },
  { key: "booths", label: "참가 부스", mobile: "부스", icon: "storefront" },
  { key: "popular", label: "인기 부스", mobile: "인기", icon: "bar_chart" },
  { key: "interest", label: "관심 부스", mobile: "관심", icon: "favorite" },
];

// deterministic QR mock, same formula as original
const qrPixels = Array.from({ length: 100 }, (_, i) => (i * 37 + 13) % 7 < 3);

// 실제 배치도 핀은 혼잡도/방문자 데이터가 없어 목록 목업과 같은 방식으로 부스 id 기반 가짜 값을 만든다.
const mockCongestion = (boothId) => (Number(boothId) * 37 + 13) % 70 + 20;

export default function EventOngoing() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [events, setEvents] = useState([]);
  const [loadingEvents, setLoadingEvents] = useState(true);
  const [eventsError, setEventsError] = useState("");
  const [selectedEventId, setSelectedEventId] = useState(searchParams.get("eventId") || "");
  const [eventDetail, setEventDetail] = useState(null);
  const [eventDetailError, setEventDetailError] = useState("");

  const [venueMaps, setVenueMaps] = useState([]);
  const [loadingVenueMaps, setLoadingVenueMaps] = useState(false);
  const [venueMapError, setVenueMapError] = useState("");
  const [mapPinBooth, setMapPinBooth] = useState(null);

  const [booths, setBooths] = useState(initialBooths);
  const [tab, setTab] = useState("map");
  const [activeBoothId, setActiveBoothId] = useState(null);
  const [boothSheetOpen, setBoothSheetOpen] = useState(false);
  const [qrSheetOpen, setQrSheetOpen] = useState(false);

  // 진행 중인 실제 행사(PUBLISHED) 목록을 불러와 선택할 수 있게 한다.
  useEffect(() => {
    eventApi.list({ size: 100, sort: "startAt,asc" })
      .then((result) => {
        const list = result?.data?.content || [];
        setEvents(list);
        if (!selectedEventId && list.length > 0) {
          setSelectedEventId(String(list[0].id));
        }
      })
      .catch((error) => setEventsError(error.message || "행사 목록을 불러오지 못했습니다."))
      .finally(() => setLoadingEvents(false));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (selectedEventId) {
      setSearchParams({ eventId: selectedEventId }, { replace: true });
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [selectedEventId]);

  // 뒤로가기·앞으로가기 등 외부 내비게이션으로 URL의 eventId가 바뀌면 선택 상태도 맞춘다.
  useEffect(() => {
    const urlEventId = searchParams.get("eventId") || "";
    if (urlEventId && urlEventId !== selectedEventId) {
      setSelectedEventId(urlEventId);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [searchParams]);

  useEffect(() => {
    if (!selectedEventId) return;
    let cancelled = false;

    setEventDetail(null);
    setEventDetailError("");
    eventApi.detail(selectedEventId)
      .then((result) => {
        if (!cancelled) setEventDetail(result?.data ?? null);
      })
      .catch((error) => {
        if (!cancelled) {
          setEventDetail(null);
          setEventDetailError(error instanceof ApiError ? error.message : "행사 정보를 불러오지 못했습니다.");
        }
      });

    return () => {
      cancelled = true;
    };
  }, [selectedEventId]);

  useEffect(() => {
    if (!selectedEventId || !eventDetail?.venueMapEnabled) return;
    let cancelled = false;

    setVenueMaps([]);
    setVenueMapError("");
    setLoadingVenueMaps(true);
    listPublicVenueMaps(selectedEventId, "VISITOR")
      .then((data) => {
        if (!cancelled) setVenueMaps(data ?? []);
      })
      .catch((error) => {
        if (!cancelled) {
          setVenueMaps([]);
          setVenueMapError(error instanceof ApiError ? error.message : "평면도를 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingVenueMaps(false);
      });

    return () => {
      cancelled = true;
    };
  }, [selectedEventId, eventDetail?.venueMapEnabled]);

  const top3 = useMemo(
    () => [...booths].sort((a, b) => b.congestion - a.congestion).slice(0, 3),
    [booths]
  );
  const interestList = booths.filter((b) => b.interest);
  const activeBooth = mapPinBooth || booths.find((b) => b.id === activeBoothId) || null;

  const openBoothSheet = (id) => {
    setMapPinBooth(null);
    setActiveBoothId(id);
    setBoothSheetOpen(true);
  };

  // 배치도 핀은 참가 부스 목록(mock)에 없는 실제 부스라서, 같은 바텀시트를 재사용하되
  // 혼잡도/방문자 수는 부스 id 기반의 가짜 값으로 채운다.
  const openMapBoothSheet = (position, venueMap) => {
    setMapPinBooth({
      id: position.boothCode,
      name: position.displayName || position.boothCode,
      zone: venueMap.floorName,
      congestion: mockCongestion(position.boothId),
      interest: false,
      icon: "storefront",
    });
    setBoothSheetOpen(true);
  };

  const toggleInterestFromSheet = () => {
    if (mapPinBooth) {
      setMapPinBooth((prev) => prev && { ...prev, interest: !prev.interest });
      return;
    }
    setBooths((prev) =>
      prev.map((b) => (b.id === activeBoothId ? { ...b, interest: !b.interest } : b))
    );
  };

  return (
    <div className="bg-surface font-body text-on-surface antialiased">
      {/* Top Nav */}
      <header className="fixed top-0 w-full h-[44px] z-[100] bg-black flex justify-between items-center px-lg">
        <Link to="/" className="font-hero-display text-tagline text-white">EvenToday</Link>
        <div className="flex items-center gap-sm">
          <button className="text-white/80 hover:text-white transition-colors"><Icon name="search" className="text-[20px]" /></button>
          <NotificationBell />
          <button onClick={() => setQrSheetOpen(true)} className="text-white/80 hover:text-white transition-colors"><Icon name="qr_code_2" className="text-[20px]" /></button>
        </div>
      </header>

      {/* Left sidebar (desktop) */}
      <aside className="hidden md:flex flex-col fixed left-0 top-[44px] h-[calc(100vh-44px)] w-[220px] bg-white border-r border-hairline z-40 py-lg px-sm gap-1">
        {tabButtons.map((t) => (
          <button
            key={t.key}
            onClick={() => setTab(t.key)}
            className={`flex items-center gap-sm px-md py-sm rounded-lg font-body-strong transition-colors ${
              tab === t.key ? "text-primary-focus bg-primary-container/10" : "text-on-surface-variant hover:bg-surface-container"
            }`}
          >
            <Icon name={t.icon} /> {t.label}
          </button>
        ))}
        <Link to="/" className="mt-auto flex items-center gap-sm px-md py-sm rounded-lg text-on-surface-variant hover:bg-surface-container transition-colors">
          <Icon name="arrow_back" /> 메인으로
        </Link>
      </aside>

      <main className="pt-[44px] md:ml-[220px] pb-[90px] md:pb-xl">
        {/* App Header & QR */}
        <section className="pt-lg pb-md px-lg bg-surface-container-low">
          <div className="max-w-[900px] mx-auto mb-md">
            {loadingEvents ? (
              <p className="text-caption text-ink-muted">진행 중인 행사를 불러오는 중입니다.</p>
            ) : eventsError ? (
              <p className="text-caption text-error">{eventsError}</p>
            ) : events.length === 0 ? (
              <p className="text-caption text-ink-muted">공개된 행사가 없습니다.</p>
            ) : (
              <select
                value={selectedEventId}
                onChange={(e) => setSelectedEventId(e.target.value)}
                className="h-[36px] rounded-full border border-hairline px-md text-caption bg-white outline-none focus:border-primary-focus"
              >
                {events.map((event) => (
                  <option key={event.id} value={event.id}>{event.name}</option>
                ))}
              </select>
            )}
          </div>
          <div className="max-w-[900px] mx-auto flex justify-between items-end">
            <div>
              {eventDetailError ? (
                <p className="text-caption text-error mb-1">{eventDetailError}</p>
              ) : (
                <p className="text-caption text-secondary mb-1">{formatEventPeriod(eventDetail)}</p>
              )}
              <h1 className="font-display-lg-mobile md:font-display-lg text-display-lg-mobile md:text-display-lg text-on-surface">
                {eventDetail?.name ?? "행사를 선택해 주세요"}
              </h1>
            </div>
            <button onClick={() => setQrSheetOpen(true)} className="hidden md:flex bg-primary-container text-white px-lg py-sm rounded-full items-center gap-xs font-body-strong active:scale-95 transition-transform flex-shrink-0">
              <Icon name="qr_code_2" fill /> 입장 QR
            </button>
          </div>
          <div className="max-w-[900px] mx-auto mt-lg glass-nav border border-hairline rounded-xl p-md flex items-center justify-between flex-wrap gap-sm">
            <div className="flex items-center gap-sm">
              <div className="w-3 h-3 rounded-full dot-normal pulse" />
              <span className="font-body-strong">실시간 행사장 혼잡도: 보통</span>
            </div>
            <div className="flex gap-xs text-caption">
              <span className="px-sm py-1 bg-status-available/10 text-status-available font-bold rounded-full">여유</span>
              <span className="px-sm py-1 bg-status-pending/10 text-status-pending font-bold rounded-full">보통</span>
              <span className="px-sm py-1 bg-status-visited/10 text-status-visited font-bold rounded-full">혼잡</span>
            </div>
          </div>
        </section>

        {/* TAB: Floor map */}
        {tab === "map" && (
          <section className="py-lg px-lg max-w-[900px] mx-auto">
            <h2 className="font-display-md text-[20px] mb-md">행사장 배치도</h2>
            {loadingVenueMaps && <p className="text-caption text-ink-muted">평면도를 불러오는 중입니다.</p>}
            {venueMapError && <p className="text-caption text-error">{venueMapError}</p>}
            {!loadingVenueMaps && !venueMapError && venueMaps.length === 0 && (
              <p className="text-caption text-ink-muted">등록된 평면도가 없습니다.</p>
            )}
            {!loadingVenueMaps && venueMaps.length > 0 && (
              <div className="space-y-lg">
                {venueMaps.map((venueMap) => (
                  <div key={venueMap.id}>
                    <h3 className="font-body-strong text-body mb-sm">{venueMap.floorName}</h3>
                    <div className="bg-surface-pearl border border-hairline rounded-2xl p-lg">
                      <VenueMapPins venueMap={venueMap} onPinClick={(p) => openMapBoothSheet(p, venueMap)} />
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        )}

        {/* TAB: Booth list */}
        {tab === "booths" && (
          <section className="py-lg px-lg max-w-[900px] mx-auto">
            <h2 className="font-display-md text-[20px] mb-md">참가 부스 ({booths.length})</h2>
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-md">
              {booths.map((b) => (
                <div
                  key={b.id}
                  onClick={() => openBoothSheet(b.id)}
                  className="bg-white border border-hairline rounded-xl overflow-hidden cursor-pointer hover:shadow-md transition-all-custom"
                >
                  <div className="h-20 flex items-center justify-center bg-surface-container-low relative">
                    <Icon name={b.icon} className="text-[26px] text-primary" />
                    <span className="absolute top-1 left-1 text-[10px] font-bold bg-black/60 text-white px-1.5 py-0.5 rounded-full">{b.id}</span>
                  </div>
                  <div className="p-sm">
                    <p className="text-caption font-body-strong truncate">{b.name}</p>
                    <p className="text-[11px] text-ink-muted">{b.zone}</p>
                  </div>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* TAB: Popular */}
        {tab === "popular" && (
          <section className="py-lg px-lg max-w-[900px] mx-auto">
            <h2 className="font-display-md text-[20px] mb-md">인기 부스 TOP 3</h2>
            <div className="space-y-md">
              {top3.map((b, i) => (
                <div
                  key={b.id}
                  onClick={() => openBoothSheet(b.id)}
                  className="flex gap-md bg-white p-md rounded-2xl border border-hairline shadow-sm cursor-pointer active:bg-surface-pearl transition-colors"
                >
                  <div className="w-16 h-16 rounded-lg bg-surface-container-low flex items-center justify-center flex-shrink-0 border border-hairline">
                    <Icon name={b.icon} className="text-primary" />
                  </div>
                  <div className="flex-grow flex flex-col justify-center">
                    <div className="flex items-center gap-xs mb-1">
                      <span className="bg-primary text-white text-[10px] font-extrabold px-1.5 py-0.5 rounded">{i + 1}</span>
                      <span className="text-caption text-secondary">{b.zone}</span>
                    </div>
                    <h3 className="font-body-strong">{b.name}</h3>
                    <p className="text-caption text-secondary">방문자 {1200 - i * 300}명</p>
                  </div>
                  <div className="flex items-center"><Icon name="chevron_right" className="text-secondary" /></div>
                </div>
              ))}
            </div>
          </section>
        )}

        {/* TAB: Interest */}
        {tab === "interest" && (
          <section className="py-lg px-lg max-w-[900px] mx-auto">
            <h2 className="font-display-md text-[20px] mb-md">나의 관심 부스</h2>
            {interestList.length === 0 ? (
              <p className="text-center text-ink-muted py-xxl">
                <Icon name="favorite_border" className="text-[32px] block mb-sm" />
                등록한 관심 부스가 없어요
              </p>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-md">
                {interestList.map((b) => (
                  <div
                    key={b.id}
                    onClick={() => openBoothSheet(b.id)}
                    className="bg-white border border-hairline rounded-xl overflow-hidden cursor-pointer hover:shadow-md transition-all-custom"
                  >
                    <div className="h-20 flex items-center justify-center bg-surface-container-low relative">
                      <Icon name={b.icon} className="text-[26px] text-primary" />
                      <span className="absolute top-1 right-1 text-primary"><Icon name="favorite" fill className="text-[16px]" /></span>
                    </div>
                    <div className="p-sm">
                      <p className="text-caption font-body-strong truncate">{b.name}</p>
                      <p className="text-[11px] text-ink-muted">{b.zone}</p>
                    </div>
                  </div>
                ))}
              </div>
            )}
          </section>
        )}
      </main>

      {/* Mobile bottom nav */}
      <nav className="md:hidden fixed bottom-0 w-full h-[64px] bg-white border-t border-hairline flex items-center justify-around px-lg z-[100]">
        {tabButtons.map((t) => {
          const active = tab === t.key;
          return (
            <button key={t.key} onClick={() => setTab(t.key)} className={`flex flex-col items-center ${active ? "text-primary" : "text-secondary opacity-60"}`}>
              <Icon name={t.icon} fill={active} className="text-[24px]" />
              <span className={`text-[10px] mt-1 ${active ? "font-bold" : ""}`}>{t.mobile}</span>
            </button>
          );
        })}
      </nav>

      {/* Booth detail bottom sheet */}
      <div className={`sheet-overlay${boothSheetOpen ? " open" : ""}`}>
        <div className="sheet-backdrop" onClick={() => setBoothSheetOpen(false)} />
        <div className="sheet-panel max-w-[600px] mx-auto left-0 right-0">
          <div className="w-12 h-1.5 bg-surface-variant rounded-full mx-auto mb-lg" />
          {activeBooth && (
            <>
              <div className="flex justify-between items-start mb-md">
                <div>
                  <h3 className="font-display-md text-[24px] text-on-surface">{activeBooth.name}</h3>
                  <p className="text-secondary text-caption">{activeBooth.id} · {activeBooth.zone}</p>
                </div>
                <span className={`px-sm py-1 text-caption font-bold rounded-full bg-${levelColor(activeBooth.congestion)}/10 text-${levelColor(activeBooth.congestion)}`}>
                  {levelLabel(activeBooth.congestion)}
                </span>
              </div>
              <div className="grid grid-cols-2 gap-sm mb-lg">
                <div className="bg-surface-container-low p-md rounded-xl">
                  <p className="text-caption text-secondary mb-xs">실시간 대기</p>
                  <p className="font-display-md text-primary text-[22px]">{Math.round(activeBooth.congestion / 2)}분</p>
                </div>
                <div className="bg-surface-container-low p-md rounded-xl">
                  <p className="text-caption text-secondary mb-xs">오늘 방문자</p>
                  <p className="font-body-strong">{(1200 - activeBooth.congestion * 5).toLocaleString()}명</p>
                </div>
              </div>
              <div className="flex gap-sm">
                <button onClick={toggleInterestFromSheet} className="flex-1 border border-hairline rounded-xl py-md font-body-strong flex items-center justify-center gap-xs active:scale-95 transition-transform">
                  {activeBooth.interest ? (
                    <><Icon name="favorite" fill className="text-primary" /> 관심 등록됨</>
                  ) : (
                    <><Icon name="favorite_border" /> 관심 등록</>
                  )}
                </button>
                <Link to={`/booth-detail?booth=${activeBooth.id}`} className="flex-1 bg-primary text-white rounded-xl py-md font-body-strong text-center active:scale-95 transition-transform">
                  부스 상세·예약
                </Link>
              </div>
            </>
          )}
        </div>
      </div>

      {/* QR sheet */}
      <div className={`sheet-overlay${qrSheetOpen ? " open" : ""}`}>
        <div className="sheet-backdrop" onClick={() => setQrSheetOpen(false)} />
        <div className="sheet-panel max-w-[420px] mx-auto left-0 right-0 text-center">
          <div className="w-12 h-1.5 bg-surface-variant rounded-full mx-auto mb-lg" />
          <h3 className="font-display-md text-[20px] mb-md">입장 QR</h3>
          <div className="bg-surface-container-low p-xl rounded-2xl inline-block mb-md">
            <div className="grid grid-cols-10 gap-[2px] w-[160px] mx-auto">
              {qrPixels.map((on, i) => (
                <div key={i} className={`w-full aspect-square ${on ? "bg-black" : "bg-white"}`} />
              ))}
            </div>
          </div>
          <p className="font-body-strong">회원 입장 QR · 사용 전</p>
          <p className="text-caption text-ink-muted mt-xs">화면 밝기를 최대로 설정하면 현장 스캔이 더 원활해요.</p>
        </div>
      </div>

    </div>
  );
}
