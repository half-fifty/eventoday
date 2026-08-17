import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import NotificationBell from "../components/NotificationBell.jsx";
import { ApiError } from "../api/apiClient.js";
import { getGuideBoothDetail, addBoothInterest, removeBoothInterest, updateVacancyNotification, getMyInterests } from "../api/boothApi.js";
import { listReservationSlots, getMyReservation, createReservation, cancelReservation } from "../api/boothReservationApi.js";
import { getVenueMapMarkersWithCongestion } from "../api/venueMapApi.js";
import { listReviews, createReview, updateReview, deleteReview, getMyReviews, getReviewSummary } from "../api/boothReviewApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import BoothReviewSummaryCard from "../components/BoothReviewSummaryCard.jsx";
import useAuth from "../hooks/useAuth.js";
import { congestionLevelMeta } from "../utils/congestion.js";

const REVIEW_PAGE_SIZE = 5;

const formatSlotTime = (isoValue) => isoValue
  ? new Date(isoValue).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" })
  : "-";

export default function BoothDetail() {
  const [params] = useSearchParams();
  const eventId = params.get("eventId");
  const boothId = params.get("boothId");
  const { isAuthenticated } = useAuth();

  // 매 렌더마다 최신 boothId를 반영 - 비동기 응답이 도착했을 때 그 사이 부스가 바뀌었는지
  // 판단하는 기준으로 쓴다 (요청 ID만으로는 같은 함수가 다시 호출되지 않는 한 부스 전환을 감지 못 함).
  const currentBoothIdRef = useRef(boothId);
  currentBoothIdRef.current = boothId;

  const [booth, setBooth] = useState(null);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [togglingInterest, setTogglingInterest] = useState(false);
  const [interestError, setInterestError] = useState("");

  // 빈자리 알림: 관심 등록된 부스에 한해 켜고 끌 수 있다 (null = 아직 조회 전/대상 아님).
  const [vacancyNotificationEnabled, setVacancyNotificationEnabled] = useState(null);
  const [togglingVacancyNotification, setTogglingVacancyNotification] = useState(false);
  const [vacancyNotificationError, setVacancyNotificationError] = useState("");

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

  // QR 방문 부스의 실시간 혼잡도 (평면도에 핀이 등록된 부스만 데이터가 있다).
  const [congestionInfo, setCongestionInfo] = useState(null);
  const [loadingCongestion, setLoadingCongestion] = useState(false);

  // 부스 후기 목록 (더보기 방식으로 누적)
  const [reviews, setReviews] = useState([]);
  const [reviewsPage, setReviewsPage] = useState(0);
  const [reviewsHasMore, setReviewsHasMore] = useState(false);
  const [loadingReviews, setLoadingReviews] = useState(false);
  const [reviewsError, setReviewsError] = useState("");
  const [reviewSummary, setReviewSummary] = useState(null);

  // 회원은 부스당 후기를 한 번만 작성할 수 있어, 전체 목록과 별개로 "내 후기"를 조회해
  // 작성 폼과 수정/삭제 UI를 전환하는 데 사용한다.
  const [myReviewForThisBooth, setMyReviewForThisBooth] = useState(null);
  const [reviewFormRating, setReviewFormRating] = useState(5);
  const [reviewFormComment, setReviewFormComment] = useState("");
  const [editingReview, setEditingReview] = useState(false);
  const [submittingReview, setSubmittingReview] = useState(false);
  const [reviewFormError, setReviewFormError] = useState("");
  const [deletingReview, setDeletingReview] = useState(false);

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

  // 혼잡도는 평면도 마커 API에서 이 부스의 boothId를 찾아 합성한다 (부스 단독 조회용 혼잡도 API는 없음).
  useEffect(() => {
    if (!eventId || !boothId) return undefined;
    let cancelled = false;
    setLoadingCongestion(true);
    getVenueMapMarkersWithCongestion(eventId, "VISITOR")
      .then((floors) => {
        if (cancelled) return;
        let found = null;
        (floors ?? []).forEach((floor) => {
          (floor.positions ?? []).forEach((position) => {
            if (String(position.boothId) === String(boothId)) {
              found = { congestionCount: position.congestionCount, congestionLevel: position.congestionLevel };
            }
          });
        });
        setCongestionInfo(found);
      })
      .catch(() => {
        if (!cancelled) setCongestionInfo(null);
      })
      .finally(() => {
        if (!cancelled) setLoadingCongestion(false);
      });

    return () => { cancelled = true; };
  }, [eventId, boothId]);

  // 부스를 빠르게 전환하거나 페이지를 연속으로 넘기면 응답이 요청과 다른 순서로 도착할 수 있어,
  // 매 요청마다 증가하는 id를 매겨 가장 마지막에 시작된 요청의 응답만 반영한다.
  const reviewsRequestIdRef = useRef(0);
  const loadReviews = (page) => {
    if (!boothId) return;
    const requestId = ++reviewsRequestIdRef.current;
    const requestedBoothId = boothId;
    setLoadingReviews(true);
    setReviewsError("");
    listReviews(requestedBoothId, { page, size: REVIEW_PAGE_SIZE })
      .then((data) => {
        if (reviewsRequestIdRef.current !== requestId || currentBoothIdRef.current !== requestedBoothId) return;
        setReviews((prev) => (page === 0 ? (data?.content ?? []) : [...prev, ...(data?.content ?? [])]));
        setReviewsPage(page);
        setReviewsHasMore(data ? !data.last : false);
      })
      .catch((requestError) => {
        if (reviewsRequestIdRef.current !== requestId || currentBoothIdRef.current !== requestedBoothId) return;
        setReviewsError(requestError.message || "후기를 불러오지 못했습니다.");
      })
      .finally(() => {
        if (reviewsRequestIdRef.current === requestId && currentBoothIdRef.current === requestedBoothId) setLoadingReviews(false);
      });
  };

  useEffect(() => {
    if (!boothId) return;
    setReviews([]);
    setReviewsHasMore(false);
    loadReviews(0);
    loadReviewSummary();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [boothId]);

  // 후기 코멘트 AI 요약. 리뷰 등록/수정/삭제 후에도 부스 요약(refreshBoothSummary)과 함께 다시 불러온다.
  const reviewSummaryRequestIdRef = useRef(0);
  const loadReviewSummary = () => {
    if (!boothId) return;
    const requestId = ++reviewSummaryRequestIdRef.current;
    const requestedBoothId = boothId;
    getReviewSummary(requestedBoothId)
      .then((data) => {
        if (reviewSummaryRequestIdRef.current !== requestId || currentBoothIdRef.current !== requestedBoothId) return;
        setReviewSummary(data ?? null);
      })
      .catch(() => {
        if (reviewSummaryRequestIdRef.current !== requestId || currentBoothIdRef.current !== requestedBoothId) return;
        setReviewSummary(null);
      });
  };

  const myReviewRequestIdRef = useRef(0);
  const refreshMyReview = () => {
    // 먼저 증가시켜, 로그아웃/부스 변경으로 인한 이 이른 반환 이후에도 이전에 날아간 요청이
    // 뒤늦게 도착했을 때 무효화되도록 한다.
    const requestId = ++myReviewRequestIdRef.current;
    if (!isAuthenticated || !boothId) {
      setMyReviewForThisBooth(null);
      return;
    }
    const requestedBoothId = boothId;
    // 전체 후기 목록은 페이지 단위라 내 후기가 다른 페이지에 있을 수 있어, 별도로 "내 후기 목록"에서 찾는다.
    getMyReviews({ size: 100 })
      .then((data) => {
        if (myReviewRequestIdRef.current !== requestId || currentBoothIdRef.current !== requestedBoothId) return;
        const mine = (data?.content ?? []).find((r) => String(r.boothId) === String(requestedBoothId));
        setMyReviewForThisBooth(mine ?? null);
      })
      .catch(() => {
        if (myReviewRequestIdRef.current !== requestId || currentBoothIdRef.current !== requestedBoothId) return;
        setMyReviewForThisBooth(null);
      });
  };

  useEffect(() => {
    // refreshMyReview()가 끝나기 전까지 이전 부스의 후기 편집 상태가 남아있으면, 그 사이 "수정 완료"를
    // 눌렀을 때 이전 부스의 후기 id로 새 부스에 잘못 반영될 수 있어 부스가 바뀌는 즉시 초기화한다.
    setMyReviewForThisBooth(null);
    setEditingReview(false);
    setReviewFormRating(5);
    setReviewFormComment("");
    setReviewFormError("");
    refreshMyReview();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, boothId]);

  const startEditingReview = () => {
    if (!myReviewForThisBooth) return;
    setReviewFormRating(myReviewForThisBooth.rating);
    setReviewFormComment(myReviewForThisBooth.comment ?? "");
    setReviewFormError("");
    setEditingReview(true);
  };

  const cancelEditingReview = () => {
    setEditingReview(false);
    setReviewFormError("");
  };

  // 후기 작성/수정/삭제는 부스의 평균 별점·후기 수에도 영향을 주므로 상단 요약도 함께 새로고침한다.
  const boothSummaryRequestIdRef = useRef(0);
  const refreshBoothSummary = () => {
    if (!eventId || !boothId) return;
    const requestId = ++boothSummaryRequestIdRef.current;
    const requestedBoothId = boothId;
    getGuideBoothDetail(eventId, boothId)
      .then((data) => {
        // refreshBoothSummary는 부스 전환 시 자동으로 다시 호출되지 않으므로(작성/삭제 후에만 수동 호출),
        // 요청 id뿐 아니라 그 사이 실제로 보고 있는 부스가 바뀌었는지도 함께 확인해야 한다.
        if (boothSummaryRequestIdRef.current !== requestId || currentBoothIdRef.current !== requestedBoothId) return;
        setBooth(data);
      })
      .catch(() => {});
  };

  const submitReview = async () => {
    if (submittingReview) return;
    const requestedBoothId = boothId;
    setSubmittingReview(true);
    setReviewFormError("");
    try {
      if (myReviewForThisBooth) {
        await updateReview(boothId, myReviewForThisBooth.id, { content: reviewFormComment, rating: reviewFormRating });
      } else {
        await createReview(boothId, { rating: reviewFormRating, comment: reviewFormComment });
      }
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setEditingReview(false);
      setReviewFormComment("");
      setReviewFormRating(5);
      refreshMyReview();
      refreshBoothSummary();
      loadReviews(0);
      loadReviewSummary();
    } catch (requestError) {
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReviewFormError(requestError.message || "후기 등록에 실패했습니다.");
    } finally {
      if (currentBoothIdRef.current === requestedBoothId) setSubmittingReview(false);
    }
  };

  const handleDeleteReview = async () => {
    if (!myReviewForThisBooth || deletingReview) return;
    if (!window.confirm("후기를 삭제하시겠어요?")) return;
    const requestedBoothId = boothId;
    setDeletingReview(true);
    try {
      await deleteReview(boothId, myReviewForThisBooth.id);
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setMyReviewForThisBooth(null);
      refreshBoothSummary();
      loadReviews(0);
      loadReviewSummary();
    } catch (requestError) {
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReviewFormError(requestError.message || "후기 삭제에 실패했습니다.");
    } finally {
      if (currentBoothIdRef.current === requestedBoothId) setDeletingReview(false);
    }
  };

  const toggleInterest = async () => {
    if (!booth || togglingInterest) return;
    const requestedBoothId = boothId;
    setTogglingInterest(true);
    setInterestError("");
    try {
      if (booth.isInterested) {
        await removeBoothInterest(boothId);
      } else {
        await addBoothInterest(boothId);
      }
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setBooth((prev) => prev && { ...prev, isInterested: !prev.isInterested });
    } catch (error) {
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setInterestError(error.message || "관심 등록 처리에 실패했습니다.");
    } finally {
      if (currentBoothIdRef.current === requestedBoothId) setTogglingInterest(false);
    }
  };

  // 관심 등록된 부스만 빈자리 알림을 설정할 수 있어, 관심 상태가 바뀔 때마다 현재 알림 수신 여부를 다시 조회한다.
  useEffect(() => {
    if (!isAuthenticated || !boothId || !booth?.isInterested) {
      setVacancyNotificationEnabled(null);
      return undefined;
    }
    let cancelled = false;
    getMyInterests()
      .then((data) => {
        if (cancelled) return;
        const mine = (Array.isArray(data) ? data : []).find((i) => String(i.boothId) === String(boothId));
        setVacancyNotificationEnabled(mine?.vacancyNotificationEnabled ?? false);
      })
      .catch(() => {
        if (!cancelled) setVacancyNotificationEnabled(false);
      });
    return () => { cancelled = true; };
  }, [isAuthenticated, boothId, booth?.isInterested]);

  const toggleVacancyNotification = async () => {
    if (togglingVacancyNotification || vacancyNotificationEnabled == null) return;
    const requestedBoothId = boothId;
    setTogglingVacancyNotification(true);
    setVacancyNotificationError("");
    const next = !vacancyNotificationEnabled;
    try {
      await updateVacancyNotification(boothId, next);
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setVacancyNotificationEnabled(next);
    } catch (error) {
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setVacancyNotificationError(error.message || "빈자리 알림 설정에 실패했습니다.");
    } finally {
      if (currentBoothIdRef.current === requestedBoothId) setTogglingVacancyNotification(false);
    }
  };

  // 알 수 없는 congestionLevel 값이면 congestionLevelMeta가 null을 반환할 수 있어,
  // 뱃지를 그리기 전에 먼저 확인해 "정보 없음" 상태로 안전하게 대체한다.
  const congestionMeta = congestionInfo ? congestionLevelMeta(congestionInfo.congestionLevel) : null;

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
    const requestedBoothId = boothId;
    setReserving(true);
    setReservationError("");
    try {
      await createReservation(boothId, { slotId: Number(selectedSlotId), partySize: Number(partySize) });
      if (currentBoothIdRef.current !== requestedBoothId) return;
      loadReservationInfo();
      setSelectedSlotId("");
      setPartySize(1);
    } catch (requestError) {
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReservationError(requestError.message || "예약에 실패했습니다.");
    } finally {
      if (currentBoothIdRef.current === requestedBoothId) setReserving(false);
    }
  };

  const handleCancelReservation = async () => {
    if (!myReservation || cancelling) return;
    if (!window.confirm("예약을 취소하시겠어요?")) return;
    const requestedBoothId = boothId;
    setCancelling(true);
    setReservationError("");
    try {
      await cancelReservation(boothId, myReservation.id);
      if (currentBoothIdRef.current !== requestedBoothId) return;
      loadReservationInfo();
    } catch (requestError) {
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReservationError(requestError.message || "예약 취소에 실패했습니다.");
    } finally {
      if (currentBoothIdRef.current === requestedBoothId) setCancelling(false);
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

                <div className="border-t border-hairline pt-lg">
                  <h3 className="font-body-strong text-body-strong mb-sm">실시간 혼잡도</h3>
                  {loadingCongestion ? (
                    <p className="text-caption text-ink-muted">혼잡도 정보를 불러오는 중입니다.</p>
                  ) : congestionInfo && congestionMeta ? (
                    <div className="flex items-center gap-sm">
                      <span className={`px-md py-1 text-caption font-bold rounded-full bg-${congestionMeta.colorClass}/10 text-${congestionMeta.colorClass}`}>
                        {congestionMeta.label}
                      </span>
                      <span className="text-caption text-ink-muted">최근 10분 방문 {congestionInfo.congestionCount}명</span>
                    </div>
                  ) : (
                    <p className="text-caption text-ink-muted">아직 집계된 혼잡도 데이터가 없어요.</p>
                  )}
                </div>

                <div className="border-t border-hairline pt-lg">
                  <h3 className="font-body-strong text-body-strong mb-md">방문객 후기</h3>

                  <BoothReviewSummaryCard summary={reviewSummary} />

                  {!isAuthenticated ? (
                    <p className="text-caption text-ink-muted mb-lg">로그인 후 후기를 남길 수 있어요.</p>
                  ) : myReviewForThisBooth && !editingReview ? (
                    <div className="bg-surface-container-low rounded-xl p-md mb-lg">
                      <div className="flex items-center justify-between mb-1">
                        <span className="text-caption font-bold text-primary">내가 남긴 후기</span>
                        <div className="flex gap-sm">
                          <button onClick={startEditingReview} className="text-caption text-primary font-body-strong">수정</button>
                          <button onClick={handleDeleteReview} disabled={deletingReview} className="text-caption text-error font-body-strong disabled:opacity-40">
                            {deletingReview ? "삭제 중..." : "삭제"}
                          </button>
                        </div>
                      </div>
                      <div className="flex mb-1">
                        {[1, 2, 3, 4, 5].map((i) => (
                          <Icon key={i} name="star" fill={i <= myReviewForThisBooth.rating} className={`text-[16px] ${i <= myReviewForThisBooth.rating ? "text-amber-500" : "text-hairline"}`} />
                        ))}
                      </div>
                      {myReviewForThisBooth.comment && (
                        <p className="text-caption text-on-surface-variant">{myReviewForThisBooth.comment}</p>
                      )}
                      {reviewFormError && <p className="text-caption text-error mt-sm">{reviewFormError}</p>}
                    </div>
                  ) : (
                    <div className="bg-surface-container-low rounded-xl p-md mb-lg space-y-sm">
                      <div className="flex gap-1">
                        {[1, 2, 3, 4, 5].map((i) => (
                          <button key={i} type="button" onClick={() => setReviewFormRating(i)}>
                            <Icon name="star" fill={i <= reviewFormRating} className={`text-[22px] ${i <= reviewFormRating ? "text-amber-500" : "text-hairline"}`} />
                          </button>
                        ))}
                      </div>
                      <textarea
                        value={reviewFormComment}
                        onChange={(e) => setReviewFormComment(e.target.value)}
                        placeholder="부스는 어떠셨나요? (선택)"
                        rows={3}
                        maxLength={300}
                        className="w-full rounded-lg border border-hairline px-sm py-2 text-caption outline-none focus:border-primary-focus resize-none"
                      />
                      {reviewFormError && <p className="text-caption text-error">{reviewFormError}</p>}
                      <div className="flex gap-sm">
                        <button
                          onClick={submitReview}
                          disabled={submittingReview}
                          className="h-[36px] px-lg rounded-full bg-primary text-white text-caption font-body-strong disabled:opacity-40"
                        >
                          {submittingReview ? "등록 중..." : editingReview ? "수정 완료" : "후기 등록"}
                        </button>
                        {editingReview && (
                          <button onClick={cancelEditingReview} className="h-[36px] px-lg rounded-full border border-hairline text-caption font-body-strong">
                            취소
                          </button>
                        )}
                      </div>
                    </div>
                  )}

                  {loadingReviews && reviews.length === 0 && <p className="text-caption text-ink-muted">후기를 불러오는 중입니다.</p>}
                  {reviewsError && <p className="text-caption text-error">{reviewsError}</p>}
                  {!loadingReviews && !reviewsError && reviews.length === 0 && (
                    <p className="text-caption text-ink-muted">아직 등록된 후기가 없어요.</p>
                  )}
                  {reviews.length > 0 && (
                    <div className="divide-y divide-divider-soft">
                      {reviews.map((review) => (
                        <div key={review.id} className="py-sm">
                          <div className="flex items-center justify-between mb-1">
                            <div className="flex items-center gap-sm">
                              <span className="text-caption font-body-strong">{review.memberName}</span>
                              {myReviewForThisBooth?.id === review.id && (
                                <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-full bg-primary-container/10 text-primary-focus">내 후기</span>
                              )}
                            </div>
                            <span className="text-[11px] text-ink-muted">
                              {review.createdAt ? new Date(review.createdAt).toLocaleDateString("ko-KR") : ""}
                            </span>
                          </div>
                          <div className="flex mb-1">
                            {[1, 2, 3, 4, 5].map((i) => (
                              <Icon key={i} name="star" fill={i <= review.rating} className={`text-[14px] ${i <= review.rating ? "text-amber-500" : "text-hairline"}`} />
                            ))}
                          </div>
                          {review.comment && <p className="text-caption text-on-surface-variant">{review.comment}</p>}
                        </div>
                      ))}
                    </div>
                  )}
                  {reviewsHasMore && (
                    <button
                      onClick={() => loadReviews(reviewsPage + 1)}
                      disabled={loadingReviews}
                      className="w-full mt-sm h-[36px] rounded-full border border-hairline text-caption font-body-strong disabled:opacity-40"
                    >
                      {loadingReviews ? "불러오는 중..." : "후기 더보기"}
                    </button>
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

                    {booth.isInterested && (
                      <button
                        onClick={toggleVacancyNotification}
                        disabled={togglingVacancyNotification || vacancyNotificationEnabled == null}
                        className="w-full h-[44px] rounded-xl font-body-strong border border-hairline flex items-center justify-center gap-xs disabled:opacity-40 disabled:cursor-not-allowed mb-sm"
                      >
                        {vacancyNotificationEnabled ? (
                          <><Icon name="notifications_active" fill className="text-primary" /> 빈자리 알림 받는 중</>
                        ) : (
                          <><Icon name="notifications_off" /> 빈자리 알림 받기</>
                        )}
                      </button>
                    )}
                    {vacancyNotificationError && (
                      <p className="text-caption text-error mb-sm">{vacancyNotificationError}</p>
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
