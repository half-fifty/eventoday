import { useEffect, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import NotificationBell from "../components/NotificationBell.jsx";
import { ApiError } from "../api/apiClient.js";
import { getGuideBoothDetail, addBoothInterest, removeBoothInterest, updateVacancyNotification, getMyInterests } from "../api/boothApi.js";
import { listReservationSlots, getMyReservation, createReservation, cancelReservation, checkInBooth } from "../api/boothReservationApi.js";
import { admissionApi } from "../api/admissionApi.js";
import { getVenueMapMarkersWithCongestion } from "../api/venueMapApi.js";
import {
  listReviews, createReview, updateReview, deleteReview, getMyReviews, getReviewSummary,
  searchReviews, reportReview, cancelReport, markHelpful, unmarkHelpful,
} from "../api/boothReviewApi.js";
import { fileDownloadUrl, uploadFile } from "../api/fileApi.js";
import BoothReviewSummaryCard from "../components/BoothReviewSummaryCard.jsx";
import useAuth from "../hooks/useAuth.js";
import { congestionLevelMeta } from "../utils/congestion.js";

const REVIEW_PAGE_SIZE = 5;
const REVIEW_PHOTO_MAX = 5;

const REPORT_REASON_OPTIONS = [
  { value: "SPAM", label: "광고/도배" },
  { value: "ABUSE", label: "욕설/혐오 표현" },
  { value: "HARASSMENT", label: "특정인 비방/괴롭힘" },
  { value: "FALSE_INFORMATION", label: "허위 정보" },
  { value: "OTHER", label: "기타" },
];

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

  // 부스 체크인: 이 행사에 대해 게이트에서 이미 입장 처리(USED)된 내 입장권 — 있어야 체크인 버튼을 보여준다.
  const [myAdmissionTicket, setMyAdmissionTicket] = useState(null);
  const [loadingAdmissionTicket, setLoadingAdmissionTicket] = useState(false);
  const [checkingIn, setCheckingIn] = useState(false);
  const [checkInError, setCheckInError] = useState("");
  const [checkInSuccess, setCheckInSuccess] = useState(false);

  // 부스 후기 목록 (더보기 방식으로 누적)
  const [reviews, setReviews] = useState([]);
  const [reviewsPage, setReviewsPage] = useState(0);
  const [reviewsHasMore, setReviewsHasMore] = useState(false);
  const [loadingReviews, setLoadingReviews] = useState(false);
  const [reviewsError, setReviewsError] = useState("");
  const [reviewSummary, setReviewSummary] = useState(null);

  // 정렬/검색 — 검색어가 있으면 searchReviews를, 없으면 listReviews(sortBy)를 사용한다.
  const [reviewSort, setReviewSort] = useState("LATEST");
  const [reviewKeywordInput, setReviewKeywordInput] = useState("");
  const [reviewKeyword, setReviewKeyword] = useState("");

  // 회원은 부스당 후기를 한 번만 작성할 수 있어, 전체 목록과 별개로 "내 후기"를 조회해
  // 작성 폼과 수정/삭제 UI를 전환하는 데 사용한다.
  const [myReviewForThisBooth, setMyReviewForThisBooth] = useState(null);
  const [reviewFormRating, setReviewFormRating] = useState(5);
  const [reviewFormComment, setReviewFormComment] = useState("");
  const [reviewFormPhotos, setReviewFormPhotos] = useState([]); // [{ fileId, previewUrl }]
  const [uploadingPhoto, setUploadingPhoto] = useState(false);
  // 사진 업로드는 비동기라, 업로드 도중 편집을 취소하거나 다른 부스로 이동하면 늦게 도착한 응답이
  // 이미 리셋되었거나 다른 부스의 폼에 잘못 append될 수 있다. 폼이 리셋될 때마다 이 값을 올려서,
  // 업로드 완료 시점에 "그 폼이 아직 그대로인지"를 boothId와 함께 확인한다.
  const reviewFormSessionRef = useRef(0);
  const [editingReview, setEditingReview] = useState(false);
  const [submittingReview, setSubmittingReview] = useState(false);
  const [reviewFormError, setReviewFormError] = useState("");
  const [deletingReview, setDeletingReview] = useState(false);

  // 리뷰 신고 — 신고 폼을 펼친 리뷰 id, 선택한 사유, 신고 처리 중인 리뷰 id를 각각 추적한다.
  const [reportingReviewId, setReportingReviewId] = useState(null);
  const [reportReasonCode, setReportReasonCode] = useState("SPAM");
  const [reportReasonText, setReportReasonText] = useState("");
  const [reportError, setReportError] = useState("");
  const [reportSubmittingId, setReportSubmittingId] = useState(null);
  const [cancelingReportId, setCancelingReportId] = useState(null);

  // "도움이 돼요" 등록/취소 처리 중인 리뷰 id
  const [helpfulSubmittingId, setHelpfulSubmittingId] = useState(null);

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

  // 이 행사에 대해 게이트에서 이미 입장 처리(USED)된 내 입장권을 조회한다 — 체크인 버튼 노출 여부 판단용.
  // (한 회원이 같은 행사 입장권을 여러 장 갖고 있을 수 있어 그중 하나만 찾으면 된다)
  useEffect(() => {
    if (!isAuthenticated || !eventId) {
      setMyAdmissionTicket(null);
      return undefined;
    }
    let cancelled = false;
    setLoadingAdmissionTicket(true);
    admissionApi.getMyAdmissionTickets({ status: "USED", size: 50 })
      .then((data) => {
        if (cancelled) return;
        const content = data?.content ?? [];
        const mine = content.find((t) => String(t.eventId) === String(eventId));
        setMyAdmissionTicket(mine ?? null);
      })
      .catch(() => {
        if (!cancelled) setMyAdmissionTicket(null);
      })
      .finally(() => {
        if (!cancelled) setLoadingAdmissionTicket(false);
      });
    return () => { cancelled = true; };
  }, [isAuthenticated, eventId]);

  // 부스가 바뀌면 이전 부스의 체크인 결과 표시를 초기화한다.
  useEffect(() => {
    setCheckInSuccess(false);
    setCheckInError("");
  }, [boothId]);

  const handleCheckIn = async () => {
    if (!myAdmissionTicket || checkingIn) return;
    const requestedBoothId = boothId;
    setCheckingIn(true);
    setCheckInError("");
    try {
      await checkInBooth(boothId, myAdmissionTicket.admissionTicketId);
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setCheckInSuccess(true);
    } catch (requestError) {
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setCheckInError(requestError.message || "체크인에 실패했습니다.");
    } finally {
      if (currentBoothIdRef.current === requestedBoothId) setCheckingIn(false);
    }
  };

  // 부스를 빠르게 전환하거나 페이지를 연속으로 넘기면 응답이 요청과 다른 순서로 도착할 수 있어,
  // 매 요청마다 증가하는 id를 매겨 가장 마지막에 시작된 요청의 응답만 반영한다.
  const reviewsRequestIdRef = useRef(0);
  const loadReviews = (page, overrides = {}) => {
    if (!boothId) return;
    const sort = overrides.sort ?? reviewSort;
    const keyword = overrides.keyword !== undefined ? overrides.keyword : reviewKeyword;
    const requestId = ++reviewsRequestIdRef.current;
    const requestedBoothId = boothId;
    setLoadingReviews(true);
    setReviewsError("");
    const request = keyword
      ? searchReviews(requestedBoothId, keyword, { page, size: REVIEW_PAGE_SIZE })
      : listReviews(requestedBoothId, { page, size: REVIEW_PAGE_SIZE, sortBy: sort });
    request
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

  const handleSortChange = (nextSort) => {
    setReviewSort(nextSort);
    loadReviews(0, { sort: nextSort });
  };

  const handleSearchSubmit = (e) => {
    e.preventDefault();
    const keyword = reviewKeywordInput.trim();
    setReviewKeyword(keyword);
    loadReviews(0, { keyword });
  };

  const clearSearch = () => {
    setReviewKeywordInput("");
    setReviewKeyword("");
    loadReviews(0, { keyword: "" });
  };

  useEffect(() => {
    if (!boothId) return;
    setReviews([]);
    setReviewsHasMore(false);
    setReviewSummary(null);
    setReviewSort("LATEST");
    setReviewKeywordInput("");
    setReviewKeyword("");
    loadReviews(0, { sort: "LATEST", keyword: "" });
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
    reviewFormSessionRef.current += 1;
    setMyReviewForThisBooth(null);
    setEditingReview(false);
    setReviewFormRating(5);
    setReviewFormComment("");
    setReviewFormPhotos([]);
    setReviewFormError("");
    refreshMyReview();
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [isAuthenticated, boothId]);

  const startEditingReview = () => {
    if (!myReviewForThisBooth) return;
    reviewFormSessionRef.current += 1;
    setReviewFormRating(myReviewForThisBooth.rating);
    setReviewFormComment(myReviewForThisBooth.comment ?? "");
    setReviewFormPhotos(
      (myReviewForThisBooth.photos ?? []).map((p) => ({ fileId: p.fileId, previewUrl: fileDownloadUrl(p.fileId) }))
    );
    setReviewFormError("");
    setEditingReview(true);
  };

  const cancelEditingReview = () => {
    reviewFormSessionRef.current += 1;
    setEditingReview(false);
    setReviewFormPhotos([]);
    setReviewFormError("");
  };

  const handlePhotoSelect = async (e) => {
    const files = Array.from(e.target.files ?? []);
    e.target.value = "";
    if (files.length === 0) return;
    if (reviewFormPhotos.length + files.length > REVIEW_PHOTO_MAX) {
      setReviewFormError(`사진은 최대 ${REVIEW_PHOTO_MAX}장까지 첨부할 수 있어요.`);
      return;
    }
    const requestedBoothId = boothId;
    const session = reviewFormSessionRef.current;
    setUploadingPhoto(true);
    setReviewFormError("");
    try {
      for (const file of files) {
        const uploaded = await uploadFile(file, "PUBLIC");
        if (currentBoothIdRef.current !== requestedBoothId || reviewFormSessionRef.current !== session) continue;
        setReviewFormPhotos((prev) => [...prev, { fileId: uploaded.fileId, previewUrl: fileDownloadUrl(uploaded.fileId) }]);
      }
    } catch (requestError) {
      if (currentBoothIdRef.current === requestedBoothId && reviewFormSessionRef.current === session) {
        setReviewFormError(requestError.message || "사진 업로드에 실패했습니다.");
      }
    } finally {
      if (currentBoothIdRef.current === requestedBoothId && reviewFormSessionRef.current === session) {
        setUploadingPhoto(false);
      }
    }
  };

  const removePhoto = (fileId) => {
    setReviewFormPhotos((prev) => prev.filter((p) => p.fileId !== fileId));
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
    const fileIds = reviewFormPhotos.map((p) => p.fileId);
    try {
      if (myReviewForThisBooth) {
        await updateReview(boothId, myReviewForThisBooth.id, { content: reviewFormComment, rating: reviewFormRating, fileIds });
      } else {
        await createReview(boothId, { rating: reviewFormRating, comment: reviewFormComment, fileIds });
      }
      if (currentBoothIdRef.current !== requestedBoothId) return;
      reviewFormSessionRef.current += 1;
      setEditingReview(false);
      setReviewFormComment("");
      setReviewFormRating(5);
      setReviewFormPhotos([]);
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

  const openReportForm = (reviewId) => {
    setReportingReviewId(reviewId);
    setReportReasonCode("SPAM");
    setReportReasonText("");
    setReportError("");
  };

  const closeReportForm = () => {
    setReportingReviewId(null);
    setReportError("");
  };

  const submitReport = async (reviewId) => {
    if (reportSubmittingId) return;
    if (reportReasonCode === "OTHER" && !reportReasonText.trim()) {
      setReportError("기타 사유를 선택한 경우 상세 사유를 입력해주세요.");
      return;
    }
    const requestedBoothId = boothId;
    setReportSubmittingId(reviewId);
    setReportError("");
    try {
      await reportReview(boothId, reviewId, { reasonCode: reportReasonCode, reason: reportReasonText.trim() || undefined });
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReportingReviewId(null);
      setReviews((prev) => prev.map((r) => (r.id === reviewId ? { ...r, reportedByMe: true } : r)));
      loadReviews(0);
    } catch (requestError) {
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReportError(requestError.message || "신고 접수에 실패했습니다.");
    } finally {
      if (currentBoothIdRef.current === requestedBoothId) setReportSubmittingId(null);
    }
  };

  const handleCancelReport = async (reviewId) => {
    if (cancelingReportId) return;
    const requestedBoothId = boothId;
    setCancelingReportId(reviewId);
    try {
      await cancelReport(boothId, reviewId);
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReviews((prev) => prev.map((r) => (r.id === reviewId ? { ...r, reportedByMe: false } : r)));
    } catch (requestError) {
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReviewsError(requestError.message || "신고 취소에 실패했습니다.");
    } finally {
      if (currentBoothIdRef.current === requestedBoothId) setCancelingReportId(null);
    }
  };

  const handleMarkHelpful = async (reviewId) => {
    if (helpfulSubmittingId) return;
    const requestedBoothId = boothId;
    setHelpfulSubmittingId(reviewId);
    try {
      await markHelpful(boothId, reviewId);
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReviews((prev) => prev.map((r) => (r.id === reviewId
        ? { ...r, helpfulByMe: true, helpfulCount: r.helpfulCount + 1, trustedReview: r.trustedReview || r.helpfulCount + 1 >= 3 }
        : r)));
    } catch (requestError) {
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReviewsError(requestError.message || "도움이 돼요 등록에 실패했습니다.");
    } finally {
      if (currentBoothIdRef.current === requestedBoothId) setHelpfulSubmittingId(null);
    }
  };

  const handleUnmarkHelpful = async (reviewId) => {
    if (helpfulSubmittingId) return;
    const requestedBoothId = boothId;
    setHelpfulSubmittingId(reviewId);
    try {
      await unmarkHelpful(boothId, reviewId);
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReviews((prev) => prev.map((r) => (r.id === reviewId
        ? { ...r, helpfulByMe: false, helpfulCount: Math.max(0, r.helpfulCount - 1), trustedReview: r.helpfulCount - 1 >= 3 }
        : r)));
    } catch (requestError) {
      if (currentBoothIdRef.current !== requestedBoothId) return;
      setReviewsError(requestError.message || "도움이 돼요 취소에 실패했습니다.");
    } finally {
      if (currentBoothIdRef.current === requestedBoothId) setHelpfulSubmittingId(null);
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
                      {myReviewForThisBooth.photos?.length > 0 && (
                        <div className="flex gap-xs mt-sm">
                          {myReviewForThisBooth.photos.map((p) => (
                            <img key={p.fileId} src={fileDownloadUrl(p.fileId)} alt="후기 사진" className="w-14 h-14 rounded-lg object-cover border border-hairline" />
                          ))}
                        </div>
                      )}
                      {myReviewForThisBooth.hidden && (
                        <p className="text-[11px] text-error mt-sm">
                          부스 담당자 조치로 비공개 처리된 후기라 다른 방문객에게는 보이지 않아요.
                        </p>
                      )}
                      {myReviewForThisBooth.reply && (
                        <div className="mt-sm bg-white rounded-lg p-sm border border-hairline">
                          <span className="text-[11px] font-bold text-primary">부스 담당자 답글</span>
                          <p className="text-caption text-on-surface-variant mt-0.5">{myReviewForThisBooth.reply.content}</p>
                        </div>
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
                      <div className="flex flex-wrap gap-xs">
                        {reviewFormPhotos.map((p) => (
                          <div key={p.fileId} className="relative w-14 h-14">
                            <img src={p.previewUrl} alt="첨부 사진" className="w-14 h-14 rounded-lg object-cover border border-hairline" />
                            <button
                              type="button"
                              onClick={() => removePhoto(p.fileId)}
                              className="absolute -top-1.5 -right-1.5 w-5 h-5 rounded-full bg-black/70 text-white flex items-center justify-center"
                            >
                              <Icon name="close" className="text-[12px]" />
                            </button>
                          </div>
                        ))}
                        {reviewFormPhotos.length < REVIEW_PHOTO_MAX && (
                          <label className="w-14 h-14 rounded-lg border border-dashed border-hairline flex items-center justify-center cursor-pointer text-ink-muted">
                            {uploadingPhoto ? (
                              <span className="text-[10px]">업로드 중</span>
                            ) : (
                              <Icon name="add_a_photo" className="text-[18px]" />
                            )}
                            <input type="file" accept="image/*" multiple className="hidden" onChange={handlePhotoSelect} disabled={uploadingPhoto} />
                          </label>
                        )}
                      </div>
                      {reviewFormError && <p className="text-caption text-error">{reviewFormError}</p>}
                      <div className="flex gap-sm">
                        <button
                          onClick={submitReview}
                          disabled={submittingReview || uploadingPhoto}
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

                  <form onSubmit={handleSearchSubmit} className="flex items-center gap-sm mb-sm">
                    <div className="flex-1 flex items-center gap-1 rounded-full border border-hairline px-sm h-[34px]">
                      <Icon name="search" className="text-[16px] text-ink-muted" />
                      <input
                        value={reviewKeywordInput}
                        onChange={(e) => setReviewKeywordInput(e.target.value)}
                        placeholder="후기 검색"
                        className="flex-1 text-caption outline-none bg-transparent"
                      />
                      {reviewKeyword && (
                        <button type="button" onClick={clearSearch} className="text-ink-muted">
                          <Icon name="close" className="text-[14px]" />
                        </button>
                      )}
                    </div>
                    <select
                      value={reviewSort}
                      onChange={(e) => handleSortChange(e.target.value)}
                      disabled={!!reviewKeyword}
                      title={reviewKeyword ? "검색 결과는 관련도순으로 표시돼요" : undefined}
                      className="h-[34px] rounded-full border border-hairline px-sm text-caption bg-white disabled:opacity-40"
                    >
                      <option value="LATEST">최신순</option>
                      <option value="RATING_DESC">별점 높은순</option>
                      <option value="RATING_ASC">별점 낮은순</option>
                      <option value="HELPFUL_DESC">도움순</option>
                    </select>
                  </form>

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
                              {review.mine && (
                                <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-full bg-primary-container/10 text-primary-focus">내 후기</span>
                              )}
                              {review.trustedReview && (
                                <span className="text-[10px] font-bold px-1.5 py-0.5 rounded-full bg-amber-100 text-amber-700">믿을 수 있는 리뷰</span>
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
                          {review.photos?.length > 0 && (
                            <div className="flex gap-xs mt-sm">
                              {review.photos.map((p) => (
                                <img key={p.fileId} src={fileDownloadUrl(p.fileId)} alt="후기 사진" className="w-14 h-14 rounded-lg object-cover border border-hairline" />
                              ))}
                            </div>
                          )}
                          {review.reply && (
                            <div className="mt-sm bg-surface-container-low rounded-lg p-sm">
                              <span className="text-[11px] font-bold text-primary">부스 담당자 답글</span>
                              <p className="text-caption text-on-surface-variant mt-0.5">{review.reply.content}</p>
                            </div>
                          )}

                          <div className="mt-1 flex items-center gap-sm">
                            {!review.mine && isAuthenticated ? (
                              <button
                                onClick={() => (review.helpfulByMe ? handleUnmarkHelpful(review.id) : handleMarkHelpful(review.id))}
                                disabled={helpfulSubmittingId === review.id}
                                className={`text-[11px] flex items-center gap-0.5 disabled:opacity-40 ${review.helpfulByMe ? "text-primary font-body-strong" : "text-ink-muted"}`}
                              >
                                <Icon name="thumb_up" fill={review.helpfulByMe} className="text-[13px]" />
                                도움이 돼요{review.helpfulCount > 0 ? ` ${review.helpfulCount}` : ""}
                              </button>
                            ) : (
                              review.helpfulCount > 0 && (
                                <span className="text-[11px] text-ink-muted flex items-center gap-0.5">
                                  <Icon name="thumb_up" className="text-[13px]" />
                                  도움이 돼요 {review.helpfulCount}
                                </span>
                              )
                            )}
                          </div>

                          {!review.mine && isAuthenticated && (
                            <div className="mt-1">
                              {review.reportedByMe ? (
                                <button
                                  onClick={() => handleCancelReport(review.id)}
                                  disabled={cancelingReportId === review.id}
                                  className="text-[11px] text-ink-muted underline disabled:opacity-40"
                                >
                                  {cancelingReportId === review.id ? "취소 중..." : "신고 취소하기 (신고완료)"}
                                </button>
                              ) : reportingReviewId === review.id ? (
                                <div className="mt-sm bg-surface-container-low rounded-lg p-sm space-y-sm">
                                  <select
                                    value={reportReasonCode}
                                    onChange={(e) => setReportReasonCode(e.target.value)}
                                    className="w-full h-[32px] rounded-lg border border-hairline px-sm text-caption bg-white"
                                  >
                                    {REPORT_REASON_OPTIONS.map((opt) => (
                                      <option key={opt.value} value={opt.value}>{opt.label}</option>
                                    ))}
                                  </select>
                                  {reportReasonCode === "OTHER" && (
                                    <textarea
                                      value={reportReasonText}
                                      onChange={(e) => setReportReasonText(e.target.value)}
                                      placeholder="상세 사유를 입력해주세요"
                                      rows={2}
                                      maxLength={200}
                                      className="w-full rounded-lg border border-hairline px-sm py-1.5 text-caption outline-none resize-none"
                                    />
                                  )}
                                  {reportError && <p className="text-[11px] text-error">{reportError}</p>}
                                  <div className="flex gap-sm">
                                    <button
                                      onClick={() => submitReport(review.id)}
                                      disabled={reportSubmittingId === review.id}
                                      className="h-[28px] px-md rounded-full bg-error text-white text-[11px] font-body-strong disabled:opacity-40"
                                    >
                                      {reportSubmittingId === review.id ? "신고 중..." : "신고하기"}
                                    </button>
                                    <button onClick={closeReportForm} className="h-[28px] px-md rounded-full border border-hairline text-[11px] font-body-strong">
                                      취소
                                    </button>
                                  </div>
                                </div>
                              ) : (
                                <button onClick={() => openReportForm(review.id)} className="text-[11px] text-ink-muted underline">
                                  신고
                                </button>
                              )}
                            </div>
                          )}
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

                    <div className="border-t border-hairline pt-lg mb-lg">
                      <h3 className="font-body-strong text-body-strong mb-sm">부스 체크인</h3>
                      {!isAuthenticated ? (
                        <p className="text-caption text-ink-muted">체크인은 로그인 후 이용할 수 있어요.</p>
                      ) : loadingAdmissionTicket ? (
                        <p className="text-caption text-ink-muted">입장권 정보를 확인하는 중입니다.</p>
                      ) : !myAdmissionTicket ? (
                        <p className="text-caption text-ink-muted">아직 이 행사에 입장 처리되지 않았어요. 게이트에서 먼저 입장해주세요.</p>
                      ) : checkInSuccess ? (
                        <p className="font-body-strong text-status-available flex items-center gap-1">
                          <Icon name="check_circle" className="text-[18px]" /> 체크인 완료! 혼잡도 집계에 반영돼요.
                        </p>
                      ) : (
                        <>
                          <button
                            onClick={handleCheckIn}
                            disabled={checkingIn}
                            className="w-full h-[44px] rounded-xl font-body-strong bg-primary text-white flex items-center justify-center gap-xs disabled:opacity-40 disabled:cursor-not-allowed"
                          >
                            <Icon name="qr_code_scanner" className="text-[18px]" />
                            {checkingIn ? "체크인 처리 중..." : "이 부스 체크인하기"}
                          </button>
                          {checkInError && <p className="text-caption text-error mt-sm">{checkInError}</p>}
                        </>
                      )}
                    </div>

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
