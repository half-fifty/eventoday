import { useEffect, useRef, useState } from "react";
import { Link, useParams } from "react-router-dom";
import TopNav from "../components/TopNav.jsx";
import Footer from "../components/Footer.jsx";
import Icon from "../components/Icon.jsx";
import { ApiError } from "../api/apiClient.js";
import { getPublicRecruitment } from "../api/recruitmentApi.js";
import { listPublicBooths } from "../api/boothApi.js";
import { listPublicVenueMaps } from "../api/venueMapApi.js";
import VenueMapPins from "../components/VenueMapPins.jsx";

const STATUS_BADGE = {
  OPEN: { label: "모집 중", cls: "bg-primary-container/10 text-primary-focus" },
  CLOSED: { label: "모집 마감", cls: "bg-surface-container-highest text-secondary" },
  COMPLETED: { label: "모집 완료", cls: "bg-status-visited/10 text-status-visited" },
};

const BOOTH_STATUS_BADGE = {
  AVAILABLE: { label: "신청 가능", cls: "bg-primary-container/10 text-primary-focus" },
  APPLICATION_PENDING: { label: "심사 중", cls: "bg-surface-container-highest text-secondary" },
  ASSIGNED: { label: "배정 완료", cls: "bg-status-visited/10 text-status-visited" },
  UNAVAILABLE: { label: "신청 불가", cls: "bg-error/10 text-error" },
};

const formatDateTime = (isoValue) => {
  if (!isoValue) return "-";
  const d = new Date(isoValue);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
};

