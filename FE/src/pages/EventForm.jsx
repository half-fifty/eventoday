import { useEffect, useRef, useState } from "react";
import { createPortal } from "react-dom";
import {
  Link,
  useNavigate,
  useParams,
  useSearchParams,
} from "react-router-dom";
import { eventApi } from "../api/eventApi.js";
import { locationApi } from "../api/locationApi.js";
import { fileDownloadUrl } from "../api/fileApi.js";
import KakaoMapPreview from "../components/KakaoMapPreview.jsx";
import Icon from "../components/Icon.jsx";
import FileUploadField from "../components/FileUploadField.jsx";
import EventDetailImageEditor from "../components/EventDetailImageEditor.jsx";
import EventDetailSettings from "../components/EventDetailSettings.jsx";
import EventDetailPresentation from "../components/EventDetailPresentation.jsx";
import { EXHIBIT_CATEGORIES } from "../constants/eventOptions.js";
import TopNav from "../components/TopNav.jsx";

const emptyForm = {
  name: "",
  eventType: "EXPO",
  shortDescription: "",
  description: "",
  detailDisplayType: "IMAGE_GALLERY",
  officialWebsiteUrl: "",
  venueName: "",
  address: "",
  contactEmail: "",
  contactPhone: "",
  postalCode: "",
  addressDetail: "",
  latitude: null,
  longitude: null,
  kakaoPlaceId: null,
  startAt: "",
  endAt: "",
  ticketSalesStartAt: "",
  ticketSalesEndAt: "",
  ticketPrice: 0,
  ticketTotalQuantity: 100,
  ticketPurchaseLimit: 1,
  representativeFileId: null,
  boothRecruitmentEnabled: false,
  venueMapEnabled: false,
  boothReservationEnabled: false,
  noShowGraceMinutes: 10,
  exhibitCategoryCodes: [],
};

const toInputDateTime = (value) => {
  if (!value) return "";
  const date = new Date(value);
  const pad = (number) => String(number).padStart(2, "0");
  return `${date.getFullYear()}-${pad(date.getMonth() + 1)}-${pad(date.getDate())}T${pad(date.getHours())}:${pad(date.getMinutes())}`;
};
const toOffsetDateTime = (value) =>
  value ? new Date(value).toISOString() : null;
const OPERATION_CUTOFF_MS = 60 * 60 * 1000;

const normalizePosterForAnalysis = async (file) => {
  const supported = ["image/jpeg", "image/png", "image/webp"];
  if (!supported.includes(file.type)) {
    throw new Error("포스터 분석은 JPG, PNG, WEBP 파일만 지원합니다. HEIC·GIF 파일은 JPG 또는 PNG로 변환해 주세요.");
  }
  if (file.type !== "image/webp") return file;
  const bitmap = await createImageBitmap(file);
  try {
    const canvas = document.createElement("canvas");
    canvas.width = bitmap.width;
    canvas.height = bitmap.height;
    canvas.getContext("2d").drawImage(bitmap, 0, 0);
    const blob = await new Promise((resolve, reject) =>
      canvas.toBlob(
        (result) => result ? resolve(result) : reject(new Error("WEBP 이미지를 변환하지 못했습니다.")),
        "image/jpeg",
        0.92,
      ),
    );
    return new File([blob], `${file.name.replace(/\.webp$/i, "")}.jpg`, { type: "image/jpeg" });
  } finally {
    bitmap.close();
  }
};

function FormToast({ toast, onClose }) {
  if (!toast) return null;
  return (
    <div className={`fixed right-lg top-[64px] z-[100] flex max-w-[420px] items-center gap-sm rounded-xl border bg-white px-lg py-md shadow-xl ${toast.tone === "error" ? "border-error/30 text-error" : "border-status-available/30 text-status-available"}`} role={toast.tone === "error" ? "alert" : "status"}>
      <Icon name={toast.tone === "error" ? "error" : "check_circle"} />
      <span className="flex-1 text-sm font-body-strong">{toast.message}</span>
      <button type="button" aria-label="알림 닫기" onClick={onClose}><Icon name="close" className="text-[18px]" /></button>
    </div>
  );
}

