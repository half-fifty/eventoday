import { useEffect, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import NotificationBell from "../components/NotificationBell.jsx";
import { ApiError } from "../api/apiClient.js";
import { getGuideBoothDetail, addBoothInterest, removeBoothInterest } from "../api/boothApi.js";
import { listReservationSlots, getMyReservation, createReservation, cancelReservation } from "../api/boothReservationApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import useAuth from "../hooks/useAuth.js";

const formatSlotTime = (isoValue) => isoValue
  ? new Date(isoValue).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })
  : "-";

export default function BoothDetail() {
  const [params] = useSearchParams();
  const eventId = params.get("eventId");
  const boothId = params.get("boothId");
  const { isAuthenticated } = useAuth();

  const [booth, setBooth] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [togglingInterest, setTogglingInterest] = useState(false);
  const [interestError, setInterestError] = useState("");

  const [slots, setSlots] = useState([]);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [myReservation, setMyReservation] = useState(null);
  const [loadingReservation, setLoadingReservation] = useState(false);
  const [selectedSlotId, setSelectedSlotId] = useState("");
  const [partySize, setPartySize] = useState(1);
  const [reserving, setReserving] = useState(false);
  const [cancelling, setCancelling] = useState(false);
  const [reservationError, setReservationError] = useState("");
  const [reloadToken, setReloadToken] = useState(0);

  const loadReservationInfo = () => setReloadToken((value) => value + 1);

  useEffect(() => {
    if (!boothId) return undefined;

    let cancelled = false;
    setLoadingSlots(true);
    listReservationSlots(boothId)
      .then((data) => {
        if (!cancelled) setSlots(Array.isArray(data) ? data : []);
      })
      .catch(() => {
        if (!cancelled) setSlots([]);
      })
      .finally(() => {
        if (!cancelled) setLoadingSlots(false);
      });

    if (isAuthenticated) {
      setLoadingReservation(true);
      getMyReservation(boothId)
        .then((data) => {
          if (!cancelled) setMyReservation(data ?? null);
        })
        .catch(() => {
          if (!cancelled) setMyReservation(null);
        })
        .finally(() => {
          if (!cancelled) setLoadingReservation(false);
        });
    } else {
      if (!cancelled) setMyReservation(null);
    }

    return () => {
      cancelled = true;
    };
  }, [boothId, isAuthenticated, reloadToken]);

  useEffect(() => {
    let cancelled = false;
    setLoading(true);
    setError("");
    setBooth(null);

    if (!eventId || !boothId) {
      setError("잘못된 접근입니다. QR 코드를 다시 스캔해 주세요.");
      setLoading(false);
      return;
    }

    getGuideBoothDetail(eventId, boothId)
      .then((data) => { if (!cancelled) setBooth(data); })
      .catch((requestError) => {
        if (!cancelled) {
          setError(requestError instanceof ApiError && requestError.status === 404
            ? "부스 정보를 찾을 수 없습니다."
            : "부스 정보를 불러오지 못했습니다.");
        }
      })
      .finally(() => { if (!cancelled) setLoading(false); });

    return () => { cancelled = true; };
  }, [eventId, boothId]);

  const toggleInterest = async () => {
    if (!booth || togglingInterest) return;
    setTogglingInterest(true);
    setInterestError("");
    try {
      if (booth.isInterested) {
        await removeBoothInterest(boothId);
      } else {
        await addBoothInterest(boothId);
      }
      setBooth((prev) => prev && { ...prev, isInterested: !prev.isInterested });
    } catch (error) {
      setInterestError(error.message || "관심 등록 처리에 실패했습니다.");
    } finally {
      setTogglingInterest(false);
    }
  };

  const isSlotBookable = (s) => s.status === "OPEN" && s.reservedCount < s.capacity;
  const selectedSlot = slots.find((s) => s.id === Number(selectedSlotId));
  const maxPartySize = selectedSlot ? selectedSlot.capacity - selectedSlot.reservedCount : 1;

  const handleSelectSlot = (slot) => {
    setSelectedSlotId(String(slot.id));
    setPartySize(1);
    setReservationError("");
  };

  const adjustPartySize = (delta) => {
    setPartySize((prev) => {
      const next = Number(prev) + delta;
      return Math.min(Math.max(next, 1), Math.max(maxPartySize, 1));
    });
  };

  const handleReserve = async () => {
    if (!selectedSlotId || reserving) return;
    setReserving(true);
    setReservationError("");
    try {
      await createReservation(boothId, { slotId: Number(selectedSlotId), partySize: Number(partySize) });
      loadReservationInfo();
      setSelectedSlotId("");
      setPartySize(1);
    } catch (requestError) {
      setReservationError(requestError.message || "예약에 실패했습니다.");
    } finally {
      setReserving(false);
    }
  };

  const handleCancelReservation = async () => {
    if (!myReservation || cancelling) return;
    if (!window.confirm("예약을 취소하시겠어요?")) return;
    setCancelling(true);
    setReservationError("");
    try {
      await cancelReservation(boothId, myReservation.id);
      loadReservationInfo();
    } catch (requestError) {
      setReservationError(requestError.message || "예약 취소에 실패했습니다.");
    } finally {
      setCancelling(false);
    }
  };

  return (
    <div className="bg-surface-container-lowest text-on-surface min-h-screen">
      {/* Top Nav */}
      <header className="fixed top-0 w-full h-[44px] z-[100] bg-black flex justify-between items-center px-lg">
        <Link to="/" className="font-hero-display text-tagline text-white">EvenToday</Link>
        <div className="flex items-center gap-sm">
          <NotificationBell />
          <Link to={eventId ? `/events/${eventId}/ongoing` : "/"} className="text-white/80 hover:text-white text-nav-link font-nav-link flex items-center gap-1">
            <Icon name="arrow_back" className="text-[18px]" /> 행사로 돌아가기
          </Link>
        </div>
      </header>

      <main className="pt-[44px] pb-xxl">
        <div className="max-w-[1200px] mx-auto px-lg py-xl">
          {loading && <p className="py-xxl text-center text-ink-muted">부스 정보를 불러오는 중입니다.</p>}

          {!loading && error && (
            <div className="bg-white border border-hairline rounded-2xl p-xxl text-center text-ink-muted">
              <Icon name="error_outline" className="text-[32px] block mb-sm" />
              {error}
            </div>
          )}

          {!loading && !error && booth && (
            <div className="grid grid-cols-1 lg:grid-cols-12 gap-xl items-start">
              {/* Left: Info */}
              <div className="lg:col-span-7 space-y-lg">
                <div className="rounded-3xl overflow-hidden aspect-[16/10] flex items-center justify-center text-white relative bg-gradient-to-br from-primary-focus to-secondary">
                  {booth.representativeFileId ? (
                    <img
                      src={fileDownloadUrl(booth.representativeFileId)}
                      alt={booth.displayName || booth.boothCode}
                      className="w-full h-full object-cover"
                    />
                  ) : (
                    <Icon name="storefront" className="text-[72px] opacity-90" />
                  )}
                  <span className="absolute top-lg left-lg px-md py-1.5 text-caption font-bold rounded-full bg-white/90 text-primary">
                    {booth.boothCode}
                  </span>
                </div>

                {booth.shortIntro && (
                  <p className="text-body-strong text-on-surface-variant">{booth.shortIntro}</p>
                )}

                {booth.description && (
                  <div className="border-t border-hairline pt-lg">
                    <h3 className="font-body-strong text-body-strong mb-sm">부스 소개</h3>
                    <p className="text-body text-on-surface-variant leading-relaxed whitespace-pre-line">{booth.description}</p>
                  </div>
                )}

                <div className="border-t border-hairline pt-lg">
                  <h3 className="font-body-strong text-body-strong mb-sm">평균 별점</h3>
                  {booth.averageRating != null ? (
                    <div className="flex items-center gap-sm">
                      <span className="font-display-md text-[26px]">{booth.averageRating.toFixed(1)}</span>
                      <div className="flex">
                        {[1, 2, 3, 4, 5].map((i) => (
                          <Icon key={i} name="star" fill={i <= Math.round(booth.averageRating)} className={`text-[18px] ${i <= Math.round(booth.averageRating) ? "text-amber-500" : "text-hairline"}`} />
                        ))}
                      </div>
                      <span className="text-caption text-ink-muted">방문객 후기 {booth.reviewCount ?? 0}건</span>
                    </div>
                  ) : (
                    <p className="text-caption text-ink-muted">아직 등록된 후기가 없습니다.</p>
                  )}
                </div>
              </div>

              {/* Right: Summary panel */}
              <div className="lg:col-span-5">
                <div className="sticky top-[80px] bg-white rounded-2xl border border-hairline shadow-lg overflow-hidden">
                  <div className="p-xl">
                    {booth.location && (
                      <p className="flex items-center gap-1 text-caption text-primary font-body-strong mb-1">
                        <Icon name="location_on" className="text-[16px]" /> {booth.location}
                      </p>
                    )}
                    <h1 className="font-display-lg text-display-lg mb-sm leading-tight">{booth.displayName || booth.boothCode}</h1>
                    {booth.boothType && (
                      <div className="flex flex-wrap gap-xs mb-lg">
                        <span className="px-md py-1 text-caption font-body-strong rounded-full bg-surface-container text-on-surface-variant">
                          {booth.boothType}
                        </span>
                      </div>
                    )}

                    <div className={`rounded-xl p-md mb-lg flex items-center gap-sm ${booth.hasAvailableSlots ? "bg-status-available/10" : "bg-surface-container"}`}>
                      <Icon name={booth.hasAvailableSlots ? "event_available" : "event_busy"} className={booth.hasAvailableSlots ? "text-status-available" : "text-ink-muted"} />
                      <div>
                        <p className={`font-body-strong ${booth.hasAvailableSlots ? "text-status-available" : "text-ink-muted"}`}>
                          {booth.hasAvailableSlots ? "예약 가능한 시간이 있어요" : "현재 예약 가능한 시간이 없어요"}
                        </p>
                      </div>
                    </div>

                    <button
                      onClick={toggleInterest}
                      disabled={!isAuthenticated || togglingInterest}
                      title={!isAuthenticated ? "로그인 후 이용할 수 있어요" : undefined}
                      className="w-full h-[48px] rounded-xl font-body-strong border border-hairline flex items-center justify-center gap-xs disabled:opacity-40 disabled:cursor-not-allowed mb-sm"
                    >
                      {booth.isInterested ? (
                        <><Icon name="favorite" fill className="text-primary" /> 관심 등록됨</>
                      ) : (
                        <><Icon name="favorite_border" /> 관심 등록</>
                      )}
                    </button>
                    {interestError && (
                      <p className="text-caption text-error mb-sm">{interestError}</p>
                    )}

                    <div className="border-t border-hairline pt-lg">
                      <h3 className="font-body-strong text-body-strong mb-sm">부스 예약</h3>

                      {!isAuthenticated && (
                        <p className="text-caption text-ink-muted">예약은 로그인 후 이용할 수 있어요.</p>
                      )}

                      {isAuthenticated && (loadingSlots || loadingReservation) && (
                        <p className="text-caption text-ink-muted">예약 정보를 불러오는 중입니다.</p>
                      )}

                      {isAuthenticated && !loadingSlots && !loadingReservation && myReservation?.status === "RESERVED" && (
                        <div className="rounded-xl bg-status-available/10 p-md space-y-sm">
                          <p className="font-body-strong text-status-available flex items-center gap-1"><Icon name="check_circle" className="text-[18px]" /> 예약 완료</p>
                          <p className="text-caption text-on-surface-variant">
                            {(() => {
                              const reservedSlot = slots.find((s) => s.id === myReservation.slotId);
                              return reservedSlot ? `${formatSlotTime(reservedSlot.startAt)}~${formatSlotTime(reservedSlot.endAt)}` : "-";
                            })()} · {myReservation.partySize}명
                          </p>
                          <button
                            onClick={handleCancelReservation}
                            disabled={cancelling}
                            className="text-caption font-body-strong text-error disabled:opacity-40"
                          >
                            {cancelling ? "취소 중..." : "예약 취소"}
                          </button>
                        </div>
                      )}

                      {isAuthenticated && !loadingSlots && !loadingReservation && myReservation && myReservation.status !== "RESERVED" && (
                        <p className="text-caption text-ink-muted">이미 예약했던 부스라 다시 예약할 수 없어요.</p>
                      )}

                      {isAuthenticated && !loadingSlots && !loadingReservation && !myReservation && (
                        slots.length === 0 ? (
                          <p className="text-caption text-ink-muted">현재 예약 가능한 시간이 없어요.</p>
                        ) : (
                          <div className="space-y-md">
                            <div>
                              <p className="text-caption font-body-strong mb-sm">예약 시간 선택</p>
                              <div className="grid grid-cols-2 gap-sm">
                                {slots.map((s) => {
                                  const bookable = isSlotBookable(s);
                                  const selected = selectedSlotId === String(s.id);
                                  const remaining = s.capacity - s.reservedCount;
                                  return (
                                    <button
                                      key={s.id}
                                      type="button"
                                      onClick={() => bookable && handleSelectSlot(s)}
                                      disabled={!bookable}
                                      className={`h-14 rounded-lg border text-caption font-body-strong flex flex-col items-center justify-center leading-tight gap-0.5
                                        ${!bookable ? "border-hairline text-ink-muted cursor-not-allowed" : selected ? "border-primary text-primary bg-primary/5" : "border-hairline hover:border-primary/50"}`}
                                    >
                                      <span className={!bookable ? "line-through" : ""}>{formatSlotTime(s.startAt)}~{formatSlotTime(s.endAt)}</span>
                                      <span className="text-[10px] font-normal no-underline">
                                        {bookable ? `정원 ${s.capacity}명 · 잔여 ${remaining}명` : "마감"}
                                      </span>
                                    </button>
                                  );
                                })}
                              </div>
                            </div>

                            <div className="flex items-center justify-between">
                              <p className="text-caption font-body-strong">인원 수</p>
                              <div className="flex items-center gap-md">
                                <button
                                  type="button"
                                  onClick={() => adjustPartySize(-1)}
                                  disabled={!selectedSlotId || Number(partySize) <= 1}
                                  className="w-8 h-8 rounded-full border border-hairline flex items-center justify-center disabled:opacity-40"
                                >
                                  <Icon name="remove" className="text-[16px]" />
                                </button>
                                <span className="w-6 text-center font-body-strong">{partySize}</span>
                                <button
                                  type="button"
                                  onClick={() => adjustPartySize(1)}
                                  disabled={!selectedSlotId || Number(partySize) >= maxPartySize}
                                  className="w-8 h-8 rounded-full border border-hairline flex items-center justify-center disabled:opacity-40"
                                >
                                  <Icon name="add" className="text-[16px]" />
                                </button>
                              </div>
                            </div>

                            {reservationError && <p className="text-caption text-error">{reservationError}</p>}
                            <button
                              onClick={handleReserve}
                              disabled={!selectedSlotId || reserving}
                              className="w-full h-[48px] bg-primary text-white rounded-xl font-body-strong disabled:opacity-40 disabled:cursor-not-allowed"
                            >
                              {reserving ? "예약 처리 중..." : "선택한 시간으로 예약하기"}
                            </button>
                          </div>
                        )
                      )}
                    </div>
                  </div>
                </div>
              </div>
            </div>
          )}
        </div>
      </main>

      <footer className="w-full py-section bg-surface-container-low border-t border-hairline">
        <div className="max-w-[1200px] mx-auto px-lg text-center">
          <p className="text-[12px] text-ink-muted">© 2026 EvenToday. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}
