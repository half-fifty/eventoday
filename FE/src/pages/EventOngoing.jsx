import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import NotificationBell from "../components/NotificationBell.jsx";
import VenueMapPins from "../components/VenueMapPins.jsx";
import { ApiError } from "../api/apiClient.js";
import { eventApi } from "../api/eventApi.js";
import { listPublicVenueMaps, getVenueMapMarkersWithCongestion } from "../api/venueMapApi.js";
import {
  listAllPublicBooths,
  addBoothInterest,
  removeBoothInterest,
  getMyInterests,
  getRecommendedBooths,
  searchGuideBooths,
} from "../api/boothApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import useAuth from "../hooks/useAuth.js";
import useFunnelTracking from "../hooks/useFunnelTracking.js";
import { congestionLevelMeta, congestionLevelFromCount } from "../utils/congestion.js";

const formatEventPeriod = (event) => {
  if (!event) return "";
  const start = new Date(event.startAt);
  const end = new Date(event.endAt);
  const fmt = (d) => `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
  return `${fmt(start)} – ${fmt(end)} · ${event.venueName ?? ""}`;
};

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
  const { eventId } = useParams();
  const selectedEventId = eventId || "";
  const trackFunnelAction = useFunnelTracking(eventId);
  const [eventDetail, setEventDetail] = useState(null);
  const [loadingEventDetail, setLoadingEventDetail] = useState(false);
  const [eventDetailError, setEventDetailError] = useState("");

  const [venueMaps, setVenueMaps] = useState([]);
  const [loadingVenueMaps, setLoadingVenueMaps] = useState(false);
  const [venueMapError, setVenueMapError] = useState("");
  // 평면도 핀별 혼잡도(최근 10분 QR스캔 수 기준). boothId -> { congestionCount, congestionLevel }
  const [congestionByBoothId, setCongestionByBoothId] = useState(new Map());

  const [tab, setTab] = useState("map");
  // 부스 목록 탭을 실제로 봤을 때만, 행사당 최초 1회만 기록한다. 같은 컴포넌트에서 eventId만
  // 바뀌는 경우(라우트 파라미터 변경)에도 새 행사 기준으로 다시 전송되도록 ref에 eventId를 같이 둔다.
  const boothListTrackedEventIdRef = useRef(null);
  useEffect(() => {
    if (tab !== "booths" || !eventId || boothListTrackedEventIdRef.current === eventId) return;
    boothListTrackedEventIdRef.current = eventId;
    trackFunnelAction("VIEW_BOOTH_LIST");
  }, [tab, eventId, trackFunnelAction]);

  // 실제 배정 완료(ASSIGNED)된 참가 부스 목록.
  const [participatingBooths, setParticipatingBooths] = useState([]);
  // 상태 필터링 전 전체 공개 부스 목록. 배치도 핀·인기 부스 탭은 ASSIGNED가 아닌 부스도 찍힐 수 있어 대표이미지·소개 조회에 사용한다.
  const [allPublicBooths, setAllPublicBooths] = useState([]);
  const [loadingParticipatingBooths, setLoadingParticipatingBooths] = useState(false);
  const [participatingBoothsError, setParticipatingBoothsError] = useState("");
  // 바텀시트에 표시할 부스 (평면도 핀 클릭 / 인기·추천 부스 탭 항목 클릭이 공용으로 사용)
  const [activeBooth, setActiveBooth] = useState(null);
  const [boothSheetOpen, setBoothSheetOpen] = useState(false);
  const [qrSheetOpen, setQrSheetOpen] = useState(false);

  // 혼잡도 기반 추천/혼잡 부스
  const [recommendedBooths, setRecommendedBooths] = useState([]);
  const [congestedBooths, setCongestedBooths] = useState([]);
  const [recommendationMessage, setRecommendationMessage] = useState("");
  const [loadingRecommendation, setLoadingRecommendation] = useState(false);
  const [recommendationError, setRecommendationError] = useState("");

  // 모바일 부스 검색
  const [searchSheetOpen, setSearchSheetOpen] = useState(false);
  const [searchKeyword, setSearchKeyword] = useState("");
  const [searchResults, setSearchResults] = useState([]);
  const [searchLoading, setSearchLoading] = useState(false);
  const [searchError, setSearchError] = useState("");
  const [searchSubmitted, setSearchSubmitted] = useState(false);

  // 관심 부스: 실제 백엔드(GET /booths/interests)와 연동된다.
  const [interestedBooths, setInterestedBooths] = useState([]);
  const [loadingInterests, setLoadingInterests] = useState(false);
  const [interestsError, setInterestsError] = useState("");
  const [togglingInterestId, setTogglingInterestId] = useState(null);
  const interestedBoothIds = useMemo(
    () => new Set(interestedBooths.map((b) => b.boothId)),
    [interestedBooths]
  );
  // 관심 부스는 회원 기준으로 전체 행사에 걸쳐 조회되지만, 관심 부스 탭에는 현재 보고 있는
  // 행사의 부스만 노출해야 한다.
  const eventInterestedBooths = useMemo(
    () => interestedBooths.filter((b) => String(b.eventId) === String(selectedEventId)),
    [interestedBooths, selectedEventId]
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

  useEffect(() => {
    if (!selectedEventId) return;
    let cancelled = false;

    setEventDetail(null);
    setLoadingEventDetail(true);
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
      })
      .finally(() => {
        if (!cancelled) setLoadingEventDetail(false);
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
      setCongestionByBoothId(new Map());
      return;
    }

    let cancelled = false;
    setVenueMaps([]);
    setVenueMapError("");
    setLoadingVenueMaps(true);
    Promise.all([
      listPublicVenueMaps(selectedEventId, "VISITOR"),
      // 혼잡도는 부가 정보라 실패해도 평면도 자체는 보여줘야 하므로 별도로 흡수한다.
      getVenueMapMarkersWithCongestion(selectedEventId, "VISITOR").catch(() => []),
    ])
      .then(([maps, markerFloors]) => {
        if (cancelled) return;
        setVenueMaps(maps ?? []);
        const nextCongestionByBoothId = new Map();
        (markerFloors ?? []).forEach((floor) => {
          (floor.positions ?? []).forEach((position) => {
            nextCongestionByBoothId.set(position.boothId, {
              congestionCount: position.congestionCount,
              congestionLevel: position.congestionLevel,
            });
          });
        });
        setCongestionByBoothId(nextCongestionByBoothId);
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

  // 혼잡도 기반 추천/혼잡 부스
  useEffect(() => {
    if (!selectedEventId) return;
    let cancelled = false;

    setLoadingRecommendation(true);
    setRecommendationError("");
    getRecommendedBooths(selectedEventId, { size: 5 })
      .then((data) => {
        if (cancelled) return;
        setRecommendedBooths(data?.recommendedBooths ?? []);
        setCongestedBooths(data?.congestedBooths ?? []);
        setRecommendationMessage(data?.recommendation ?? "");
      })
      .catch((error) => {
        if (cancelled) return;
        setRecommendedBooths([]);
        setCongestedBooths([]);
        setRecommendationError(error instanceof ApiError ? error.message : "혼잡도 정보를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (!cancelled) setLoadingRecommendation(false);
      });

    return () => {
      cancelled = true;
    };
  }, [selectedEventId]);

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

  // 인기·추천 부스 탭에서 boothId로 대표이미지·구역 등 상세 정보를 찾을 때 사용.
  const boothMetaById = useMemo(() => {
    const map = new Map();
    allPublicBooths.forEach((b) => map.set(b.id, b));
    return map;
  }, [allPublicBooths]);

  const activeBoothIsInterested = activeBooth?.boothId != null && interestedBoothIds.has(activeBooth.boothId);

  const openBoothDetailSheet = (boothInfo) => {
    setActiveBooth(boothInfo);
    setBoothSheetOpen(true);
  };

  // 배치도 핀 클릭 시 바텀시트에 표시할 정보 구성 (평면도 마커 API의 혼잡도를 함께 붙인다).
  const openMapBoothSheet = (position, venueMap) => {
    const boothDetail = allPublicBooths.find((b) => b.id === position.boothId);
    const congestion = congestionByBoothId.get(position.boothId) ?? null;
    openBoothDetailSheet({
      boothId: position.boothId,
      code: position.boothCode,
      name: position.displayName || position.boothCode,
      zone: venueMap.floorName,
      representativeFileId: boothDetail?.representativeFileId ?? null,
      shortIntro: boothDetail?.shortIntro ?? "",
      congestionCount: congestion?.congestionCount ?? null,
      congestionLevel: congestion?.congestionLevel ?? null,
    });
  };

  // 인기·추천 부스 탭 항목 클릭 시 바텀시트에 표시할 정보 구성.
  const openRankedBoothSheet = (entry) => {
    const boothDetail = boothMetaById.get(entry.boothId);
    openBoothDetailSheet({
      boothId: entry.boothId,
      code: boothDetail?.boothCode ?? "",
      name: boothDetail?.displayName || boothDetail?.boothCode || `부스 #${entry.boothId}`,
      zone: [boothDetail?.floorName, boothDetail?.zoneName].filter(Boolean).join(" · "),
      representativeFileId: boothDetail?.representativeFileId ?? null,
      shortIntro: boothDetail?.shortIntro ?? "",
      congestionCount: entry.congestionCount ?? null,
      congestionLevel: congestionLevelFromCount(entry.congestionCount),
    });
  };

  const toggleInterestFromSheet = () => {
    if (!activeBooth?.boothId || !isAuthenticated) return;
    setBoothInterest(activeBooth.boothId, !interestedBoothIds.has(activeBooth.boothId));
  };

  // 상단 배너에 쓸 행사장 전체 혼잡도 요약 (평면도 핀 혼잡도 중 최댓값 기준).
  const overallCongestionLevel = useMemo(() => {
    const levels = Array.from(congestionByBoothId.values()).map((v) => v.congestionLevel);
    if (levels.length === 0) return null;
    if (levels.includes("HIGH")) return "HIGH";
    if (levels.includes("MEDIUM")) return "MEDIUM";
    return "LOW";
  }, [congestionByBoothId]);

  // 연속으로 검색하거나 행사를 전환하면 이전 요청이 나중에 도착해 최신 검색어 결과를 덮어쓸 수 있어,
  // 요청마다 증가하는 id를 매겨 가장 마지막에 시작된 요청의 응답만 반영한다.
  const searchRequestIdRef = useRef(0);
  const runBoothSearch = () => {
    if (!selectedEventId) return;
    const requestId = ++searchRequestIdRef.current;
    const keyword = searchKeyword.trim();
    setSearchSubmitted(true);
    setSearchLoading(true);
    setSearchError("");
    searchGuideBooths(selectedEventId, { keyword, page: 0, size: 20 })
      .then((data) => {
        if (searchRequestIdRef.current !== requestId) return;
        setSearchResults(Array.isArray(data?.content) ? data.content : []);
      })
      .catch((error) => {
        if (searchRequestIdRef.current !== requestId) return;
        setSearchResults([]);
        setSearchError(error instanceof ApiError ? error.message : "부스 검색에 실패했습니다.");
      })
      .finally(() => {
        if (searchRequestIdRef.current === requestId) setSearchLoading(false);
      });
  };

  const openSearchSheet = () => {
    setSearchKeyword("");
    setSearchResults([]);
    setSearchError("");
    setSearchSubmitted(false);
    setSearchSheetOpen(true);
  };

  return (
    <div className="bg-surface font-body text-on-surface antialiased">
      {/* Top Nav */}
      <header className="fixed top-0 w-full h-[44px] z-[100] bg-black flex justify-between items-center px-lg">
        <Link to="/" className="font-hero-display text-tagline text-white">EvenToday</Link>
        <div className="flex items-center gap-sm">
          <button
            onClick={openSearchSheet}
            aria-label="부스 검색"
            className="flex h-8 w-8 items-center justify-center rounded-full text-white transition-colors hover:bg-white/10"
          >
            <svg width="19" height="19" viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="2" strokeLinecap="round" strokeLinejoin="round">
              <circle cx="11" cy="11" r="7" />
              <line x1="21" y1="21" x2="16.65" y2="16.65" />
            </svg>
          </button>
          <NotificationBell />
          <button
            onClick={() => setQrSheetOpen(true)}
            aria-label="입장 QR 보기"
            className="flex h-8 w-8 items-center justify-center rounded-full text-status-available transition-colors hover:bg-white/10"
          >
            <Icon name="qr_code_2" fill className="text-[20px]" />
          </button>
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
          <div className="max-w-[900px] mx-auto flex justify-between items-end">
            <div>
              {loadingEventDetail ? (
                <p className="text-caption text-ink-muted mb-1">행사 정보를 불러오는 중입니다.</p>
              ) : eventDetailError ? (
                <p className="text-caption text-error mb-1">{eventDetailError}</p>
              ) : (
                <p className="text-caption text-secondary mb-1">{formatEventPeriod(eventDetail)}</p>
              )}
              <h1 className="font-display-lg-mobile md:font-display-lg text-display-lg-mobile md:text-display-lg text-on-surface">
                {eventDetail?.name ?? "행사 정보를 확인할 수 없습니다"}
              </h1>
            </div>
            <button onClick={() => setQrSheetOpen(true)} className="hidden md:flex bg-primary-container text-white px-lg py-sm rounded-full items-center gap-xs font-body-strong active:scale-95 transition-transform flex-shrink-0">
              <Icon name="qr_code_2" fill /> 입장 QR
            </button>
          </div>
          <div className="max-w-[900px] mx-auto mt-lg glass-nav border border-hairline rounded-xl p-md flex items-center justify-between flex-wrap gap-sm">
            <div className="flex items-center gap-sm">
              <div
                className={`w-3 h-3 rounded-full pulse ${
                  overallCongestionLevel ? `bg-${congestionLevelMeta(overallCongestionLevel).colorClass}` : "dot-normal"
                }`}
              />
              <span className="font-body-strong">
                실시간 행사장 혼잡도: {overallCongestionLevel ? congestionLevelMeta(overallCongestionLevel).label : "정보 없음"}
              </span>
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
                <div className="flex flex-wrap items-center gap-md text-caption text-ink-muted">
                  <span>범례</span>
                  <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-status-available" />여유</span>
                  <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-status-pending" />보통</span>
                  <span className="flex items-center gap-1"><span className="w-2.5 h-2.5 rounded-full bg-status-visited" />혼잡</span>
                  {isAuthenticated && (
                    <span className="flex items-center gap-1">
                      <span className="w-2.5 h-2.5 rounded-full bg-primary ring-2 ring-primary ring-offset-1" />내 관심 부스
                    </span>
                  )}
                </div>
                {venueMaps.map((venueMap) => (
                  <div key={venueMap.id}>
                    <h3 className="font-body-strong text-body mb-sm">{venueMap.floorName}</h3>
                    <div className="bg-surface-pearl border border-hairline rounded-2xl p-lg">
                      <VenueMapPins
                        venueMap={venueMap}
                        onPinClick={(p) => openMapBoothSheet(p, venueMap)}
                        pinClassName={(p) => {
                          const congestion = congestionByBoothId.get(p.boothId);
                          const meta = congestion ? congestionLevelMeta(congestion.congestionLevel) : null;
                          const baseColor = meta ? `bg-${meta.colorClass}` : "bg-primary";
                          const interestedRing = interestedBoothIds.has(p.boothId) ? " ring-2 ring-primary ring-offset-1" : "";
                          return baseColor + interestedRing;
                        }}
                      />
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
            <h2 className="font-display-md text-[20px] mb-md">인기·추천 부스</h2>
            {loadingRecommendation && <p className="text-caption text-ink-muted">혼잡도 정보를 불러오는 중입니다.</p>}
            {recommendationError && <p className="text-caption text-error">{recommendationError}</p>}

            {!loadingRecommendation && !recommendationError && (
              <>
                {recommendationMessage && (
                  <div className="bg-primary-container/10 border border-primary-container/30 rounded-xl p-md mb-lg flex items-start gap-sm">
                    <Icon name="lightbulb" className="text-primary flex-shrink-0" />
                    <p className="text-caption text-on-surface-variant">{recommendationMessage}</p>
                  </div>
                )}

                <h3 className="font-body-strong text-body mb-sm flex items-center gap-xs">
                  <Icon name="local_fire_department" className="text-status-visited text-[18px]" /> 지금 붐비는 부스
                </h3>
                {congestedBooths.length === 0 ? (
                  <p className="text-caption text-ink-muted mb-lg">아직 집계된 혼잡도 데이터가 없어요.</p>
                ) : (
                  <div className="space-y-md mb-lg">
                    {congestedBooths.map((entry, i) => {
                      const meta = boothMetaById.get(entry.boothId);
                      return (
                        <button
                          type="button"
                          key={entry.boothId}
                          onClick={() => openRankedBoothSheet(entry)}
                          className="w-full text-left flex gap-md bg-white p-md rounded-2xl border border-hairline shadow-sm cursor-pointer active:bg-surface-pearl transition-colors"
                        >
                          <div className="w-16 h-16 rounded-lg bg-surface-container-low flex items-center justify-center flex-shrink-0 border border-hairline overflow-hidden">
                            {meta?.representativeFileId ? (
                              <img src={fileDownloadUrl(meta.representativeFileId)} alt={meta.displayName || meta.boothCode} className="w-full h-full object-cover" />
                            ) : (
                              <Icon name="storefront" className="text-primary" />
                            )}
                          </div>
                          <div className="flex-grow flex flex-col justify-center">
                            <div className="flex items-center gap-xs mb-1">
                              <span className="bg-status-visited text-white text-[10px] font-extrabold px-1.5 py-0.5 rounded">{i + 1}</span>
                              <span className="text-caption text-secondary">{[meta?.floorName, meta?.zoneName].filter(Boolean).join(" · ")}</span>
                            </div>
                            <h3 className="font-body-strong">{meta?.displayName || meta?.boothCode || `부스 #${entry.boothId}`}</h3>
                            <p className="text-caption text-secondary">최근 10분 방문 {entry.congestionCount ?? 0}명</p>
                          </div>
                          <div className="flex items-center"><Icon name="chevron_right" className="text-secondary" /></div>
                        </button>
                      );
                    })}
                  </div>
                )}

                <h3 className="font-body-strong text-body mb-sm flex items-center gap-xs">
                  <Icon name="eco" className="text-status-available text-[18px]" /> 여유로운 추천 부스
                </h3>
                {recommendedBooths.length === 0 ? (
                  <p className="text-caption text-ink-muted">아직 집계된 혼잡도 데이터가 없어요.</p>
                ) : (
                  <div className="space-y-md">
                    {recommendedBooths.map((entry) => {
                      const meta = boothMetaById.get(entry.boothId);
                      return (
                        <button
                          type="button"
                          key={entry.boothId}
                          onClick={() => openRankedBoothSheet(entry)}
                          className="w-full text-left flex gap-md bg-white p-md rounded-2xl border border-hairline shadow-sm cursor-pointer active:bg-surface-pearl transition-colors"
                        >
                          <div className="w-16 h-16 rounded-lg bg-surface-container-low flex items-center justify-center flex-shrink-0 border border-hairline overflow-hidden">
                            {meta?.representativeFileId ? (
                              <img src={fileDownloadUrl(meta.representativeFileId)} alt={meta.displayName || meta.boothCode} className="w-full h-full object-cover" />
                            ) : (
                              <Icon name="storefront" className="text-primary" />
                            )}
                          </div>
                          <div className="flex-grow flex flex-col justify-center">
                            <div className="flex items-center gap-xs mb-1">
                              <span className="bg-status-available text-white text-[10px] font-extrabold px-1.5 py-0.5 rounded">{entry.rank}</span>
                              <span className="text-caption text-secondary">{[meta?.floorName, meta?.zoneName].filter(Boolean).join(" · ")}</span>
                            </div>
                            <h3 className="font-body-strong">{meta?.displayName || meta?.boothCode || `부스 #${entry.boothId}`}</h3>
                            <p className="text-caption text-secondary">최근 10분 방문 {entry.congestionCount ?? 0}명</p>
                          </div>
                          <div className="flex items-center"><Icon name="chevron_right" className="text-secondary" /></div>
                        </button>
                      );
                    })}
                  </div>
                )}
              </>
            )}
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
            ) : eventInterestedBooths.length === 0 ? (
              <p className="text-center text-ink-muted py-xxl">
                <Icon name="favorite_border" className="text-[32px] block mb-sm" />
                등록한 관심 부스가 없어요
              </p>
            ) : (
              <div className="grid grid-cols-2 sm:grid-cols-3 gap-md">
                {eventInterestedBooths.map((b) => (
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
                  <p className="text-secondary text-caption">{[activeBooth.code, activeBooth.zone].filter(Boolean).join(" · ")}</p>
                </div>
                {activeBooth.congestionLevel && (
                  <span className={`px-sm py-1 text-caption font-bold rounded-full bg-${congestionLevelMeta(activeBooth.congestionLevel).colorClass}/10 text-${congestionLevelMeta(activeBooth.congestionLevel).colorClass}`}>
                    {congestionLevelMeta(activeBooth.congestionLevel).label}
                  </span>
                )}
              </div>
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
              <div className="grid grid-cols-2 gap-sm mb-lg">
                <div className="bg-surface-container-low p-md rounded-xl">
                  <p className="text-caption text-secondary mb-xs">최근 10분 방문</p>
                  <p className="font-display-md text-primary text-[22px]">
                    {activeBooth.congestionCount != null ? `${activeBooth.congestionCount}명` : "정보 없음"}
                  </p>
                </div>
                <div className="bg-surface-container-low p-md rounded-xl">
                  <p className="text-caption text-secondary mb-xs">혼잡도</p>
                  <p className="font-body-strong">
                    {activeBooth.congestionLevel ? congestionLevelMeta(activeBooth.congestionLevel).label : "정보 없음"}
                  </p>
                </div>
              </div>
              {interestsError && (
                <p className="text-caption text-error mb-sm">{interestsError}</p>
              )}
              <div className="flex gap-sm">
                <button
                  onClick={toggleInterestFromSheet}
                  disabled={!activeBooth.boothId || !isAuthenticated || togglingInterestId === activeBooth.boothId}
                  title={!activeBooth.boothId ? "실제 부스 정보가 없어 관심 등록을 지원하지 않아요" : !isAuthenticated ? "로그인 후 이용할 수 있어요" : undefined}
                  className="flex-1 border border-hairline rounded-xl py-md font-body-strong flex items-center justify-center gap-xs active:scale-95 transition-transform disabled:opacity-50"
                >
                  {activeBoothIsInterested ? (
                    <><Icon name="favorite" fill className="text-primary" /> 관심 등록됨</>
                  ) : (
                    <><Icon name="favorite_border" /> 관심 등록</>
                  )}
                </button>
                <Link
                  to={`/booth-detail?eventId=${selectedEventId}&boothId=${activeBooth.boothId}`}
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

      {/* Booth search sheet */}
      <div className={`sheet-overlay${searchSheetOpen ? " open" : ""}`}>
        <div className="sheet-backdrop" onClick={() => setSearchSheetOpen(false)} />
        <div className="sheet-panel max-w-[600px] mx-auto left-0 right-0 max-h-[80vh] flex flex-col">
          <div className="w-12 h-1.5 bg-surface-variant rounded-full mx-auto mb-lg flex-shrink-0" />
          <div className="flex items-center gap-sm bg-surface-pearl border border-hairline rounded-full px-lg h-[48px] mb-md flex-shrink-0">
            <Icon name="search" className="text-ink-muted text-[20px]" />
            <input
              autoFocus
              value={searchKeyword}
              onChange={(e) => setSearchKeyword(e.target.value)}
              onKeyDown={(e) => e.key === "Enter" && runBoothSearch()}
              type="text"
              placeholder="부스명으로 검색"
              className="flex-1 bg-transparent outline-none text-body"
            />
            <button onClick={runBoothSearch} className="text-primary font-body-strong text-caption flex-shrink-0">검색</button>
          </div>
          <div className="flex-1 overflow-y-auto">
            {searchLoading && <p className="text-caption text-ink-muted">검색 중입니다.</p>}
            {searchError && <p className="text-caption text-error">{searchError}</p>}
            {!searchLoading && !searchError && searchSubmitted && searchResults.length === 0 && (
              <p className="text-caption text-ink-muted py-lg text-center">검색 결과가 없어요.</p>
            )}
            {!searchLoading && searchResults.length > 0 && (
              <div className="space-y-sm">
                {searchResults.map((b) => (
                  <Link
                    key={b.boothId}
                    to={`/booth-detail?eventId=${selectedEventId}&boothId=${b.boothId}`}
                    onClick={() => setSearchSheetOpen(false)}
                    className="flex items-center gap-md p-sm rounded-xl hover:bg-surface-pearl transition-colors"
                  >
                    <div className="w-12 h-12 rounded-lg bg-surface-container-low flex items-center justify-center flex-shrink-0">
                      <Icon name="storefront" className="text-primary" />
                    </div>
                    <div className="flex-1 min-w-0">
                      <p className="font-body-strong truncate">{b.displayName || b.boothCode}</p>
                      <p className="text-caption text-ink-muted truncate">{b.shortIntro}</p>
                    </div>
                    {b.averageRating != null && (
                      <span className="text-caption text-secondary flex items-center gap-1 flex-shrink-0">
                        <Icon name="star" fill className="text-[13px] text-amber-500" />{b.averageRating.toFixed(1)}
                      </span>
                    )}
                  </Link>
                ))}
              </div>
            )}
          </div>
        </div>
      </div>

    </div>
  );
}