export default function EventForm() {
  const { eventId } = useParams();
  const [searchParams] = useSearchParams();
  const navigate = useNavigate();
  const requestedOrganizationId =
    searchParams.get("organizationId") ||
    localStorage.getItem("organizationId") ||
    "";
  const [organizationId, setOrganizationId] = useState(requestedOrganizationId);
  const [organizations, setOrganizations] = useState([]);
  const [organizationLoading, setOrganizationLoading] = useState(true);
  const [organizationLoadFailed, setOrganizationLoadFailed] = useState(false);
  const [form, setForm] = useState(emptyForm);
  const [loading, setLoading] = useState(Boolean(eventId));
  const [detailLoaded, setDetailLoaded] = useState(!eventId);
  const [saving, setSaving] = useState(false);
  const [error, setError] = useState("");
  const [placeQuery, setPlaceQuery] = useState("");
  const [places, setPlaces] = useState([]);
  const [searching, setSearching] = useState(false);
  const [placeError, setPlaceError] = useState("");
  const [previewOpen, setPreviewOpen] = useState(false);
  const [previewImages, setPreviewImages] = useState([]);
  const [draftDetailImages, setDraftDetailImages] = useState([]);
  const [posterFile, setPosterFile] = useState(null);
  const [posterExtraction, setPosterExtraction] = useState(null);
  const [posterAnalyzing, setPosterAnalyzing] = useState(false);
  const [posterAnalysisError, setPosterAnalysisError] = useState("");
  const [contentSuggestion, setContentSuggestion] = useState(null);
  const [contentSuggesting, setContentSuggesting] = useState(false);
  const [toast, setToast] = useState(null);
  const [aiPortalTarget, setAiPortalTarget] = useState(null);
  const toastTimer = useRef(null);
  const placeSearchSequence = useRef(0);
  const postalCodeSequence = useRef(0);
  const publicInfoOnly =
    Boolean(eventId) && detailLoaded && !["PREPARING", "REJECTED"].includes(form.status);
  const cancelled = detailLoaded && form.status === "CANCELLED";

  useEffect(() => {
    eventApi
      .managedOrganizations()
      .then((result) => {
        const available = result?.data || [];
        setOrganizations(available);
        const allowedRequested = available.some(
          (organization) =>
            String(organization.id) === String(requestedOrganizationId),
        );
        const selected = allowedRequested
          ? String(requestedOrganizationId)
          : available.length === 1
            ? String(available[0].id)
            : "";
        setOrganizationId(selected);
        if (selected) localStorage.setItem("organizationId", selected);
      })
      .catch((requestError) => {
        setOrganizationLoadFailed(true);
        setError(
          requestError.status === 401
            ? "행사를 등록하려면 먼저 로그인해 주세요."
            : "운영 가능한 조직 정보를 불러오지 못했습니다.",
        );
      })
      .finally(() => setOrganizationLoading(false));
  }, [requestedOrganizationId]);

  useEffect(() => {
    if (!eventId) {
      setForm(emptyForm);
      setDetailLoaded(true);
      setLoading(false);
      return;
    }
    if (organizationLoading) {
      setLoading(true);
      return;
    }
    if (!organizationId) {
      setForm(emptyForm);
      setDetailLoaded(false);
      setLoading(false);
      return;
    }
    let cancelled = false;
    setForm(emptyForm);
    setDetailLoaded(false);
    setError("");
    setLoading(true);
    eventApi
      .managedDetail(organizationId, eventId)
      .then((result) => {
        if (cancelled) return;
        const data = result?.data;
        setForm({
          ...emptyForm,
          ...data,
          startAt: toInputDateTime(data.startAt),
          endAt: toInputDateTime(data.endAt),
          ticketSalesStartAt: toInputDateTime(data.ticketSalesStartAt),
          ticketSalesEndAt: toInputDateTime(data.ticketSalesEndAt),
        });
        setDetailLoaded(true);
      })
      .catch(
        (requestError) =>
          !cancelled &&
          setError(
            requestError.status === 403
              ? "이 행사를 조회하거나 수정할 권한이 없습니다. 조직의 OWNER 또는 MANAGER에게 권한을 요청해 주세요."
              : requestError.message || "행사를 불러오지 못했습니다.",
          ),
      )
      .finally(() => !cancelled && setLoading(false));
    return () => {
      cancelled = true;
    };
  }, [eventId, organizationId, organizationLoading]);

  useEffect(() => {
    if (!eventId || !organizationId) {
      setPreviewImages([]);
      return;
    }
    let cancelled = false;
    eventApi.managedDetailImages(organizationId, eventId)
      .then((result) => {
        if (!cancelled) setPreviewImages(result?.data || []);
      })
      .catch(() => {
        if (!cancelled) setPreviewImages([]);
      });
    return () => {
      cancelled = true;
    };
  }, [eventId, organizationId]);

  const change = (key, value) =>
    setForm((previous) => ({ ...previous, [key]: value }));
  const showToast = (message, tone = "success") => {
    window.clearTimeout(toastTimer.current);
    setToast({ message, tone });
    toastTimer.current = window.setTimeout(() => setToast(null), 2800);
  };
  const showFormError = (message) => {
    setError(message);
    showToast(message, "error");
  };
  const analyzePoster = async () => {
    if (!posterFile || !organizationId) {
      setPosterAnalysisError("분석할 포스터 이미지를 먼저 선택해 주세요.");
      return;
    }
    setPosterAnalyzing(true);
    setPosterAnalysisError("");
    setPosterExtraction(null);
    try {
      const analysisFile = await normalizePosterForAnalysis(posterFile);
      const result = await eventApi.extractPoster(organizationId, analysisFile);
      setPosterExtraction(result?.data || null);
    } catch (requestError) {
      setPosterAnalysisError(requestError.message || "포스터를 분석하지 못했습니다.");
    } finally {
      setPosterAnalyzing(false);
    }
  };
  const applyPosterDate = (key, date, time) => {
    if (date) change(key, `${date}T${time}`);
  };
  const suggestEventContent = async () => {
    if (!organizationId || contentSuggesting) return;
    if (!form.name?.trim()) {
      setPosterAnalysisError("행사명 또는 포스터 분석 결과를 먼저 적용해 주세요.");
      return;
    }
    setContentSuggesting(true);
    setContentSuggestion(null);
    setPosterAnalysisError("");
    try {
      const result = await eventApi.suggestContent({
        organizationId: Number(organizationId),
        eventName: form.name,
        eventType: form.eventType,
        startAt: form.startAt || null,
        endAt: form.endAt || null,
        venueName: form.venueName || posterExtraction?.venueName || null,
        posterSummary: posterExtraction?.summary || null,
        posterVisibleText: posterExtraction?.rawVisibleText?.slice(0, 4000) || null,
        allowedCategoryCodes: EXHIBIT_CATEGORIES.map(([code]) => code),
      });
      setContentSuggestion(result?.data || null);
    } catch (requestError) {
      setPosterAnalysisError(requestError.message || "행사 소개를 추천하지 못했습니다.");
    } finally {
      setContentSuggesting(false);
    }
  };
  const toggleCategory = (code) =>
    setForm((previous) => ({
      ...previous,
      exhibitCategoryCodes: previous.exhibitCategoryCodes.includes(code)
        ? previous.exhibitCategoryCodes.filter((item) => item !== code)
        : [...previous.exhibitCategoryCodes, code],
    }));
  const searchPlaces = async (e) => {
    e.preventDefault();
    if (placeQuery.trim().length < 2) {
      placeSearchSequence.current += 1;
      setPlaces([]);
      setSearching(false);
      setPlaceError("두 글자 이상 입력해 주세요.");
      return;
    }
    const sequence = ++placeSearchSequence.current;
    setSearching(true);
    setPlaceError("");
    try {
      const result = await locationApi.search(placeQuery.trim());
      if (sequence !== placeSearchSequence.current) return;
      setPlaces(result?.data || []);
      if (!result?.data?.length) setPlaceError("검색 결과가 없습니다.");
    } catch (requestError) {
      if (sequence === placeSearchSequence.current)
        setPlaceError(requestError.message || "장소를 검색하지 못했습니다.");
    } finally {
      if (sequence === placeSearchSequence.current) setSearching(false);
    }
  };
  const changePlaceQuery = (value) => {
    placeSearchSequence.current += 1;
    setPlaceQuery(value);
    setPlaces([]);
    setSearching(false);
    setPlaceError("");
  };
  const selectPlace = async (place) => {
    const sequence = ++postalCodeSequence.current;
    const selectedAddress = place.roadAddress || place.address;
    setForm((previous) => ({
      ...previous,
      venueName: place.name,
      address: selectedAddress,
      postalCode: "",
      latitude: place.latitude,
      longitude: place.longitude,
      kakaoPlaceId: place.placeId,
    }));
    setPlaces([]);
    setPlaceQuery(place.name);
    try {
      const result = await locationApi.postalCode(selectedAddress);
      if (sequence === postalCodeSequence.current)
        change("postalCode", result?.data?.postalCode || "");
    } catch {
      if (sequence === postalCodeSequence.current)
        setPlaceError("장소는 선택했지만 우편번호를 자동 조회하지 못했습니다.");
    }
  };
  const submit = async (e) => {
    e.preventDefault();
    if (!organizationId) {
      showFormError("행사를 등록할 운영 조직을 선택해 주세요.");
      return;
    }
    if (!form.representativeFileId) {
      showFormError("행사 포스터를 등록해 주세요.");
      return;
    }
    if (form.detailDisplayType === "EXTERNAL_SITE" && !form.officialWebsiteUrl?.trim()) {
      showFormError("공식 사이트 표시를 선택했다면 행사 홈페이지 URL을 입력해 주세요.");
      return;
    }
    const normalizedDescription = form.description?.trim()
      || form.shortDescription?.trim()
      || form.name?.trim()
      || "행사 상세정보";
    if (publicInfoOnly) {
      setSaving(true);
      setError("");
      try {
        await eventApi.updatePublicInfo(organizationId, eventId, {
          shortDescription: form.shortDescription,
          description: normalizedDescription,
          detailDisplayType: form.detailDisplayType,
          officialWebsiteUrl: form.officialWebsiteUrl?.trim() || null,
          contactEmail: form.contactEmail,
          contactPhone: form.contactPhone,
          representativeFileId: form.representativeFileId,
        });
        navigate(
          `/organizer-admin?organizationId=${organizationId}&eventId=${eventId}`,
        );
      } catch (requestError) {
        setError(
          requestError.status === 403
            ? "이 행사를 수정할 권한이 없습니다. 조직의 OWNER 또는 MANAGER에게 권한을 요청해 주세요."
            : requestError.message || "공개 정보를 변경하지 못했습니다.",
        );
      } finally {
        setSaving(false);
      }
      return;
    }
    if (!form.exhibitCategoryCodes.length) {
      showFormError("전시품목을 하나 이상 선택해 주세요.");
      return;
    }
    if (new Date(form.startAt) >= new Date(form.endAt)) {
      showFormError("행사 종료는 시작 이후여야 합니다.");
      return;
    }
    if (
      form.ticketSalesEndAt &&
      new Date(form.ticketSalesEndAt).getTime() >
        new Date(form.endAt).getTime() - OPERATION_CUTOFF_MS
    ) {
      showFormError("티켓 판매 종료는 행사 종료 1시간 전까지로 설정해 주세요.");
      return;
    }
    const operationCutoffTime =
      new Date(form.endAt).getTime() - OPERATION_CUTOFF_MS;
    const effectiveSalesEndTime = form.ticketSalesEndAt
      ? Math.min(new Date(form.ticketSalesEndAt).getTime(), operationCutoffTime)
      : operationCutoffTime;
    if (
      form.ticketSalesStartAt &&
      new Date(form.ticketSalesStartAt).getTime() >= effectiveSalesEndTime
    ) {
      showFormError("티켓 판매 시작은 실제 판매 종료보다 이전이어야 합니다.");
      return;
    }
    setSaving(true);
    setError("");
    const payload = {
      ...form,
      description: normalizedDescription,
      officialWebsiteUrl: form.officialWebsiteUrl?.trim() || null,
      ticketPrice: Number(form.ticketPrice),
      ticketTotalQuantity: Number(form.ticketTotalQuantity),
      ticketPurchaseLimit: Number(form.ticketPurchaseLimit),
      noShowGraceMinutes: Number(form.noShowGraceMinutes),
      startAt: toOffsetDateTime(form.startAt),
      endAt: toOffsetDateTime(form.endAt),
      ticketSalesStartAt: toOffsetDateTime(form.ticketSalesStartAt),
      ticketSalesEndAt: toOffsetDateTime(form.ticketSalesEndAt),
    };
    try {
      const result = eventId
        ? await eventApi.update(organizationId, eventId, payload)
        : await eventApi.create(organizationId, payload);
      if (!eventId && form.detailDisplayType === "IMAGE_GALLERY" && draftDetailImages.length > 0) {
        try {
          await eventApi.replaceDetailImages(organizationId, result.data.id, draftDetailImages);
        } catch (imageError) {
          sessionStorage.setItem(
            "eventFormNotice",
            imageError.message || "행사는 저장됐지만 상세 이미지를 저장하지 못했습니다. 다시 등록해 주세요.",
          );
          navigate(`/organizer-admin/events/${result.data.id}/edit?organizationId=${organizationId}`);
          return;
        }
      }
      navigate(
        `/organizer-admin?organizationId=${organizationId}&eventId=${result.data.id}`,
      );
    } catch (requestError) {
      setError(
        requestError.status === 403
          ? "이 행사를 수정할 권한이 없습니다. 조직의 OWNER 또는 MANAGER에게 권한을 요청해 주세요."
          : requestError.message || "저장하지 못했습니다.",
      );
    } finally {
      setSaving(false);
    }
  };

  const field =
    "w-full h-11 border border-hairline rounded-lg px-md bg-white outline-none focus:border-primary";
  const section =
    "bg-white border border-hairline rounded-2xl p-lg md:p-xl space-y-lg";
  if (loading)
    return (
      <div className="min-h-screen grid place-items-center">
        행사를 불러오는 중입니다.
      </div>
    );
  if (eventId && !detailLoaded)
    return (
      <><TopNav /><main className="mx-auto max-w-[760px] px-lg py-xxl"><div role="alert" className="rounded-2xl border border-error/20 bg-error/5 p-xl text-error">{error || "행사 정보를 불러오지 못했습니다."}</div><Link to="/organizer-admin" className="mt-lg inline-flex text-primary">관리 화면으로 돌아가기</Link></main></>
    );
  if (publicInfoOnly)
    return (
      <>
        <TopNav active="organizer" />
        <FormToast toast={toast} onClose={() => setToast(null)} />
        <main className="min-h-screen bg-surface-container-low px-lg pb-xl pt-[76px]">
          <form onSubmit={submit} className="max-w-[920px] mx-auto space-y-lg">
            <header className="flex flex-wrap justify-between items-end gap-md">
              <div>
                <p className="text-caption text-primary">ORGANIZER CENTER</p>
                <h1 className="font-display-lg text-[32px]">공개 정보 수정</h1>
                <p className="text-ink-muted mt-xs">
                  예매에 영향을 주지 않는 행사 소개와 문의 정보를 바로 수정할 수
                  있습니다.
                </p>
              </div>
              <Link
                to={`/organizer-admin?organizationId=${organizationId || ""}&eventId=${eventId}`}
                className="inline-flex items-center gap-xs text-caption"
              >
                <Icon name="arrow_back" className="text-[17px]" />
                운영센터로
              </Link>
            </header>
            <div
              className={`rounded-2xl border p-lg flex gap-md ${cancelled ? "border-error/20 bg-error/5" : "border-primary/20 bg-primary/5"}`}
            >
              <span
                className={`grid h-10 w-10 shrink-0 place-items-center rounded-full ${cancelled ? "bg-error/10 text-error" : "bg-primary/10 text-primary"}`}
              >
                <Icon name={cancelled ? "block" : "info"} />
              </span>
              <div>
                <p className="font-body-strong">
                  {cancelled
                    ? "취소된 행사는 수정할 수 없습니다"
                    : "수정 가능한 항목만 표시하고 있어요"}
                </p>
                <p className="mt-xs text-caption leading-6 text-ink-muted">
                  {cancelled
                    ? "잘못 취소했거나 추가 안내가 필요하다면 플랫폼 관리자에게 문의해 주세요."
                    : "행사명·장소·일정·가격·티켓·운영 기능은 예매자에게 영향을 주므로 현재 상태에서 잠겨 있습니다."}
                </p>
              </div>
            </div>
            {error && (
              <p
                role="alert"
                className="bg-error/10 border border-error/20 text-error p-md rounded-xl flex gap-sm"
              >
                <Icon name="error" />
                {error}
              </p>
            )}
            <section className={section}>
              <div className="flex flex-wrap items-start justify-between gap-md">
                <div>
                  <p className="text-caption text-primary mb-xs">
                    PUBLIC CONTENT
                  </p>
                  <h2 className="font-display-md text-[22px]">행사 소개</h2>
                  <p className="text-caption text-ink-muted mt-xs">
                    상세 페이지와 행사 목록에서 방문객에게 보이는 내용입니다.
                  </p>
                </div>
                <span className="rounded-full bg-surface-container px-md py-xs text-caption text-ink-muted">
                  {form.status}
                </span>
              </div>
              <label>
                한 줄 소개
                <input
                  disabled={cancelled}
                  maxLength={300}
                  className={`${field} disabled:bg-surface-container disabled:text-ink-muted`}
                  value={form.shortDescription || ""}
                  onChange={(e) => change("shortDescription", e.target.value)}
                  placeholder="행사의 매력을 한 문장으로 소개해 주세요"
                />
                <span className="block text-right text-caption text-ink-muted mt-xs">
                  {(form.shortDescription || "").length}/300
                </span>
              </label>
              <EventDetailSettings
                form={form}
                onChange={change}
                disabled={cancelled}
              />
            </section>
            <section className={section}>
              <div>
                <p className="text-caption text-primary mb-xs">CONTACT</p>
                <h2 className="font-display-md text-[22px]">문의처</h2>
                <p className="text-caption text-ink-muted mt-xs">
                  방문객에게 공개되는 공식 연락처입니다.
                </p>
              </div>
              <div className="grid md:grid-cols-2 gap-md">
                <label>
                  이메일 *
                  <input
                    disabled={cancelled}
                    required
                    type="email"
                    maxLength={255}
                    className={`${field} disabled:bg-surface-container disabled:text-ink-muted`}
                    value={form.contactEmail || ""}
                    onChange={(e) => change("contactEmail", e.target.value)}
                  />
                </label>
                <label>
                  연락처 *
                  <input
                    disabled={cancelled}
                    required
                    type="tel"
                    maxLength={30}
                    pattern="[0-9\-]{9,14}"
                    className={`${field} disabled:bg-surface-container disabled:text-ink-muted`}
                    value={form.contactPhone || ""}
                    onChange={(e) =>
                      change(
                        "contactPhone",
                        e.target.value.replace(/[^0-9-]/g, ""),
                      )
                    }
                  />
                </label>
              </div>
            </section>
            <section className={section}>
              <div>
                <p className="text-caption text-primary mb-xs">POSTER</p>
                <h2 className="font-display-md text-[22px]">대표 포스터</h2>
                <p className="text-caption text-ink-muted mt-xs">
                  교체하면 행사 목록과 상세 페이지에 바로 반영됩니다.
                </p>
              </div>
              {form.representativeFileId && (
                <div className="rounded-2xl bg-surface-container p-md">
                  <img
                    src={fileDownloadUrl(form.representativeFileId)}
                    alt={`${form.name} 현재 포스터`}
                    onError={(imageEvent) => {
                      imageEvent.currentTarget.style.display = "none";
                    }}
                    className="mx-auto max-h-[360px] rounded-xl object-contain"
                  />
                </div>
              )}
              {!cancelled && (
                <FileUploadField
                  label="새 행사 포스터"
                  required
                  value={form.representativeFileId}
                  onChange={(fileId) => change("representativeFileId", fileId)}
                />
              )}
            </section>
            {form.detailDisplayType === "IMAGE_GALLERY" && <section className={section}>
              <div>
                <p className="text-caption text-primary mb-xs">DETAIL IMAGES</p>
                <h2 className="font-display-md text-[22px]">상세정보 이미지</h2>
                <p className="text-caption text-ink-muted mt-xs">
                  티켓 예매 페이지처럼 세로형 이미지를 순서대로 이어서
                  보여줍니다.
                </p>
              </div>
              <EventDetailImageEditor
                organizationId={organizationId}
                eventId={eventId}
                disabled={cancelled}
              />
            </section>}
            <section className="rounded-2xl border border-hairline bg-white p-lg">
              <div className="flex items-start gap-md">
                <span className="grid h-10 w-10 shrink-0 place-items-center rounded-xl bg-surface-container text-ink-muted">
                  <Icon name="lock" />
                </span>
                <div>
                  <h2 className="font-body-strong">잠긴 운영 정보</h2>
                  <p className="mt-xs text-caption leading-6 text-ink-muted">
                    행사명, 유형, 장소, 일정, 판매 기간, 가격, 티켓 수량,
                    전시품목과 운영 기능은 현재 수정할 수 없습니다. 변경이 꼭
                    필요하면 플랫폼 관리자에게 재승인을 요청해 주세요.
                  </p>
                </div>
              </div>
            </section>
            {!cancelled && (
              <div className="sticky bottom-md rounded-2xl border border-hairline bg-white/95 p-md shadow-xl backdrop-blur flex flex-wrap items-center justify-between gap-md">
                <p className="text-caption text-ink-muted">
                  저장하면 방문객 화면에 바로 반영됩니다.
                </p>
                <div className="flex gap-sm">
                <button type="button" onClick={() => setPreviewOpen(true)} className="px-lg py-sm rounded-full border border-hairline font-body-strong">미리보기</button>
                <button
                  disabled={saving}
                  className="min-w-44 px-xl py-sm bg-primary text-white rounded-full font-body-strong disabled:opacity-50"
                >
                  {saving ? "저장 중..." : "공개 정보 저장"}
                </button>
                </div>
              </div>
            )}
          </form>
        </main>
        <EventPreviewModal open={previewOpen} onClose={() => setPreviewOpen(false)} form={form} detailImages={previewImages} />
      </>
    );
  return (
    <>
      <TopNav active="organizer" />
      <FormToast toast={toast} onClose={() => setToast(null)} />
      <main className="min-h-screen bg-surface-container-low px-lg pb-xl pt-[76px]">
        <form onSubmit={submit} className="max-w-[1040px] mx-auto space-y-lg">
          <header className="flex justify-between items-end gap-md">
            <div>
              <p className="text-caption text-primary">ORGANIZER CENTER</p>
              <h1 className="font-display-lg text-[32px]">
                {eventId ? "행사 수정" : "새 행사 등록"}
              </h1>
              <p className="text-ink-muted mt-xs">
                행사 공개 전까지 언제든 수정할 수 있습니다.
              </p>
            </div>
            <Link
              to={`/organizer-admin?organizationId=${organizationId || ""}`}
              className="text-caption"
            >
              돌아가기
            </Link>
          </header>
          {error && (
            <p className="bg-error/10 border border-error/20 text-error p-md rounded-xl">
              {error}
            </p>
          )}

          <section className={section}>
            <div>
              <p className="text-caption text-primary mb-xs">ORGANIZATION</p>
              <h2 className="font-display-md text-[22px]">운영 조직</h2>
            </div>
            {organizationLoading ? (
              <p className="text-ink-muted">
                운영 가능한 조직을 확인하는 중입니다.
              </p>
            ) : organizationLoadFailed ? (
              <p className="text-ink-muted">
                로그인 후 운영 조직을 확인할 수 있습니다.
              </p>
            ) : organizations.length === 0 ? (
              <p className="text-error">
                행사를 등록할 수 있는 운영 조직이 없습니다. 조직의 OWNER 또는
                MANAGER 권한이 필요합니다.
              </p>
            ) : (
              <label>
                행사를 등록할 조직 *
                <select
                  required
                  className={field}
                  value={organizationId}
                  onChange={(e) => {
                    setOrganizationId(e.target.value);
                    localStorage.setItem("organizationId", e.target.value);
                  }}
                >
                  <option value="">조직을 선택하세요</option>
                  {organizations.map((organization) => (
                    <option key={organization.id} value={organization.id}>
                      {organization.name}
                    </option>
                  ))}
                </select>
              </label>
            )}
            <div ref={setAiPortalTarget} className="mt-lg" />
          </section>

          <section className={section}>
            <div>
              <p className="text-caption text-primary mb-xs">01</p>
              <h2 className="font-display-md text-[22px]">기본 정보</h2>
            </div>
            <div className="grid md:grid-cols-2 gap-md">
              <label>
                행사명 *
                <input
                  required
                  maxLength={200}
                  className={field}
                  value={form.name}
                  onChange={(e) => change("name", e.target.value)}
                  placeholder="행사명을 입력하세요"
                />
              </label>
              <label>
                행사 유형 *
                <select
                  className={field}
                  value={form.eventType}
                  onChange={(e) => change("eventType", e.target.value)}
                >
                  <option value="EXPO">박람회</option>
                  <option value="EXHIBITION">전시회</option>
                  <option value="SEMINAR">세미나</option>
                  <option value="CONFERENCE">컨퍼런스</option>
                </select>
              </label>
              <label className="md:col-span-2">
                한 줄 소개
                <input
                  maxLength={300}
                  className={field}
                  value={form.shortDescription || ""}
                  onChange={(e) => change("shortDescription", e.target.value)}
                  placeholder="목록에 표시할 짧은 소개"
                />
              </label>
              <div className="md:col-span-2">
                <EventDetailSettings form={form} onChange={change} />
              </div>
            </div>
          </section>

          <section className={section}>
            <div>
              <p className="text-caption text-primary mb-xs">02</p>
              <h2 className="font-display-md text-[22px]">행사 장소</h2>
              <p className="text-caption text-ink-muted mt-xs">
                시설명을 검색하면 주소와 지도 위치가 자동 입력됩니다.
              </p>
            </div>
            <div className="grid lg:grid-cols-2 gap-lg">
              <div className="space-y-md">
                <div>
                  <label>장소 검색</label>
                  <div className="flex gap-sm mt-xs">
                    <input
                      className={field}
                      value={placeQuery}
                      onChange={(e) => changePlaceQuery(e.target.value)}
                      onKeyDown={(e) => {
                        if (e.key === "Enter") searchPlaces(e);
                      }}
                      placeholder="예: 코엑스, 킨텍스"
                    />
                    <button
                      type="button"
                      onClick={searchPlaces}
                      disabled={searching}
                      className="shrink-0 px-lg bg-primary text-white rounded-lg disabled:opacity-50"
                    >
                      <Icon name="search" /> {searching ? "검색 중" : "검색"}
                    </button>
                  </div>
                </div>
                {placeError && (
                  <p className="text-caption text-error">{placeError}</p>
                )}
                {places.length > 0 && (
                  <div className="border border-hairline rounded-xl divide-y divide-divider-soft max-h-64 overflow-y-auto">
                    {places.map((place) => (
                      <button
                        type="button"
                        key={place.placeId}
                        onClick={() => selectPlace(place)}
                        className="w-full text-left p-md hover:bg-primary/5"
                      >
                        <p className="font-body-strong">{place.name}</p>
                        <p className="text-caption text-ink-muted">
                          {place.roadAddress || place.address}
                        </p>
                        <p className="text-[11px] text-ink-muted mt-xs">
                          {place.category}
                        </p>
                      </button>
                    ))}
                  </div>
                )}
                <div className="grid grid-cols-[120px_1fr] gap-md">
                  <label>
                    우편번호
                    <input
                      maxLength={10}
                      className={field}
                      value={form.postalCode || ""}
                      onChange={(e) => change("postalCode", e.target.value)}
                    />
                  </label>
                  <label>
                    장소명 *
                    <input
                      required
                      className={field}
                      value={form.venueName}
                      onChange={(e) => change("venueName", e.target.value)}
                    />
                  </label>
                </div>
                <label>
                  도로명 주소 *
                  <input
                    required
                    className={field}
                    value={form.address}
                    onChange={(e) => change("address", e.target.value)}
                  />
                </label>
                <label>
                  상세 주소
                  <input
                    maxLength={200}
                    className={field}
                    value={form.addressDetail || ""}
                    onChange={(e) => change("addressDetail", e.target.value)}
                    placeholder="홀, 층, 출입구 등"
                  />
                </label>
              </div>
              <KakaoMapPreview
                latitude={form.latitude}
                longitude={form.longitude}
                venueName={form.venueName}
              />
            </div>
          </section>

          <section className={section}>
            <div>
              <p className="text-caption text-primary mb-xs">03</p>
              <h2 className="font-display-md text-[22px]">일정 및 티켓</h2>
            </div>
            <div className="grid md:grid-cols-2 gap-md">
              <label>
                행사 시작 *
                <input
                  required
                  type="datetime-local"
                  className={field}
                  value={form.startAt}
                  onChange={(e) => change("startAt", e.target.value)}
                />
              </label>
              <label>
                행사 종료 *
                <input
                  required
                  type="datetime-local"
                  className={field}
                  value={form.endAt}
                  onChange={(e) => change("endAt", e.target.value)}
                />
              </label>
              <label>
                티켓 판매 시작
                <input
                  type="datetime-local"
                  className={field}
                  value={form.ticketSalesStartAt || ""}
                  onChange={(e) => change("ticketSalesStartAt", e.target.value)}
                />
              </label>
              <label>
                티켓 판매 종료
                <input
                  type="datetime-local"
                  className={field}
                  value={form.ticketSalesEndAt || ""}
                  onChange={(e) => change("ticketSalesEndAt", e.target.value)}
                />
              </label>
              <label>
                가격
                <input
                  min="0"
                  type="number"
                  className={field}
                  value={form.ticketPrice}
                  onChange={(e) => change("ticketPrice", e.target.value)}
                />
              </label>
              <label>
                총 티켓 수량
                <input
                  min="0"
                  type="number"
                  className={field}
                  value={form.ticketTotalQuantity}
                  onChange={(e) =>
                    change("ticketTotalQuantity", e.target.value)
                  }
                />
              </label>
              <label>
                1회 구매 제한
                <input
                  min="1"
                  type="number"
                  className={field}
                  value={form.ticketPurchaseLimit}
                  onChange={(e) =>
                    change("ticketPurchaseLimit", e.target.value)
                  }
                />
              </label>
              <label>
                노쇼 유예 시간(분)
                <input
                  min="0"
                  type="number"
                  className={field}
                  value={form.noShowGraceMinutes}
                  onChange={(e) => change("noShowGraceMinutes", e.target.value)}
                />
              </label>
            </div>
          </section>

          <section className={section}>
            <div>
              <p className="text-caption text-primary mb-xs">04</p>
              <h2 className="font-display-md text-[22px]">문의처 및 포스터</h2>
              <p className="text-caption text-ink-muted mt-xs">
                방문객에게 공개되는 공식 문의처입니다. 개인번호보다 행사
                대표번호 사용을 권장합니다.
              </p>
            </div>
            <div className="grid md:grid-cols-2 gap-md">
              <label>
                담당자 이메일 *
                <input
                  required
                  type="email"
                  maxLength={255}
                  className={field}
                  value={form.contactEmail}
                  onChange={(e) => change("contactEmail", e.target.value)}
                  placeholder="event@example.com"
                />
              </label>
              <label>
                담당자 연락처 *
                <input
                  required
                  type="tel"
                  maxLength={30}
                  pattern="[0-9\\-]{9,14}"
                  title="숫자와 하이픈을 사용해 입력해 주세요."
                  className={field}
                  value={form.contactPhone}
                  onChange={(e) =>
                    change(
                      "contactPhone",
                      e.target.value.replace(/[^0-9-]/g, ""),
                    )
                  }
                  placeholder="02-1234-5678"
                />
                <span className="block text-caption text-ink-muted mt-xs">
                  예: 02-1234-5678, 031-123-4567
                </span>
              </label>
            </div>
            <FileUploadField
              label="행사 포스터"
              required
              value={form.representativeFileId}
              onChange={(fileId) => change("representativeFileId", fileId)}
              onFileSelected={(file) => {
                setPosterFile(file);
                setPosterExtraction(null);
                setPosterAnalysisError("");
              }}
            />
            {aiPortalTarget && createPortal(<div className="rounded-xl border border-primary/20 bg-primary/5 p-md">
              <div className="flex flex-wrap items-center justify-between gap-md">
                <div>
                  <strong className="flex items-center gap-xs text-primary"><Icon name="document_scanner" /> AI 포스터 정보 추출</strong>
                  <p className="mt-xs text-caption leading-5 text-ink-muted">Qwen이 이미지에 보이는 정보만 후보로 제안합니다. 확인 후 필요한 항목만 적용하세요.</p>
                </div>
                <button type="button" onClick={analyzePoster} disabled={!posterFile || posterAnalyzing} className="rounded-full bg-primary px-lg py-sm text-sm font-body-strong text-white disabled:bg-surface-container-highest disabled:text-ink-muted">
                  {posterAnalyzing ? "분석 중..." : "포스터 분석"}
                </button>
              </div>
              {posterAnalysisError && <p className="mt-md text-caption text-error">{posterAnalysisError}</p>}
              {posterExtraction && (
                <div className="mt-md space-y-sm border-t border-primary/10 pt-md">
                  {[
                    ["행사명", posterExtraction.eventName, () => change("name", posterExtraction.eventName)],
                    ["시작일", posterExtraction.startDate, () => applyPosterDate("startAt", posterExtraction.startDate, "09:00")],
                    ["종료일", posterExtraction.endDate, () => applyPosterDate("endAt", posterExtraction.endDate, "18:00")],
                    ["장소", [posterExtraction.venueName, posterExtraction.hall].filter(Boolean).join(" "), () => { setPlaceQuery(posterExtraction.venueName || ""); setPlaceError("장소 검색 결과에서 정확한 주소를 선택해 주세요."); }],
                    ["홈페이지", posterExtraction.officialWebsiteUrl, () => change("officialWebsiteUrl", posterExtraction.officialWebsiteUrl?.startsWith("http") ? posterExtraction.officialWebsiteUrl : `https://${posterExtraction.officialWebsiteUrl}`)],
                  ].filter(([, value]) => value).map(([label, value, apply]) => (
                    <div key={label} className="flex items-center gap-sm rounded-lg bg-white px-md py-sm">
                      <span className="w-20 shrink-0 text-caption font-bold text-ink-muted">{label}</span>
                      <span className="min-w-0 flex-1 break-words text-sm">{value}</span>
                      <button type="button" onClick={() => { apply(); showToast(`${label} 정보가 적용되었습니다.`); }} className="shrink-0 rounded-full border border-hairline px-md py-xs text-caption font-body-strong hover:border-primary hover:text-primary">적용</button>
                    </div>
                  ))}
                  {(posterExtraction.organizer || posterExtraction.operator || posterExtraction.sponsors?.length > 0) && (
                    <div className="rounded-lg bg-white px-md py-sm text-caption leading-6 text-ink-muted">
                      주최 {posterExtraction.organizer || "-"} · 주관 {posterExtraction.operator || "-"}
                      {posterExtraction.sponsors?.length > 0 && ` · 후원 ${posterExtraction.sponsors.join(", ")}`}
                    </div>
                  )}
                  {posterExtraction.warnings?.length > 0 && <div className="rounded-lg bg-warning/10 px-md py-sm text-caption text-on-surface-variant">확인 필요: {posterExtraction.warnings.join(" · ")}</div>}
                  <p className="text-caption text-ink-muted">AI 추출 결과는 자동 저장되지 않습니다. 특히 일정과 장소는 원본 포스터와 다시 확인해 주세요.</p>
                </div>
              )}
              <div className="mt-md border-t border-primary/10 pt-md">
                <div className="flex flex-wrap items-center justify-between gap-md">
                  <div>
                    <strong className="text-sm">검색 근거 기반 행사 콘텐츠 추천</strong>
                    <p className="mt-xs text-caption text-ink-muted">현재 입력값과 포스터에서 확인된 글자만 사용해 소개문과 전시품목을 제안합니다.</p>
                  </div>
                  <button type="button" onClick={suggestEventContent} disabled={!form.name || contentSuggesting} className="rounded-full border border-primary px-lg py-sm text-sm font-body-strong text-primary disabled:border-hairline disabled:text-ink-muted">
                    {contentSuggesting ? "추천 생성 중..." : "소개 콘텐츠 추천"}
                  </button>
                </div>
                {contentSuggestion && (
                  <div className="mt-md space-y-sm">
                    {contentSuggestion.notice && <p className="rounded-lg bg-warning/10 px-md py-sm text-caption">{contentSuggestion.notice}</p>}
                    {contentSuggestion.shortDescription && (
                      <div className="rounded-lg bg-white p-md text-sm">
                        <p className="font-body-strong">한 줄 소개</p><p className="mt-xs leading-6 text-ink-muted">{contentSuggestion.shortDescription}</p>
                        <button type="button" onClick={() => { change("shortDescription", contentSuggestion.shortDescription); showToast("한 줄 소개가 적용되었습니다."); }} className="mt-sm rounded-full border border-hairline px-md py-xs text-caption hover:border-primary hover:text-primary">한 줄 소개에 적용</button>
                      </div>
                    )}
                    {contentSuggestion.description && (
                      <div className="rounded-lg bg-white p-md text-sm">
                        <p className="font-body-strong">상세 소개 초안</p><p className="mt-xs whitespace-pre-wrap leading-6 text-ink-muted">{contentSuggestion.description}</p>
                        <button type="button" onClick={() => { change("description", contentSuggestion.description); showToast("상세 소개가 적용되었습니다."); }} className="mt-sm rounded-full border border-hairline px-md py-xs text-caption hover:border-primary hover:text-primary">상세 소개에 적용</button>
                      </div>
                    )}
                    {contentSuggestion.categoryCodes?.length > 0 && (
                      <div className="rounded-lg bg-white p-md text-sm">
                        <p className="font-body-strong">추천 전시품목</p><p className="mt-xs text-ink-muted">{contentSuggestion.categoryCodes.map((code) => EXHIBIT_CATEGORIES.find(([value]) => value === code)?.[1] || code).join(" · ")}</p>
                        <button type="button" onClick={() => { change("exhibitCategoryCodes", contentSuggestion.categoryCodes.slice(0, 5)); showToast("추천 전시품목이 적용되었습니다."); }} className="mt-sm rounded-full border border-hairline px-md py-xs text-caption hover:border-primary hover:text-primary">전시품목에 적용</button>
                      </div>
                    )}
                    {contentSuggestion.sources?.length > 0 && <p className="text-caption text-ink-muted">사용 근거: {contentSuggestion.sources.join(" · ")}</p>}
                    {contentSuggestion.warnings?.length > 0 && <p className="text-caption text-error">확인 필요: {contentSuggestion.warnings.join(" · ")}</p>}
                  </div>
                )}
              </div>
            </div>, aiPortalTarget)}
            <div className="rounded-xl bg-primary/5 border border-primary/10 p-md text-caption text-on-surface-variant">
              <strong className="block text-primary mb-xs">
                포스터 권장 사양
              </strong>
              세로형 3:4 비율, 최소 900×1200px 이미지를 권장합니다. 행사 목록과
              상세 페이지에 동일한 이미지가 사용됩니다.
            </div>
          </section>

          <section className={section}>
            <div>
              <p className="text-caption text-primary mb-xs">02</p>
              <h2 className="font-display-md text-[22px]">전시품목</h2>
              <p className="text-caption text-ink-muted mt-xs">
                행사에서 다루는 품목을 최대 5개까지 선택해 주세요.
              </p>
            </div>
            <div className="grid sm:grid-cols-2 lg:grid-cols-3 gap-sm">
              {EXHIBIT_CATEGORIES.map(([code, label]) => {
                const selected = form.exhibitCategoryCodes.includes(code);
                return (
                  <label
                    key={code}
                    className={`flex items-center gap-sm border rounded-xl p-md cursor-pointer ${selected ? "border-primary bg-primary/5 text-primary" : "border-hairline"}`}
                  >
                    <input
                      type="checkbox"
                      checked={selected}
                      disabled={
                        !selected && form.exhibitCategoryCodes.length >= 5
                      }
                      onChange={() => toggleCategory(code)}
                    />
                    <span className="text-caption font-body-strong">
                      {label}
                    </span>
                  </label>
                );
              })}
            </div>
          </section>

          <section className={section}>
            <div>
              <p className="text-caption text-primary mb-xs">05</p>
              <h2 className="font-display-md text-[22px]">운영 기능</h2>
            </div>
            <div className="grid md:grid-cols-3 gap-md">
              {[
                [
                  "boothRecruitmentEnabled",
                  "부스 모집",
                  "참가 조직의 부스 신청을 받습니다.",
                ],
                ["venueMapEnabled", "평면도", "행사장 평면도를 제공합니다."],
                [
                  "boothReservationEnabled",
                  "부스 예약",
                  "방문자가 부스 시간을 예약합니다.",
                ],
              ].map(([key, label, help]) => (
                <label
                  key={key}
                  className={`border rounded-xl p-md cursor-pointer ${form[key] ? "border-primary bg-primary/5" : "border-hairline"}`}
                >
                  <span className="flex gap-sm items-center">
                    <input
                      type="checkbox"
                      checked={form[key]}
                      onChange={(e) => change(key, e.target.checked)}
                    />
                    <strong>{label}</strong>
                  </span>
                  <span className="block text-caption text-ink-muted mt-sm">
                    {help}
                  </span>
                </label>
              ))}
            </div>
          </section>
          {eventId && form.detailDisplayType === "IMAGE_GALLERY" && (
            <section className={section}>
              <div>
                <p className="text-caption text-primary mb-xs">06</p>
                <h2 className="font-display-md text-[22px]">상세정보 이미지</h2>
                <p className="text-caption text-ink-muted mt-xs">
                  이미지 저장은 행사 기본정보 저장과 별도로 처리됩니다.
                </p>
              </div>
              <EventDetailImageEditor
                organizationId={organizationId}
                eventId={eventId}
              />
            </section>
          )}
          {!eventId && form.detailDisplayType === "IMAGE_GALLERY" && (
            <section className={section}>
              <div>
                <p className="text-caption text-primary mb-xs">06</p>
                <h2 className="font-display-md text-[22px]">상세정보 이미지</h2>
                <p className="text-caption text-ink-muted mt-xs">행사를 처음 저장할 때 상세 이미지도 함께 등록됩니다.</p>
              </div>
              <EventDetailImageEditor
                organizationId={organizationId}
                initialImages={draftDetailImages}
                onImagesChange={setDraftDetailImages}
              />
            </section>
          )}
          <div className="sticky bottom-md bg-white/90 backdrop-blur border border-hairline rounded-2xl p-md flex justify-between items-center shadow-lg">
            <p className="text-caption text-ink-muted hidden sm:block">
              필수 항목을 확인한 후 저장해 주세요.
            </p>
            <div className="flex w-full gap-sm sm:w-auto">
            <button type="button" onClick={() => setPreviewOpen(true)} className="flex-1 px-xl py-md border border-hairline rounded-full font-body-strong sm:flex-none">미리보기</button>
            <button
              disabled={saving}
              className="w-full sm:w-auto px-xxl py-md bg-primary text-white rounded-full font-body-strong disabled:opacity-50"
            >
              {saving ? "저장 중..." : "행사 저장"}
            </button>
            </div>
          </div>
        </form>
      </main>
      <EventPreviewModal open={previewOpen} onClose={() => setPreviewOpen(false)} form={form} detailImages={previewImages} />
    </>
  );
}

function EventPreviewModal({ open, onClose, form, detailImages }) {
  if (!open) return null;
  return (
    <div className="fixed inset-0 z-[100] grid place-items-center bg-black/55 p-lg" onMouseDown={(event) => event.target === event.currentTarget && onClose()}>
      <section role="dialog" aria-modal="true" aria-labelledby="event-preview-title" className="max-h-[92vh] w-full max-w-[1100px] overflow-y-auto rounded-2xl bg-white shadow-2xl">
        <header className="sticky top-0 z-10 flex items-center justify-between border-b border-hairline bg-white px-lg py-md">
          <div>
            <p className="text-caption text-primary">EVENT PAGE PREVIEW</p>
            <h2 id="event-preview-title" className="font-display-md text-[22px]">{form.name || "행사 상세 페이지"} 미리보기</h2>
          </div>
          <button type="button" onClick={onClose} aria-label="미리보기 닫기"><Icon name="close" /></button>
        </header>
        <div className="p-lg"><EventDetailPresentation event={form} detailImages={detailImages} preview /></div>
      </section>
    </div>
  );
}