export default function RecruitmentDetail() {
  const { recruitmentId } = useParams();
  const [recruitment, setRecruitment] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [booths, setBooths] = useState([]);
  const [loadingBooths, setLoadingBooths] = useState(false);
  const [boothError, setBoothError] = useState("");
  const [boothPage, setBoothPage] = useState(0);
  const [boothHasMore, setBoothHasMore] = useState(false);
  const [loadingMoreBooths, setLoadingMoreBooths] = useState(false);
  const [selectedBooth, setSelectedBooth] = useState(null);
  const [venueMaps, setVenueMaps] = useState([]);
  const [venueMapError, setVenueMapError] = useState("");
  const [highlightedBoothId, setHighlightedBoothId] = useState(null);
  // 행사를 전환했을 때 이전 행사의 부스 목록 요청(더 보기 포함)이 늦게 도착해
  // 현재 행사의 상태를 덮어쓰는 것을 막기 위한 세대 가드.
  const boothGenerationRef = useRef(0);

  useEffect(() => {
    let cancelled = false;

    setLoading(true);
    setError("");
    setRecruitment(null);

    getPublicRecruitment(recruitmentId)
      .then((data) => {
        if (!cancelled) setRecruitment(data);
      })
      .catch((err) => {
        if (!cancelled) {
          if (err instanceof ApiError && err.status === 404) {
            setError("모집 공고를 찾을 수 없거나 아직 공개되지 않았습니다.");
          } else {
            setError(err instanceof ApiError ? err.message : "모집 공고를 불러오지 못했습니다.");
          }
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });

    return () => {
      cancelled = true;
    };
  }, [recruitmentId]);

  useEffect(() => {
    if (!recruitment?.eventId) return;
    const generation = ++boothGenerationRef.current;

    setLoadingBooths(true);
    setBoothError("");
    setBoothPage(0);
    setBooths([]);
    setBoothHasMore(false);

    listPublicBooths(recruitment.eventId, { page: 0, size: 50 })
      .then((data) => {
        if (boothGenerationRef.current !== generation) return;
        setBooths(data?.content || []);
        setBoothHasMore(data ? !data.last : false);
      })
      .catch((err) => {
        if (boothGenerationRef.current !== generation) return;
        setBooths([]);
        setBoothHasMore(false);
        setBoothError(err instanceof ApiError ? err.message : "부스 목록을 불러오지 못했습니다.");
      })
      .finally(() => {
        if (boothGenerationRef.current === generation) setLoadingBooths(false);
      });
  }, [recruitment?.eventId]);

  const loadMoreBooths = async () => {
    if (!recruitment?.eventId || loadingMoreBooths || !boothHasMore) return;

    const generation = boothGenerationRef.current;
    const nextPage = boothPage + 1;
    setLoadingMoreBooths(true);
    setBoothError("");
    try {
      const data = await listPublicBooths(recruitment.eventId, { page: nextPage, size: 50 });
      if (boothGenerationRef.current !== generation) return;
      setBooths((prev) => [...prev, ...(data?.content || [])]);
      setBoothHasMore(data ? !data.last : false);
      setBoothPage(nextPage);
    } catch (err) {
      if (boothGenerationRef.current !== generation) return;
      setBoothError(err instanceof ApiError ? err.message : "부스 목록을 더 불러오지 못했습니다.");
    } finally {
      if (boothGenerationRef.current === generation) setLoadingMoreBooths(false);
    }
  };

  useEffect(() => {
    if (!recruitment?.eventId) return;
    let cancelled = false;

    setVenueMaps([]);
    setVenueMapError("");
    // 게시된 평면도가 없으면 빈 배열이 정상 응답이라, "없음"과 "조회 실패"를 구분해 보여준다.
    listPublicVenueMaps(recruitment.eventId, "RECRUITMENT")
      .then((data) => {
        if (!cancelled) setVenueMaps(data ?? []);
      })
      .catch((err) => {
        if (!cancelled) {
          setVenueMaps([]);
          setVenueMapError(err instanceof ApiError ? err.message : "평면도를 불러오지 못했습니다.");
        }
      });

    return () => {
      cancelled = true;
    };
  }, [recruitment?.eventId]);

  const badge = recruitment ? STATUS_BADGE[recruitment.status] ?? { label: recruitment.status, cls: "bg-surface-container" } : null;

  // 평면도 핀은 게시된 위치 목록 기준이라, 아직 로드되지 않은(더 보기 이전) 부스일 수도 있다.
  // 그런 경우엔 상세 모달 대신 조용히 무시한다.
  const selectBoothFromPin = (boothId) => {
    const booth = booths.find((b) => b.id === boothId);
    if (!booth) {
      setBoothError("이 부스는 아직 목록에 없습니다. \"부스 더 보기\"를 눌러 주세요.");
      return;
    }
    setBoothError("");
    setHighlightedBoothId(boothId);
    setSelectedBooth(booth);
  };

  const selectBoothFromList = (booth) => {
    setBoothError("");
    setHighlightedBoothId(booth.id);
    setSelectedBooth(booth);
  };

  return (
    <div className="bg-surface text-on-surface">
      <TopNav active="recruiting" />

      <main className="pt-[44px]">
        <div className="max-w-[900px] mx-auto px-lg py-xl">
          <Link to="/recruitments" className="text-caption text-primary font-body-strong inline-flex items-center gap-1 mb-lg">
            <Icon name="arrow_back" className="text-[16px]" /> 모집 공고 목록으로
          </Link>

          {loading && <p className="text-caption text-ink-muted">불러오는 중...</p>}

          {error && (
            <div className="bg-surface-pearl border border-hairline rounded-2xl p-xxl text-center text-ink-muted">
              <Icon name="error_outline" className="text-[32px] block mb-sm" />
              {error}
            </div>
          )}

          {recruitment && (
            <>
              <div className="rounded-3xl overflow-hidden mb-lg flex items-center justify-center text-white h-[160px]" style={{ background: "linear-gradient(135deg,#2b5876,#4e4376)" }}>
                <Icon name="campaign" className="text-[56px] opacity-90" />
              </div>

              <span className={`inline-block text-[12px] font-bold px-md py-1 rounded-full mb-sm ${badge.cls}`}>
                {badge.label}
              </span>
              <h1 className="font-display-lg text-[26px] mb-sm">{recruitment.title}</h1>
              <p className="text-caption text-ink-muted mb-lg">
                모집 기간 {formatDateTime(recruitment.recruitmentStartAt)} – {formatDateTime(recruitment.recruitmentEndAt)}
              </p>

              {(recruitment.eventName || recruitment.eventVenueName) && (
                <div className="bg-surface-pearl border border-hairline rounded-2xl p-lg mb-lg space-y-1">
                  <h4 className="font-body-strong text-body mb-sm">행사 정보</h4>
                  {recruitment.eventType && (
                    <span className="inline-block text-[11px] font-bold px-sm py-0.5 rounded-full bg-primary-container/10 text-primary-focus mb-1">
                      {recruitment.eventType}
                    </span>
                  )}
                  {recruitment.eventName && <p className="font-body-strong text-body-strong">{recruitment.eventName}</p>}
                  {recruitment.eventShortDescription && (
                    <p className="text-caption text-secondary">{recruitment.eventShortDescription}</p>
                  )}
                  <p className="text-caption text-ink-muted">
                    {[recruitment.eventVenueName, recruitment.eventAddress].filter(Boolean).join(" · ")}
                  </p>
                  {(recruitment.eventStartAt || recruitment.eventEndAt) && (
                    <p className="text-caption text-ink-muted">
                      행사 기간 {formatDateTime(recruitment.eventStartAt)} – {formatDateTime(recruitment.eventEndAt)}
                    </p>
                  )}
                  {recruitment.eventDescription && (
                    <p className="text-caption text-secondary whitespace-pre-line mt-sm">{recruitment.eventDescription}</p>
                  )}
                </div>
              )}

              <div className="grid grid-cols-1 md:grid-cols-3 gap-lg">
                <div className="md:col-span-2 space-y-lg">
                  <div className="border-t border-hairline pt-md">
                    <div className="flex items-center justify-between gap-sm mb-sm">
                      <h4 className="font-body-strong text-body">참가 대상</h4>
                      <span
                        className={`inline-flex items-center gap-1 text-[11px] font-bold px-sm py-1 rounded-full ${
                          recruitment.businessNumberRequired
                            ? "bg-primary-container/10 text-primary-focus"
                            : "bg-surface-container-highest text-secondary"
                        }`}
                      >
                        사업자등록번호 {recruitment.businessNumberRequired ? "필수 O" : "필수 X"}
                      </span>
                    </div>
                    <p className="font-body text-caption text-secondary">{recruitment.participantTarget}</p>
                  </div>
                  {recruitment.qualification && (
                    <div className="border-t border-hairline pt-md">
                      <h4 className="font-body-strong text-body mb-sm">자격 요건</h4>
                      <p className="font-body text-caption text-secondary whitespace-pre-line">{recruitment.qualification}</p>
                    </div>
                  )}
                  {recruitment.selectionMethod && (
                    <div className="border-t border-hairline pt-md">
                      <h4 className="font-body-strong text-body mb-sm">선정 방식</h4>
                      <p className="font-body text-caption text-secondary whitespace-pre-line">{recruitment.selectionMethod}</p>
                    </div>
                  )}
                  {recruitment.notice && (
                    <div className="border-t border-hairline pt-md">
                      <h4 className="font-body-strong text-body mb-sm">안내사항</h4>
                      <p className="font-body text-caption text-secondary whitespace-pre-line">{recruitment.notice}</p>
                    </div>
                  )}
                </div>

                <div className="bg-white rounded-2xl border border-hairline shadow-sm p-lg space-y-md h-fit">
                  <h4 className="font-body-strong text-body">문의처</h4>
                  <p className="text-caption text-secondary">{recruitment.contactName}</p>
                  <p className="text-caption text-secondary">{recruitment.contactEmail}</p>
                  <p className="text-caption text-secondary">{recruitment.contactPhone}</p>
                  {/* 모집 중(OPEN)일 때만 신청 가능. 평면도·목록에서 선택한 부스가 있으면 함께 전달 */}
                  {recruitment.status === "OPEN" ? (
                    <Link
                      to={`/booth-apply?recruitmentId=${recruitmentId}${highlightedBoothId ? `&boothId=${highlightedBoothId}` : ""}`}
                      className="w-full h-[48px] bg-primary text-white rounded-xl font-body-strong flex items-center justify-center hover:brightness-95 transition"
                    >
                      부스 신청하기
                    </Link>
                  ) : (
                    <button
                      disabled
                      className="w-full h-[48px] bg-primary text-white rounded-xl font-body-strong opacity-40 cursor-not-allowed"
                    >
                      {recruitment.status === "COMPLETED" ? "모집 완료" : "모집 마감"}
                    </button>
                  )}
                </div>
              </div>

              {venueMapError && (
                <div className="border-t border-hairline mt-xl pt-lg">
                  <p className="text-caption text-error">{venueMapError}</p>
                </div>
              )}

              {venueMaps.length > 0 && (
                <div className="border-t border-hairline mt-xl pt-lg space-y-lg">
                  {venueMaps.map((venueMap) => (
                    <div key={venueMap.id}>
                      <h4 className="font-body-strong text-body mb-md">부스 배치도 · {venueMap.floorName}</h4>
                      <div className="bg-white rounded-2xl border border-hairline p-lg">
                        <VenueMapPins
                          venueMap={venueMap}
                          onPinClick={(p) => selectBoothFromPin(p.boothId)}
                          pinClassName={(p) => (p.boothId === highlightedBoothId ? "bg-error scale-125" : "bg-primary")}
                        />
                      </div>
                    </div>
                  ))}
                </div>
              )}

              <div className="border-t border-hairline mt-xl pt-lg">
                <h4 className="font-body-strong text-body mb-md">등록된 부스 목록</h4>
                {loadingBooths && <p className="text-caption text-ink-muted">부스 목록을 불러오는 중입니다.</p>}
                {boothError && <p className="text-caption text-error">{boothError}</p>}
                {!loadingBooths && !boothError && booths.length === 0 && (
                  <p className="text-caption text-ink-muted">아직 등록된 부스가 없습니다.</p>
                )}
                {!loadingBooths && booths.length > 0 && (
                  <div className="grid grid-cols-1 sm:grid-cols-2 lg:grid-cols-3 gap-md">
                    {booths.map((b) => {
                      const boothBadge = BOOTH_STATUS_BADGE[b.status] ?? { label: b.status, cls: "bg-surface-container" };
                      return (
                        <button
                          key={b.id}
                          type="button"
                          onClick={() => selectBoothFromList(b)}
                          className={`text-left bg-white border rounded-2xl p-lg transition-colors hover:border-primary-focus ${
                            b.id === highlightedBoothId ? "border-error" : "border-hairline"
                          }`}
                        >
                          <span className={`inline-block text-[11px] font-bold px-sm py-1 rounded-full mb-sm ${boothBadge.cls}`}>
                            {boothBadge.label}
                          </span>
                          <h3 className="font-body-strong text-body-strong mb-1">{b.displayName || b.boothCode}</h3>
                          <p className="text-caption text-ink-muted">
                            {[b.floorName, b.zoneName].filter(Boolean).join(" · ") || "위치 정보 없음"}
                          </p>
                          {b.shortIntro && <p className="text-caption text-ink-muted mt-1">{b.shortIntro}</p>}
                        </button>
                      );
                    })}
                  </div>
                )}
                {boothHasMore && (
                  <div className="flex justify-center mt-lg">
                    <button
                      type="button"
                      onClick={loadMoreBooths}
                      disabled={loadingMoreBooths}
                      className="px-lg py-sm border border-hairline rounded-full text-caption font-body-strong hover:bg-surface-container transition-colors disabled:opacity-40"
                    >
                      {loadingMoreBooths ? "불러오는 중..." : "부스 더 보기"}
                    </button>
                  </div>
                )}
              </div>
            </>
          )}
        </div>
      </main>

      {selectedBooth && (
        <div
          className="fixed inset-0 z-[200] bg-black/50 flex items-center justify-center p-lg"
          onClick={() => setSelectedBooth(null)}
        >
          <div
            className="bg-white rounded-2xl max-w-[560px] w-full max-h-[85vh] overflow-y-auto p-xl relative"
            onClick={(e) => e.stopPropagation()}
          >
            <button
              type="button"
              onClick={() => setSelectedBooth(null)}
              className="absolute top-lg right-lg text-ink-muted hover:text-on-surface"
            >
              <Icon name="close" className="text-[22px]" />
            </button>

            <span
              className={`inline-block text-[11px] font-bold px-sm py-1 rounded-full mb-sm ${
                (BOOTH_STATUS_BADGE[selectedBooth.status] ?? { cls: "bg-surface-container" }).cls
              }`}
            >
              {(BOOTH_STATUS_BADGE[selectedBooth.status] ?? { label: selectedBooth.status }).label}
            </span>
            <h2 className="font-display-md text-[20px] mb-1">{selectedBooth.displayName || selectedBooth.boothCode}</h2>
            <p className="text-caption text-ink-muted mb-lg">
              {selectedBooth.boothCode} · {[selectedBooth.floorName, selectedBooth.zoneName].filter(Boolean).join(" · ") || "위치 정보 없음"}
              {selectedBooth.locationDescription ? ` · ${selectedBooth.locationDescription}` : ""}
            </p>

            <div className="grid grid-cols-2 gap-sm mb-lg">
              <div className="bg-surface-pearl rounded-xl p-md">
                <p className="text-[11px] text-ink-muted mb-1">규격 (가로×세로)</p>
                <p className="text-body font-body-strong">
                  {selectedBooth.widthMeter ?? "-"}m × {selectedBooth.depthMeter ?? "-"}m
                </p>
              </div>
              <div className="bg-surface-pearl rounded-xl p-md">
                <p className="text-[11px] text-ink-muted mb-1">면적</p>
                <p className="text-body font-body-strong">{selectedBooth.areaSqm ?? "-"}㎡</p>
              </div>
              {selectedBooth.price != null && (
                <div className="bg-surface-pearl rounded-xl p-md col-span-2">
                  <p className="text-[11px] text-ink-muted mb-1">부스 비용</p>
                  <p className="text-body font-body-strong">{Number(selectedBooth.price).toLocaleString("ko-KR")}원</p>
                </div>
              )}
            </div>

            <div className="mb-lg">
              <p className="text-[12px] font-bold text-secondary mb-sm">설비 지원</p>
              <div className="flex flex-wrap gap-sm">
                {[
                  { on: selectedBooth.electricityAvailable, icon: "bolt", label: "전기" },
                  { on: selectedBooth.waterAvailable, icon: "water_drop", label: "급수" },
                  { on: selectedBooth.drainageAvailable, icon: "plumbing", label: "배수" },
                  { on: selectedBooth.internetAvailable, icon: "wifi", label: "인터넷" },
                ].map((eq) => (
                  <span
                    key={eq.label}
                    className={`inline-flex items-center gap-1 text-[12px] px-sm py-1 rounded-full border ${
                      eq.on
                        ? "border-primary-focus bg-primary-container/10 text-primary-focus font-body-strong"
                        : "border-hairline text-ink-muted opacity-60"
                    }`}
                  >
                    <Icon name={eq.icon} className="text-[14px]" /> {eq.label}
                  </span>
                ))}
              </div>
              {selectedBooth.basicEquipment?.length > 0 && (
                <p className="text-caption text-secondary mt-sm">기본 비품: {selectedBooth.basicEquipment.join(", ")}</p>
              )}
            </div>

            {selectedBooth.description && (
              <div className="border-t border-hairline pt-md mb-md">
                <h4 className="text-[12px] font-bold text-secondary mb-1">부스 소개</h4>
                <p className="text-caption text-secondary whitespace-pre-line">{selectedBooth.description}</p>
              </div>
            )}
            {selectedBooth.exhibitionContent && (
              <div className="border-t border-hairline pt-md">
                <h4 className="text-[12px] font-bold text-secondary mb-1">전시·판매 내용</h4>
                <p className="text-caption text-secondary whitespace-pre-line">{selectedBooth.exhibitionContent}</p>
              </div>
            )}
          </div>
        </div>
      )}

      <Footer />
    </div>
  );
}
