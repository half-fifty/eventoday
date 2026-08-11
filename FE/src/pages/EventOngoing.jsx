import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import NotificationBell from "../components/NotificationBell.jsx";
import VenueMapPins from "../components/VenueMapPins.jsx";
import { ApiError } from "../api/apiClient.js";
import { eventApi } from "../api/eventApi.js";
import { listPublicVenueMaps } from "../api/venueMapApi.js";
import { listAllPublicBooths, addBoothInterest, removeBoothInterest, getMyInterests } from "../api/boothApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import useAuth from "../hooks/useAuth.js";

const formatEventPeriod = (event) => {
  if (!event) return "";
  const start = new Date(event.startAt);
  const end = new Date(event.endAt);
  const fmt = (d) => `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
  return `${fmt(start)} – ${fmt(end)} · ${event.venueName ?? ""}`;
};

// 인기 부스 탭은 실시간 혼잡도/방문자 지표 API가 없어 여전히 mock 데이터를 사용한다.
const initialBooths = [
  { id: "A01", name: "맛있는 식탁", zone: "A구역 1층", congestion: 62, icon: "lunch_dining" },
  { id: "A02", name: "그린 키친랩", zone: "A구역 1층", congestion: 30, icon: "blender" },
  { id: "A03", name: "베이크하우스", zone: "A구역 1층", congestion: 88, icon: "bakery_dining" },
  { id: "A04", name: "브루잉 스튜디오", zone: "A구역 2층", congestion: 45, icon: "coffee" },
  { id: "A05", name: "스마트키친 로보틱스", zone: "A구역 2층", congestion: 71, icon: "smart_toy" },
  { id: "A06", name: "콜드체인 솔루션", zone: "A구역 2층", congestion: 20, icon: "ac_unit" },
  { id: "A07", name: "비건 델리", zone: "B구역 1층", congestion: 55, icon: "eco" },
  { id: "A08", name: "프레시 로스터리", zone: "B구역 1층", congestion: 40, icon: "coffee_maker" },
  { id: "A09", name: "패키징 이노베이션", zone: "B구역 2층", congestion: 66, icon: "inventory_2" },
  { id: "A10", name: "푸드 딜리버리 테크", zone: "B구역 2층", congestion: 33, icon: "delivery_dining" },
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

export default function EventOngoing() {
  const { isAuthenticated, loading: authLoading } = useAuth();
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

  // 실제 배정 완료(ASSIGNED)된 참가 부스 목록. 인기 부스 탭은 아직 mock(booths)을 그대로 쓴다.
  const [participatingBooths, setParticipatingBooths] = useState([]);
  // 상태 필터링 전 전체 공개 부스 목록. 배치도 핀은 ASSIGNED가 아닌 부스도 찍힐 수 있어 대표이미지·소개 조회에 사용한다.
  const [allPublicBooths, setAllPublicBooths] = useState([]);
  const [loadingParticipatingBooths, setLoadingParticipatingBooths] = useState(false);
  const [participatingBoothsError, setParticipatingBoothsError] = useState("");
  const [activeBoothId, setActiveBoothId] = useState(null);
  const [boothSheetOpen, setBoothSheetOpen] = useState(false);
  const [qrSheetOpen, setQrSheetOpen] = useState(false);

  // 관심 부스: 실제 백엔드(GET /booths/interests)와 연동된다.
  const [interestedBooths, setInterestedBooths] = useState([]);
  const [loadingInterests, setLoadingInterests] = useState(false);
  const [interestsError, setInterestsError] = useState("");
  const [togglingInterestId, setTogglingInterestId] = useState(null);
  const interestedBoothIds = useMemo(
    () => new Set(interestedBooths.map((b) => b.boothId)),
    [interestedBooths]
  );

  // 마운트 시 초기 조회와 toggleInterestFromSheet/관심 부스 탭 삭제 버튼의 뮤테이션 후 수동
  // 새로고침이 같은 함수를 공유한다. 두 경로가 겹쳐서 요청하면(예: 초기 조회가 늦게 끝나서 방금
  // 등록한 관심을 다시 덮어쓰는 경우) 오래된 응답이 최신 상태를 덮어쓸 수 있어, 요청마다 증가하는
  // id를 매겨 가장 마지막에 시작된 요청의 응답만 반영한다.
  const interestRequestIdRef = useRef(0);
  const refreshInterests = () => {
    const requestId = ++interestRequestIdRef.current;

    if (!isAuthenticated) {
      setInterestedBooths([]);
      return;
    }

    setLoadingInterests(true);
    setInterestsError("");
    getMyInterests()
      .then((data) => {
        if (interestRequestIdRef.current !== requestId) return;
        setInterestedBooths(Array.isArray(data) ? data : []);
      })
      .catch((error) => {
        if (interestRequestIdRef.current !== requestId) return;
        setInterestedBooths([]);
        setInterestsError(error instanceof ApiError ? error.message : "관심 부스 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (interestRequestIdRef.current === requestId) setLoadingInterests(false);
      });
  };

  useEffect(() => {
    refreshInterests();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated]);

  // 관심 등록/해제 공통 처리 (배치도 핀 바텀시트, 관심 부스 탭 삭제 버튼에서 공유).
  const setBoothInterest = async (boothId, shouldBeInterested) => {
    if (togglingInterestId) return;
    setTogglingInterestId(boothId);
    try {
      if (shouldBeInterested) {
        await addBoothInterest(boothId);
      } else {
        await removeBoothInterest(boothId);
      }
      refreshInterests();
    } catch (error) {
      setInterestsError(error instanceof ApiError ? error.message : "관심 부스 처리에 실패했습니다.");
    } finally {
      setTogglingInterestId(null);
    }
  };

  // 진행 중인 실제 행사(PUBLISHED) 목록을 불러와 선택할 수 있게 한다.
  useEffect(() => {
    eventApi.list({ size: 100, sort: "startAt,asc" })
      .then((result) => {
        const list = result?.data?.content || [];
        setEvents(list);
        // selectedEventId를 클로저로 참조하면 응답이 늦게 도착했을 때 그 사이 URL로
        // 바뀐 선택을 덮어쓸 수 있어, 그 시점의 실제 URL을 기준으로 판단한다.
        const currentUrlEventId = new URLSearchParams(window.location.search).get("eventId");
        if (!currentUrlEventId && list.length > 0) {
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
    // eventDetail은 selectedEventId 변경 후 비동기로 도착하므로, 아직 이전 행사의
    // eventDetail이 남아있는 동안 새 행사로 요청이 나가지 않도록 id를 함께 확인한다.
    const isCurrentEventDetail = eventDetail && String(eventDetail.id) === String(selectedEventId);
    if (!selectedEventId || !isCurrentEventDetail || !eventDetail.venueMapEnabled) {
      setVenueMaps([]);
      setVenueMapError("");
      setLoadingVenueMaps(false);
      return;
    }

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
  }, [selectedEventId, eventDetail]);

  // 참가 부스: 공개 목록(AVAILABLE/ASSIGNED)에서 실제 배정 완료된 부스만 골라 보여준다.
  useEffect(() => {
    if (!selectedEventId) return;
    let cancelled = false;

    setParticipatingBooths([]);
    setAllPublicBooths([]);
    setParticipatingBoothsError("");
    setLoadingParticipatingBooths(true);
    listAllPublicBooths(selectedEventId)
      .then((content) => {
        if (!cancelled) {
          setAllPublicBooths(content);
          const assigned = content.filter((b) => b.status === "ASSIGNED");
          setParticipatingBooths(assigned);
        }
      })
      .catch((error) => {
        if (!cancelled) {
          setParticipatingBooths([]);
          setAllPublicBooths([]);
          setParticipatingBoothsError(error instanceof ApiError ? error.message : "참가 부스 목록을 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoadingParticipatingBooths(false);
      });

    return () => {
      cancelled = true;
    };
  }, [selectedEventId]);

  const top3 = useMemo(
    () => [...booths].sort((a, b) => b.congestion - a.congestion).slice(0, 3),
    [booths]
  );
  const activeBooth = mapPinBooth || booths.find((b) => b.id === activeBoothId) || null;
  // 인기 부스(mock)는 실제 boothId가 없어 관심 등록을 지원하지 않는다.
  const activeBoothRealId = mapPinBooth?.boothId ?? null;
  const activeBoothIsInterested = activeBoothRealId != null && interestedBoothIds.has(activeBoothRealId);

  const openBoothSheet = (id) => {
    setMapPinBooth(null);
    setActiveBoothId(id);
    setBoothSheetOpen(true);
  };

  // 배치도 핀은 참가 부스 목록(mock)에 없는 실제 부스라서 같은 바텀시트를 재사용한다.
  // 혼잡도/대기시간/방문자 수는 실제 지표 API가 없어 표시하지 않는다(정보 없음 처리).
  const openMapBoothSheet = (position, venueMap) => {
    const boothDetail = allPublicBooths.find((b) => b.id === position.boothId);
    setMapPinBooth({
      boothId: position.boothId,
      id: position.boothCode,
      name: position.displayName || position.boothCode,
      zone: venueMap.floorName,
      icon: "storefront",
      isMapBooth: true,
      representativeFileId: boothDetail?.representativeFileId ?? null,
      shortIntro: boothDetail?.shortIntro ?? "",
    });
    setBoothSheetOpen(true);
  };

  const toggleInterestFromSheet = () => {
    if (!activeBoothRealId || !isAuthenticated) return;
    setBoothInterest(activeBoothRealId, !interestedBoothIds.has(activeBoothRealId));
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
            <h2 className="font-display-md text-[20px] mb-md">참가 부스 ({participatingBooths.length})</h2>
            {loadingParticipatingBooths && <p className="text-caption text-ink-muted">부스 목록을 불러오는 중입니다.</p>}
            {participatingBoothsError && <p className="text-caption text-error">{participatingBoothsError}</p>}
            {!loadingParticipatingBooths && !participatingBoothsError && participatingBooths.length === 0 && (
              <p className="text-caption text-ink-muted">아직 배정 완료된 참가 부스가 없습니다.</p>
            )}
            <div className="grid grid-cols-2 sm:grid-cols-3 gap-md">
              {participatingBooths.map((b) => (
                <Link
                  key={b.id}
                  to={`/booth-detail?eventId=${selectedEventId}&boothId=${b.id}`}
                  className="bg-white border border-hairline rounded-xl overflow-hidden hover:shadow-md transition-all-custom"
                >
                  <div className="h-20 flex items-center justify-center bg-surface-container-low relative overflow-hidden">
                    {b.representativeFileId ? (
                      <img src={fileDownloadUrl(b.representativeFileId)} alt={b.displayName || b.boothCode} className="w-full h-full object-cover" />
                    ) : (
                      <Icon name="storefront" className="text-[26px] text-primary" />
                    )}
                    <span className="absolute top-1 left-1 text-[10px] font-bold bg-black/60 text-white px-1.5 py-0.5 rounded-full">{b.boothCode}</span>
                  </div>
                  <div className="p-sm">
                    <p className="text-caption font-body-strong truncate">{b.displayName || b.boothCode}</p>
                    <p className="text-[11px] text-ink-muted truncate">
                      {[b.floorName, b.zoneName].filter(Boolean).join(" · ") || b.shortIntro || " "}
                    </p>
                  </div>
                </Link>
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
            {!isAuthenticated ? (
              <p className="text-center text-ink-muted py-xxl">
                <Icon name="favorite_border" className="text-[32px] block mb-sm" />
                로그인 후 관심 부스를 확인할 수 있어요
              </p>
            ) : loadingInterests ? (
              <p className="text-caption text-ink-muted">관심 부스를 불러오는 중입니다.</p>
            ) : interestsError ? (
              <p className="text-caption text-error">{interestsError}</p>
            ) : interestedBooths.length === 0 ? (
              <p className="text-center text-ink-muted py-xxl">
                <Icon name="favorite_border" className="text-[32px] block mb-sm" />
                등록한 관심 부스가 없어요
              </p>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-md">
                {interestedBooths.map((b) => (
                  // 삭제 버튼은 Link(a 태그) 밖의 형제 요소로 둔다 - <a> 안에 <button>을 중첩하는 건
                  // 유효하지 않은 HTML이라 접근성 트리/하이드레이션 문제를 일으킬 수 있다.
                  <div key={b.boothId} className="relative bg-white border border-hairline rounded-xl overflow-hidden hover:shadow-md transition-all-custom">
                    <Link to={`/booth-detail?eventId=${b.eventId}&boothId=${b.boothId}`}>
                      <div className="h-20 flex items-center justify-center bg-surface-container-low">
                        <Icon name="storefront" className="text-[26px] text-primary" />
                      </div>
                      <div className="p-sm">
                        <p className="text-caption font-body-strong truncate">{b.displayName}</p>
                        <p className="text-[11px] text-ink-muted truncate">{b.shortIntro}</p>
                      </div>
                    </Link>
                    <button
                      onClick={() => setBoothInterest(b.boothId, false)}
                      disabled={togglingInterestId === b.boothId}
                      className="absolute top-1 right-1 text-primary"
                    >
                      <Icon name="favorite" fill className="text-[16px]" />
                    </button>
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
        {!authLoading && (
          <Link
            to={isAuthenticated ? "/mypage" : "/guest/orders"}
            className="flex flex-col items-center text-secondary opacity-60"
          >
            <Icon name="person" className="text-[24px]" />
            <span className="mt-1 text-[10px]">마이</span>
          </Link>
        )}
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
                {!activeBooth.isMapBooth && (
                  <span className={`px-sm py-1 text-caption font-bold rounded-full bg-${levelColor(activeBooth.congestion)}/10 text-${levelColor(activeBooth.congestion)}`}>
                    {levelLabel(activeBooth.congestion)}
                  </span>
                )}
              </div>
              {activeBooth.isMapBooth && (
                <div className="mb-lg">
                  <div className="h-32 rounded-xl overflow-hidden bg-surface-container-low flex items-center justify-center mb-sm">
                    {activeBooth.representativeFileId ? (
                      <img
                        src={fileDownloadUrl(activeBooth.representativeFileId)}
                        alt={activeBooth.name}
                        className="w-full h-full object-cover"
                      />
                    ) : (
                      <Icon name="storefront" className="text-[32px] text-primary" />
                    )}
                  </div>
                  {activeBooth.shortIntro && (
                    <p className="text-caption text-on-surface-variant">{activeBooth.shortIntro}</p>
                  )}
                </div>
              )}
              <div className="grid grid-cols-2 gap-sm mb-lg">
                <div className="bg-surface-container-low p-md rounded-xl">
                  <p className="text-caption text-secondary mb-xs">실시간 대기</p>
                  <p className="font-display-md text-primary text-[22px]">
                    {activeBooth.isMapBooth ? "정보 없음" : `${Math.round(activeBooth.congestion / 2)}분`}
                  </p>
                </div>
                <div className="bg-surface-container-low p-md rounded-xl">
                  <p className="text-caption text-secondary mb-xs">오늘 방문자</p>
                  <p className="font-body-strong">
                    {activeBooth.isMapBooth ? "정보 없음" : `${(1200 - activeBooth.congestion * 5).toLocaleString()}명`}
                  </p>
                </div>
              </div>
              {interestsError && (
                <p className="text-caption text-error mb-sm">{interestsError}</p>
              )}
              <div className="flex gap-sm">
                <button
                  onClick={toggleInterestFromSheet}
                  disabled={!activeBoothRealId || !isAuthenticated || togglingInterestId === activeBoothRealId}
                  title={!activeBoothRealId ? "실제 부스 정보가 없어 관심 등록을 지원하지 않아요" : !isAuthenticated ? "로그인 후 이용할 수 있어요" : undefined}
                  className="flex-1 border border-hairline rounded-xl py-md font-body-strong flex items-center justify-center gap-xs active:scale-95 transition-transform disabled:opacity-50"
                >
                  {activeBoothIsInterested ? (
                    <><Icon name="favorite" fill className="text-primary" /> 관심 등록됨</>
                  ) : (
                    <><Icon name="favorite_border" /> 관심 등록</>
                  )}
                </button>
                <Link
                  to={activeBooth.isMapBooth
                    ? `/booth-detail?eventId=${selectedEventId}&boothId=${activeBooth.boothId}`
                    : `/booth-detail?booth=${activeBooth.id}`}
                  className="flex-1 bg-primary text-white rounded-xl py-md font-body-strong text-center active:scale-95 transition-transform"
                >
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
