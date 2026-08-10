import { useEffect, useMemo, useRef, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import TopNav from "../components/TopNav.jsx";
import RecruitmentManagementPanel from "../components/RecruitmentManagementPanel.jsx";
import BoothManagementPanel from "../components/BoothManagementPanel.jsx";
import FloorplanManagementPanel from "../components/FloorplanManagementPanel.jsx";
import FileDownloadLink from "../components/FileDownloadLink.jsx";
import { eventApi } from "../api/eventApi.js";
import { getManagementRecruitment } from "../api/recruitmentApi.js";
import {
  listEventApplications,
  listApplicationFiles,
  startReview,
  approveApplication,
  rejectApplication,
} from "../api/boothApplicationApi.js";
import { listContents, createContent, updateContent, deleteContent } from "../api/contentApi.js";
import { getEventOverview, getPopularBooths, getHourlyStatistics } from "../api/statisticsApi.js";

const navItems = [
  { key: "dashboard", label: "대시보드", icon: "dashboard" },
  { key: "recruitment", label: "부스 모집 공고", icon: "campaign" },
  { key: "applications", label: "부스 신청서 검토", icon: "assignment" },
  { key: "assignment", label: "부스 관리", icon: "grid_view" },
  { key: "floorplan", label: "평면도 관리", icon: "map" },
  { key: "contents", label: "공지·자료", icon: "article" },
  { key: "approval", label: "행사 등록 승인 요청", icon: "verified" },
];

// 신청 상태(BoothApplicationStatus)별 뱃지
const APP_BADGE = {
  SUBMITTED: { label: "신청됨", cls: "bg-primary/10 text-primary-focus" },
  UNDER_REVIEW: { label: "검토 중", cls: "bg-status-pending/10 text-status-pending" },
  APPROVED: { label: "승인됨", cls: "bg-status-available/10 text-status-available" },
  REJECTED: { label: "반려됨", cls: "bg-status-visited/10 text-status-visited" },
  CANCELLED: { label: "취소됨", cls: "bg-surface-container text-ink-muted" },
};

// 공지·자료 대상별 라벨
const AUDIENCE_LABEL = { ALL: "전체", EXHIBITOR: "참가기업", VISITOR: "방문자" };

const formatDate = (iso) => {
  if (!iso) return "-";
  const d = new Date(iso);
  return `${d.getFullYear()}.${String(d.getMonth() + 1).padStart(2, "0")}.${String(d.getDate()).padStart(2, "0")}`;
};

// 통계 조회 기본 기간: 최근 30일 (YYYY-MM-DD)
const statPeriod = () => {
  const to = new Date().toISOString().slice(0, 10);
  const from = new Date(Date.now() - 29 * 86400000).toISOString().slice(0, 10);
  return { from, to };
};

// 공지·자료 폼 초기값
const emptyContentForm = {
  contentType: "NOTICE",
  audience: "ALL",
  title: "",
  body: "",
  resourceType: "",
  version: "",
  pinned: false,
};

export default function OrganizerAdmin() {
  const [page, setPage] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(false);

  // 부스 신청서 검토 (WBS-197/198)
  const [applications, setApplications] = useState([]);
  const [appLoading, setAppLoading] = useState(false);
  const [appError, setAppError] = useState("");
  const [appFiles, setAppFiles] = useState({}); // { [applicationId]: 첨부파일 배열 }
  const [expandedAppId, setExpandedAppId] = useState(null);
  const [rejectTarget, setRejectTarget] = useState(null); // 반려 모달 대상 신청
  const [rejectReason, setRejectReason] = useState("");
  const [rejecting, setRejecting] = useState(false);

  // 공지·자료 (WBS-199/200)
  const [contents, setContents] = useState([]);
  const [contentLoading, setContentLoading] = useState(false);
  const [contentError, setContentError] = useState("");
  const [contentForm, setContentForm] = useState(null); // null=닫힘, {...}=열림(신규/수정)
  const [contentFile, setContentFile] = useState(null);
  const [fileError, setFileError] = useState(""); // 모달 내부 파일 선택 에러
  const [saveError, setSaveError] = useState(""); // 모달 내부 저장 에러 (BE 응답 포함)
  const [contentSaving, setContentSaving] = useState(false);

  // 통계 대시보드 (WBS-201)
  const [overview, setOverview] = useState(null);
  const [popularBooths, setPopularBooths] = useState([]);
  const [statError, setStatError] = useState("");

  // STAT-API-001: 시간대별 부스 통계 (부스·날짜 선택형)
  const [hourlyBoothId, setHourlyBoothId] = useState("");
  const [hourlyDate, setHourlyDate] = useState(() => new Date().toISOString().slice(0, 10)); // 기본값 오늘
  const [hourlyStats, setHourlyStats] = useState([]);
  const [hourlyLoading, setHourlyLoading] = useState(false);
  const [hourlyError, setHourlyError] = useState("");

  const [query] = useSearchParams();
  const requestedOrganizationId = query.get("organizationId") || "";
  const requestedEventId = query.get("eventId") || "";
  const previousRequestedEventId = useRef(requestedEventId);
  const [organizationId, setOrganizationId] = useState(requestedOrganizationId || localStorage.getItem("organizationId") || "");
  const [managedOrganizations, setManagedOrganizations] = useState([]);
  const [managedEvents, setManagedEvents] = useState([]);
  const [selectedEventId, setSelectedEventId] = useState("");
  const [eventLoadError, setEventLoadError] = useState("");
  const [submittingEvent, setSubmittingEvent] = useState(false);
  const [publishingEvent, setPublishingEvent] = useState(false);
  const [recruitmentStatus, setRecruitmentStatus] = useState(null);
  const [recruitmentLoading, setRecruitmentLoading] = useState(false);

  // 내 계정이 속한 조직 목록을 불러와, URL/localStorage의 organizationId가 없거나
  // 더 이상 내 소속이 아니면(다른 계정으로 로그인 등) 자동으로 첫 번째 소속 조직으로 교체한다.
  useEffect(() => {
    eventApi.managedOrganizations()
      .then((result) => {
        const list = result?.data || [];
        setManagedOrganizations(list);
        if (list.length === 0) {
          setOrganizationId("");
          localStorage.removeItem("organizationId");
          return;
        }
        const isValid = list.some((org) => String(org.id) === String(organizationId));
        if (!isValid) {
          setOrganizationId(String(list[0].id));
          setSelectedEventId("");
        }
      })
      .catch((error) => setEventLoadError(error.message || "소속 조직 정보를 불러오지 못했습니다."));
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  useEffect(() => {
    if (requestedOrganizationId
        && managedOrganizations.some((org) => String(org.id) === requestedOrganizationId)) {
      setOrganizationId(requestedOrganizationId);
    }
  }, [requestedOrganizationId, managedOrganizations]);

  useEffect(() => {
    if (organizationId) localStorage.setItem("organizationId", organizationId);
  }, [organizationId]);

  useEffect(() => {
    if (!organizationId) {
      setManagedEvents([]);
      setSelectedEventId("");
      return;
    }
    let active = true;
    setManagedEvents([]);
    setSelectedEventId("");
    setEventLoadError("");
    eventApi.organizationList(organizationId, { size: 100, sort: "createdAt,desc" })
      .then((result) => {
        if (!active) return;
        const list = result?.data?.content || [];
        setManagedEvents(list);
      })
      .catch((error) => {
        if (active) setEventLoadError(error.message || "행사 목록을 불러오지 못했습니다.");
      });
    return () => { active = false; };
  }, [organizationId]);

  useEffect(() => {
    if (managedEvents.length === 0) {
      setSelectedEventId("");
      return;
    }
    const requestedEventChanged = previousRequestedEventId.current !== requestedEventId;
    previousRequestedEventId.current = requestedEventId;
    const requestedEventExists = requestedEventId
      && managedEvents.some((event) => String(event.id) === requestedEventId);
    setSelectedEventId((currentEventId) => {
      if (requestedEventChanged && requestedEventExists) return requestedEventId;
      const currentEventExists = currentEventId
        && managedEvents.some((event) => String(event.id) === String(currentEventId));
      if (currentEventExists) return currentEventId;
      return requestedEventExists ? requestedEventId : String(managedEvents[0].id);
    });
  }, [managedEvents, requestedEventId]);

  const selectedEvent = managedEvents.find((event) => String(event.id) === String(selectedEventId));

  useEffect(() => {
    if (!selectedEvent) {
      setRecruitmentStatus(null);
      setRecruitmentLoading(false);
      return;
    }
    if (!selectedEvent.boothRecruitmentEnabled) {
      setRecruitmentStatus("NOT_REQUIRED");
      setRecruitmentLoading(false);
      return;
    }

    let active = true;
    setRecruitmentStatus(null);
    setRecruitmentLoading(true);
    getManagementRecruitment(selectedEvent.id)
      .then((recruitment) => {
        if (active) setRecruitmentStatus(recruitment.status);
      })
      .catch((error) => {
        if (active) setRecruitmentStatus(error.status === 404 ? "NOT_CREATED" : "UNKNOWN");
      })
      .finally(() => {
        if (active) setRecruitmentLoading(false);
      });

    return () => { active = false; };
  }, [selectedEvent?.id, selectedEvent?.boothRecruitmentEnabled]);

  // APP-API-005: 행사 신청 목록 로드 (WBS-197)
  useEffect(() => {
    if (!selectedEventId) { setApplications([]); return; }
    let active = true;
    setAppLoading(true);
    setAppError("");
    setExpandedAppId(null);
    setAppFiles({});
    listEventApplications(selectedEventId, { page: 0, size: 100 })
      .then((data) => { if (active) setApplications(data?.content || []); })
      .catch((error) => { if (active) setAppError(error.message || "신청 목록을 불러오지 못했습니다."); })
      .finally(() => { if (active) setAppLoading(false); });
    return () => { active = false; };
  }, [selectedEventId]);

  // CONTENT-API-001: 공지·자료 목록 로드 (WBS-199)
  useEffect(() => {
    if (!selectedEventId) { setContents([]); return; }
    let active = true;
    setContentLoading(true);
    setContentError("");
    listContents(selectedEventId)
      .then((data) => { if (active) setContents(Array.isArray(data) ? data : []); })
      .catch((error) => { if (active) setContentError(error.message || "공지·자료를 불러오지 못했습니다."); })
      .finally(() => { if (active) setContentLoading(false); });
    return () => { active = false; };
  }, [selectedEventId]);

  // STAT-API-003/004: 대시보드 통계 로드, 최근 30일 (WBS-201)
  useEffect(() => {
    if (!selectedEventId) { setOverview(null); setPopularBooths([]); return; }
    const { from, to } = statPeriod();
    let active = true;
    setStatError("");
    Promise.all([
      getEventOverview(selectedEventId, from, to),
      getPopularBooths(selectedEventId, from, to),
    ])
      .then(([overviewData, popularData]) => {
        if (!active) return;
        setOverview(overviewData);
        setPopularBooths(popularData?.booths || []);
      })
      .catch((error) => {
        if (!active) return;
        setOverview(null);
        setPopularBooths([]);
        setStatError(error.message || "통계를 불러오지 못했습니다.");
      });
    return () => { active = false; };
  }, [selectedEventId]);

  // 행사 변경 시 시간대별 통계 부스 선택 초기화
  useEffect(() => {
    setHourlyBoothId("");
    setHourlyStats([]);
    setHourlyError("");
  }, [selectedEventId]);

  // STAT-API-001: 선택한 부스·날짜의 시간대별 통계 로드
  useEffect(() => {
    if (!hourlyBoothId) { setHourlyStats([]); return; }
    let active = true;
    setHourlyLoading(true);
    setHourlyError("");
    getHourlyStatistics(hourlyBoothId, hourlyDate)
      .then((data) => {
        if (active) setHourlyStats(data?.hourlyStats || []);
      })
      .catch((error) => {
        if (!active) return;
        setHourlyStats([]);
        setHourlyError(error.message || "시간대별 통계를 불러오지 못했습니다.");
      })
      .finally(() => { if (active) setHourlyLoading(false); });
    return () => { active = false; };
  }, [hourlyBoothId, hourlyDate]);

  const submitSelectedEvent = async () => {
    if (!selectedEventId) return;
    setSubmittingEvent(true);
    setEventLoadError("");
    try {
      await eventApi.submit(selectedEventId);
      setManagedEvents((previous) => previous.map((event) =>
        String(event.id) === String(selectedEventId) ? { ...event, status: "SUBMITTED" } : event));
    } catch (error) {
      setEventLoadError(error.message || "승인 요청에 실패했습니다.");
    } finally {
      setSubmittingEvent(false);
    }
  };

  const publishSelectedEvent = async () => {
    if (!selectedEventId || selectedEvent?.status !== "APPROVED") return;
    setPublishingEvent(true);
    setEventLoadError("");
    try {
      await eventApi.publish(selectedEventId);
      setManagedEvents((previous) => previous.map((event) =>
        String(event.id) === String(selectedEventId) ? { ...event, status: "PUBLISHED" } : event));
    } catch (error) {
      setEventLoadError(error.message || "행사 공개에 실패했습니다.");
    } finally {
      setPublishingEvent(false);
    }
  };

  // 검토 대기 = 신청됨(SUBMITTED) + 검토 중(UNDER_REVIEW)
  const pending = useMemo(
    () => applications.filter((a) => a.status === "SUBMITTED" || a.status === "UNDER_REVIEW"),
    [applications]
  );
  const approvedCount = useMemo(
    () => applications.filter((a) => a.status === "APPROVED").length,
    [applications]
  );
  const requestableStatus = ["PREPARING", "REJECTED"].includes(selectedEvent?.status);
  const platformApproved = ["APPROVED", "PUBLISHED"].includes(selectedEvent?.status);
  const recruitmentReady = !selectedEvent?.boothRecruitmentEnabled || recruitmentStatus === "COMPLETED";
  const approvalRequestReady = Boolean(selectedEventId) && requestableStatus && recruitmentReady && !recruitmentLoading;
  const approvalGuide = !selectedEvent
    ? "행사를 선택해 주세요."
    : !requestableStatus
      ? selectedEvent.status === "SUBMITTED" || selectedEvent.status === "UNDER_REVIEW"
        ? "플랫폼 관리자 검토를 기다리고 있습니다."
        : platformApproved
          ? selectedEvent.status === "PUBLISHED"
            ? "행사가 공개되어 사용자 행사 목록에 노출되고 있습니다."
            : "플랫폼 관리자 승인이 완료되었습니다. 행사를 공개할 수 있습니다."
          : `현재 ${selectedEvent.status} 상태에서는 승인 요청을 할 수 없습니다.`
      : !selectedEvent.boothRecruitmentEnabled
        ? "부스 모집을 사용하지 않는 행사로, 승인 요청이 가능합니다."
        : recruitmentLoading
          ? "부스 모집 공고 상태를 확인하고 있습니다."
          : recruitmentStatus === "COMPLETED"
            ? "부스 모집 공고가 완료되어 승인 요청이 가능합니다."
            : recruitmentStatus === "NOT_CREATED"
              ? "부스 모집 공고를 등록하고 완료 처리해야 합니다."
              : recruitmentStatus === "UNKNOWN"
                ? "부스 모집 공고 상태를 확인하지 못했습니다."
                : `부스 모집 공고를 완료해야 합니다. (현재 ${recruitmentStatus || "확인 중"})`;

  // 목록 내 특정 신청의 상태만 갱신하는 공통 헬퍼
  const patchApplicationStatus = (applicationId, status) =>
    setApplications((prev) => prev.map((a) => (a.id === applicationId ? { ...a, status } : a)));

  // APP-API-006: 검토 시작 (WBS-197)
  const handleStartReview = async (applicationId) => {
    try {
      await startReview(applicationId);
      patchApplicationStatus(applicationId, "UNDER_REVIEW");
    } catch (error) {
      setAppError(error.message || "검토 시작에 실패했습니다.");
    }
  };

  // APP-API-007: 승인·부스 배정 (WBS-198)
  const handleApprove = async (applicationId) => {
    if (!window.confirm("이 신청을 승인하시겠습니까? 승인 시 부스가 자동 배정됩니다.")) return;
    try {
      await approveApplication(applicationId);
      patchApplicationStatus(applicationId, "APPROVED");
    } catch (error) {
      setAppError(error.message || "승인에 실패했습니다.");
    }
  };

  // APP-API-008: 반려 - 사유 입력 모달을 거쳐 확정 (WBS-198)
  const openRejectModal = (application) => {
    setRejectTarget(application);
    setRejectReason("");
  };

  const handleReject = async () => {
    if (!rejectTarget || !rejectReason.trim() || rejecting) return;
    setRejecting(true);
    try {
      await rejectApplication(rejectTarget.id, rejectReason.trim());
      patchApplicationStatus(rejectTarget.id, "REJECTED");
      setRejectTarget(null);
    } catch (error) {
      setAppError(error.message || "반려에 실패했습니다.");
    } finally {
      setRejecting(false);
    }
  };

  // APP-API-009: 신청서 행 펼침 시 첨부파일 1회 로드
  const toggleAppDetail = async (applicationId) => {
    const next = expandedAppId === applicationId ? null : applicationId;
    setExpandedAppId(next);
    if (next && !appFiles[next]) {
      try {
        const fileList = await listApplicationFiles(next);
        setAppFiles((prev) => ({ ...prev, [next]: fileList || [] }));
      } catch {
        setAppFiles((prev) => ({ ...prev, [next]: [] }));
      }
    }
  };

  // CONTENT-API-003/004: 공지·자료 등록/수정 (WBS-200)
  const handleContentSave = async () => {
    if (!contentForm || !selectedEventId || contentSaving) return;
    if (!contentForm.title.trim()) return;
    setContentSaving(true);
    setSaveError(""); // 저장 에러는 모달 내부에만 표시
    try {
      // BE EventContentDtos.CreateRequest/UpdateRequest 필드에 맞춰 구성
      const data = {
        contentType: contentForm.contentType,
        resourceType: contentForm.contentType === "RESOURCE" ? contentForm.resourceType.trim() || null : null,
        audience: contentForm.audience,
        title: contentForm.title.trim(),
        content: contentForm.body,
        version: contentForm.version.trim() || null,
        pinned: contentForm.pinned,
      };
      if (contentForm.contentId) {
        const updated = await updateContent(contentForm.contentId, data, contentFile || undefined);
        setContents((prev) => prev.map((c) => (c.contentId === contentForm.contentId ? updated : c)));
      } else {
        const created = await createContent(selectedEventId, data, contentFile || undefined);
        setContents((prev) => [created, ...prev]);
      }
      setContentForm(null);
      setContentFile(null);
      setSaveError("");
    } catch (error) {
      // 저장 실패 에러를 모달 내부에 표시 (페이지 레벨 contentError 사용 안 함)
      setSaveError(error.message || "공지·자료 저장에 실패했습니다.");
    } finally {
      setContentSaving(false);
    }
  };

  // CONTENT-API-005: 공지·자료 삭제 (WBS-200)
  const handleContentDelete = async (contentId) => {
    if (!window.confirm("이 공지·자료를 삭제하시겠습니까?")) return;
    try {
      await deleteContent(contentId);
      setContents((prev) => prev.filter((c) => c.contentId !== contentId));
    } catch (error) {
      setContentError(error.message || "삭제에 실패했습니다.");
    }
  };

  // 수정 버튼: 기존 값으로 폼 채우기
  const openContentEdit = (content) => {
    setContentForm({
      contentId: content.contentId,
      contentType: content.contentType,
      audience: content.audience,
      title: content.title,
      body: content.content || "",
      resourceType: content.resourceType || "",
      version: content.version || "",
      pinned: content.pinned,
    });
    setContentFile(null);
    setFileError("");
    setSaveError("");
  };

  const gotoPage = (key) => {
    setPage(key);
    if (window.innerWidth < 768) setSidebarOpen(false);
  };

  const navBtnCls = (active) =>
    `group w-full flex items-center gap-sm border-l-[3px] px-md py-sm rounded-r-xl font-body text-left transition-all ${
      active ? "border-primary bg-primary/10 text-primary font-body-strong shadow-sm" : "border-transparent text-on-surface-variant hover:border-primary/30 hover:bg-surface-container"
    }`;

  // 신청서 행: 클릭 시 상세·첨부파일 펼침, 상태별 액션 버튼 노출 (WBS-197/198)
  const AppRow = ({ a, compact = false }) => {
    const badge = APP_BADGE[a.status] ?? { label: a.status, cls: "bg-surface-container text-ink-muted" };
    const expanded = !compact && expandedAppId === a.id;
    return (
      <div>
        <div
          className="flex items-center gap-sm p-lg cursor-pointer hover:bg-surface-pearl/50 transition-colors"
          onClick={() => !compact && toggleAppDetail(a.id)}
        >
          <div className="w-10 h-10 rounded-lg bg-surface-container flex items-center justify-center flex-shrink-0">
            <Icon name="description" className="text-[18px] text-ink-muted" />
          </div>
          <div className="flex-1 min-w-0">
            <p className="font-body-strong text-[14px] truncate">{a.teamName}</p>
            <p className="text-caption text-ink-muted">신청번호 {a.applicationNo} · 제출일 {formatDate(a.submittedAt)}</p>
          </div>
          <span className={`text-[11px] font-bold px-sm py-1 rounded-full whitespace-nowrap ${badge.cls}`}>{badge.label}</span>
          {/* 상태별 액션: SUBMITTED→검토 시작, UNDER_REVIEW→승인/반려 */}
          {a.status === "SUBMITTED" && (
            <button
              onClick={(e) => { e.stopPropagation(); handleStartReview(a.id); }}
              className="text-[11px] font-bold px-sm py-1 rounded-full border border-status-pending text-status-pending hover:bg-status-pending/10 transition-colors whitespace-nowrap"
            >
              검토 시작
            </button>
          )}
          {a.status === "UNDER_REVIEW" && (
            <div className="flex gap-xs">
              <button
                onClick={(e) => { e.stopPropagation(); handleApprove(a.id); }}
                title="승인"
                className="w-8 h-8 rounded-full bg-status-available/10 text-status-available flex items-center justify-center"
              >
                <Icon name="check" className="text-[16px]" />
              </button>
              <button
                onClick={(e) => { e.stopPropagation(); openRejectModal(a); }}
                title="반려"
                className="w-8 h-8 rounded-full bg-status-visited/10 text-status-visited flex items-center justify-center"
              >
                <Icon name="close" className="text-[16px]" />
              </button>
            </div>
          )}
          {!compact && <Icon name={expanded ? "expand_less" : "expand_more"} className="text-ink-muted text-[18px]" />}
        </div>

        {/* 펼침 상세: 신청 내용 + 첨부파일 */}
        {expanded && (
          <div className="px-lg pb-lg space-y-md bg-surface-pearl/30">
            <div className="grid grid-cols-2 gap-sm text-caption pt-md">
              <p><span className="text-ink-muted">담당자</span> {a.contactName}</p>
              <p><span className="text-ink-muted">연락처</span> {a.contactPhone}</p>
              <p className="col-span-2"><span className="text-ink-muted">이메일</span> {a.contactEmail}</p>
              {a.expectedVisitors != null && <p><span className="text-ink-muted">예상 방문객</span> {a.expectedVisitors}명</p>}
              <p className="col-span-2">
                <span className="text-ink-muted">설비</span>{" "}
                {[
                  a.electricityRequired && "전기",
                  a.waterRequired && "급수",
                  a.drainageRequired && "배수",
                  a.internetRequired && "인터넷",
                ].filter(Boolean).join(", ") || "요청 없음"}
              </p>
            </div>
            <div className="space-y-xs">
              <p className="text-caption font-body-strong">활동 소개</p>
              <p className="text-caption bg-white rounded-lg p-md whitespace-pre-line">{a.activityDescription}</p>
            </div>
            <div className="space-y-xs">
              <p className="text-caption font-body-strong">전시 내용</p>
              <p className="text-caption bg-white rounded-lg p-md whitespace-pre-line">{a.exhibitionContent}</p>
            </div>
            <div className="space-y-xs">
              <p className="text-caption font-body-strong">첨부파일</p>
              {appFiles[a.id] === undefined ? (
                <p className="text-caption text-ink-muted">불러오는 중...</p>
              ) : appFiles[a.id].length === 0 ? (
                <p className="text-caption text-ink-muted">첨부파일이 없습니다.</p>
              ) : (
                <div className="flex flex-col gap-xs items-start">
                  {appFiles[a.id].map((file) => (
                    <FileDownloadLink
                      key={file.fileId}
                      downloadUrl={file.downloadUrl}
                      fileId={file.fileId}
                      fileName={`[${file.fileType === "ESTIMATE" ? "견적서" : "기타"}] ${file.originalName}`}
                      fileSize={file.fileSize}
                      className="bg-white"
                    />
                  ))}
                </div>
              )}
            </div>
          </div>
        )}
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-surface-container-lowest pt-[44px] text-on-surface">
      <TopNav active="organizer" />
      {/* Sidebar */}
      <aside className={`fixed bottom-0 left-0 top-[44px] w-[280px] bg-white border-r border-hairline z-50 flex flex-col shadow-[12px_0_40px_rgba(15,23,42,0.04)] transition-transform md:translate-x-0 ${sidebarOpen ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="border-b border-hairline bg-gradient-to-br from-primary/10 via-white to-primary-container/10 px-lg py-lg flex items-start justify-between">
          <div>
            <span className="text-[10px] font-bold tracking-[0.18em] text-primary">EVENTODAY</span>
            <h1 className="mt-1 font-display-md text-[20px]">개최자센터</h1>
            <p className="mt-1 text-[11px] text-ink-muted">행사 운영을 한곳에서 관리하세요</p>
          </div>
          <button type="button" aria-label="개최자센터 메뉴 닫기" onClick={() => setSidebarOpen(false)} className="md:hidden"><Icon name="close" /></button>
        </div>
        <div className="px-md pt-md">
          <Link to={`/organizer-admin/events/new?organizationId=${organizationId || ""}`} className="flex w-full items-center justify-center gap-xs rounded-xl bg-primary px-md py-sm text-caption font-body-strong text-white shadow-sm transition hover:brightness-95">
            <Icon name="add_circle" className="text-[18px]" /> 새 행사 등록
          </Link>
        </div>
        <nav className="flex-1 overflow-y-auto px-md py-md space-y-1" aria-label="개최자센터 메뉴">
          <p className="px-sm pb-xs text-[10px] font-bold tracking-[0.14em] text-ink-muted">행사 운영</p>
          {navItems.map((n) => (
            <button key={n.key} onClick={() => gotoPage(n.key)} className={navBtnCls(page === n.key)}>
              <Icon name={n.icon} /><span>{n.label}</span>
            </button>
          ))}
          <p className="px-sm pb-xs pt-md text-[10px] font-bold tracking-[0.14em] text-ink-muted">홍보</p>
          <Link to={`/organizer-admin/advertisements?organizationId=${organizationId || ""}`} className={navBtnCls(false)}>
            <Icon name="ads_click" /><span>광고 신청·관리</span>
          </Link>
        </nav>
        <div className="p-lg border-t border-hairline space-y-4">
          <div className="flex items-center gap-sm">
            <div className="w-8 h-8 rounded-full bg-surface-container-high flex items-center justify-center"><Icon name="person" className="text-[18px]" /></div>
            <div className="flex flex-col"><span className="text-caption font-body-strong">김운영 매니저</span><span className="text-[10px] text-ink-muted">Organizer Admin</span></div>
          </div>
          <Link to="/" className="block w-full py-xs text-center text-caption text-secondary border border-hairline rounded-lg hover:bg-surface-container transition-colors">메인 사이트로</Link>
        </div>
      </aside>
      {sidebarOpen && <div onClick={() => setSidebarOpen(false)} className="fixed inset-0 bg-black/40 z-40 md:hidden" />}

      {/* Main */}
      <main className="min-h-[calc(100vh-44px)] md:ml-[280px]">
        <header className="sticky top-[44px] z-30 bg-white/70 backdrop-blur-xl border-b border-hairline px-lg h-[64px] flex items-center justify-between">
          <div className="flex items-center gap-sm">
            <button onClick={() => setSidebarOpen(true)} className="md:hidden"><Icon name="menu" /></button>
            <h2 className="font-display-md text-[20px] text-on-surface">{navItems.find((n) => n.key === page).label}</h2>
          </div>
          <Link to={`/organizer-admin/events/new?organizationId=${organizationId || ""}`} className="px-md py-xs bg-primary text-white text-caption rounded-full font-body-strong active:scale-95 transition-transform">+ 새 전시회 등록</Link>
        </header>

        <div className="p-lg md:p-xl space-y-section max-w-[1200px] mx-auto">
          {managedOrganizations.length > 1 && (
            <div className="bg-white border border-hairline rounded-xl p-lg flex items-center gap-sm flex-wrap">
              <label className="text-caption text-ink-muted whitespace-nowrap">소속 조직</label>
              <select
                value={organizationId}
                onChange={(event) => {
                  setOrganizationId(event.target.value);
                  setSelectedEventId("");
                }}
                className="border border-hairline rounded-lg px-md py-1.5 text-caption bg-white min-w-[240px]"
              >
                {managedOrganizations.map((org) => (
                  <option key={org.id} value={org.id}>{org.name}</option>
                ))}
              </select>
            </div>
          )}
          {managedOrganizations.length === 0 && eventLoadError && (
            <div className="bg-white border border-hairline rounded-xl p-lg text-caption text-error">
              {eventLoadError}
            </div>
          )}
          {page !== "dashboard" && (
            <div className="bg-white border border-hairline rounded-xl p-lg flex items-center gap-sm flex-wrap">
              <label className="text-caption text-ink-muted whitespace-nowrap">관리 중인 행사</label>
              <select
                value={selectedEventId}
                onChange={(event) => setSelectedEventId(event.target.value)}
                className="border border-hairline rounded-lg px-md py-1.5 text-caption bg-white min-w-[240px]"
              >
                <option value="">행사를 선택하세요</option>
                {managedEvents.map((event) => (
                  <option key={event.id} value={event.id}>{event.name} · {event.status}</option>
                ))}
              </select>
              {eventLoadError && <span className="text-caption text-error">{eventLoadError}</span>}
            </div>
          )}

          {/* DASHBOARD */}
          {page === "dashboard" && (
            <section className="space-y-xl">
              <div>
                <h1 className="font-display-lg text-[28px] md:text-display-lg text-on-surface">운영 현황</h1>
                <p className="text-lead text-on-surface-variant">{selectedEvent?.name || "행사를 선택해 주세요"}</p>
                <p className="text-caption text-ink-muted mt-1">최근 30일 기준 통계입니다.</p>
              </div>
              {statError && <p className="text-caption text-error">{statError}</p>}
              {/* STAT-API-004 overview 기반 통계 카드 */}
              <div className="grid grid-cols-1 md:grid-cols-4 gap-lg">
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">검토 대기 신청서</span><Icon name="schedule" className="text-status-pending" /></div>
                  <span className="font-display-md text-[26px]">{appLoading ? "..." : pending.length}</span>
                </div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">승인된 신청서</span><Icon name="grid_view" className="text-status-assigned" /></div>
                  <span className="font-display-md text-[26px]">{appLoading ? "..." : approvedCount}</span>
                </div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">부스 예약 건수</span><Icon name="group" className="text-status-available" /></div>
                  <span className="font-display-md text-[26px]">{overview ? overview.totalReservationCount.toLocaleString() : "-"}</span>
                </div>
                <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                  <div className="flex justify-between items-start mb-md"><span className="text-caption text-on-surface-variant">QR 스캔(방문)</span><Icon name="trending_up" className="text-status-visited" /></div>
                  <span className="font-display-md text-[26px]">{overview ? overview.totalQrScanCount.toLocaleString() : "-"}</span>
                </div>
              </div>

              {/* STAT-API-003 인기 부스 TOP 5 */}
              <div className="bg-white border border-hairline rounded-xl overflow-hidden">
                <div className="px-lg py-md border-b border-hairline">
                  <h3 className="font-body-strong">인기 부스 TOP 5</h3>
                </div>
                <div className="divide-y divide-divider-soft">
                  {popularBooths.length === 0 ? (
                    <p className="p-lg text-caption text-ink-muted">집계된 부스 통계가 없습니다.</p>
                  ) : (
                    popularBooths.slice(0, 5).map((booth) => (
                      <div key={booth.boothId} className="flex items-center gap-md p-lg">
                        <span className={`w-7 h-7 rounded-full flex items-center justify-center text-[12px] font-bold ${booth.rank <= 3 ? "bg-primary text-white" : "bg-surface-container text-ink-muted"}`}>
                          {booth.rank}
                        </span>
                        <span className="font-body-strong text-[14px] flex-1">{booth.boothCode} 부스</span>
                        <span className="text-caption text-ink-muted">예약 {booth.totalReservationCount.toLocaleString()}건</span>
                        <span className="text-caption text-ink-muted">방문 {booth.totalQrScanCount.toLocaleString()}건</span>
                      </div>
                    ))
                  )}
                </div>
              </div>

              {/* STAT-API-001 시간대별 부스 통계: 부스·날짜 선택 후 시간대별 막대 차트 표시 */}
              <div className="bg-white border border-hairline rounded-xl overflow-hidden">
                <div className="flex flex-wrap justify-between items-center gap-sm px-lg py-md border-b border-hairline">
                  <h3 className="font-body-strong">시간대별 부스 통계</h3>
                  <div className="flex items-center gap-sm">
                    {/* 부스 선택: overview 응답의 boothSummaries 활용 (추가 API 호출 없음) */}
                    <select
                      value={hourlyBoothId}
                      onChange={(e) => setHourlyBoothId(e.target.value)}
                      className="h-[36px] px-sm border border-hairline rounded-lg text-caption bg-white"
                    >
                      <option value="">부스 선택</option>
                      {(overview?.boothSummaries || []).map((booth) => (
                        <option key={booth.boothId} value={booth.boothId}>{booth.boothCode} 부스</option>
                      ))}
                    </select>
                    <input
                      type="date"
                      value={hourlyDate}
                      onChange={(e) => setHourlyDate(e.target.value)}
                      className="h-[36px] px-sm border border-hairline rounded-lg text-caption bg-white"
                    />
                  </div>
                </div>
                <div className="p-lg">
                  {!hourlyBoothId ? (
                    <p className="text-caption text-ink-muted">부스를 선택하면 시간대별 예약·방문 통계가 표시됩니다.</p>
                  ) : hourlyLoading ? (
                    <p className="text-caption text-ink-muted">통계를 불러오는 중...</p>
                  ) : hourlyError ? (
                    <p className="text-caption text-error">{hourlyError}</p>
                  ) : hourlyStats.length === 0 ? (
                    <p className="text-caption text-ink-muted">해당 날짜에 집계된 통계가 없습니다.</p>
                  ) : (
                    <HourlyBarChart stats={hourlyStats} />
                  )}
                </div>
              </div>

              <div className="bg-white border border-hairline rounded-xl overflow-hidden">
                <div className="flex justify-between items-center px-lg py-md border-b border-hairline">
                  <h3 className="font-body-strong">검토 대기 신청서</h3>
                  <button onClick={() => gotoPage("applications")} className="text-caption text-primary font-body-strong">전체 보기</button>
                </div>
                <div className="divide-y divide-divider-soft">
                  {pending.length === 0 ? (
                    <p className="p-lg text-caption text-ink-muted">검토할 신청서가 없습니다.</p>
                  ) : (
                    pending.map((a) => <AppRow key={a.id} a={a} compact />)
                  )}
                </div>
              </div>
            </section>
          )}

          {/* RECRUITMENT */}
          {page === "recruitment" && <RecruitmentManagementPanel eventId={selectedEventId} />}

          {/* APPLICATIONS (WBS-197/198) */}
          {page === "applications" && (
            <section className="space-y-lg">
              <div>
                <h1 className="font-display-lg text-[26px]">부스 신청서 검토</h1>
                <p className="text-caption text-ink-muted mt-xs">
                  검토 시작 후 승인 또는 반려를 선택하세요. 승인하면 부스가 자동 배정되고, 반려하면 부스가 다시 선택 가능 상태로 복원됩니다.
                </p>
              </div>
              {appError && <p className="text-caption text-error bg-error/10 rounded-lg p-sm">{appError}</p>}
              {appLoading ? (
                <p className="text-caption text-ink-muted">신청 목록을 불러오는 중...</p>
              ) : (
                <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
                  {applications.length === 0 ? (
                    <p className="p-lg text-caption text-ink-muted">
                      {selectedEventId ? "접수된 신청서가 없습니다." : "행사를 먼저 선택해 주세요."}
                    </p>
                  ) : (
                    applications.map((a) => <AppRow key={a.id} a={a} />)
                  )}
                </div>
              )}
            </section>
          )}

          {/* CONTENTS: 공지·자료 관리 (WBS-199/200) */}
          {page === "contents" && (
            <section className="space-y-lg">
              <div className="flex items-center justify-between flex-wrap gap-sm">
                <div>
                  <h1 className="font-display-lg text-[26px]">공지·자료 관리</h1>
                  <p className="text-caption text-ink-muted mt-xs">행사 공지사항과 자료를 등록·수정·삭제합니다.</p>
                </div>
                <button
                  onClick={() => { setContentForm({ ...emptyContentForm }); setContentFile(null); setFileError(""); setSaveError(""); }}
                  disabled={!selectedEventId}
                  className="px-md py-sm bg-primary text-white text-caption rounded-full font-body-strong disabled:opacity-40"
                >
                  + 새 공지·자료
                </button>
              </div>

              {contentError && <p className="text-caption text-error bg-error/10 rounded-lg p-sm">{contentError}</p>}

              {contentLoading ? (
                <p className="text-caption text-ink-muted">공지·자료를 불러오는 중...</p>
              ) : (
                <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
                  {contents.length === 0 ? (
                    <p className="p-lg text-caption text-ink-muted">
                      {selectedEventId ? "등록된 공지·자료가 없습니다." : "행사를 먼저 선택해 주세요."}
                    </p>
                  ) : (
                    contents.map((content) => (
                      <div key={content.contentId} className="flex items-center gap-sm p-lg">
                        <Icon
                          name={content.contentType === "NOTICE" ? "campaign" : "folder"}
                          className="text-[20px] text-ink-muted flex-shrink-0"
                        />
                        <div className="flex-1 min-w-0">
                          <p className="font-body-strong text-[14px] truncate">
                            {content.pinned && <Icon name="push_pin" className="text-[13px] text-primary mr-1" />}
                            {content.title}
                          </p>
                          <p className="text-caption text-ink-muted">
                            {content.contentType === "NOTICE" ? "공지" : "자료"}
                            {" · "}대상 {AUDIENCE_LABEL[content.audience] || content.audience}
                            {content.version ? ` · v${content.version}` : ""}
                            {" · "}{formatDate(content.publishedAt)}
                          </p>
                        </div>
                        {content.fileId && (
                          <FileDownloadLink fileId={content.fileId} fileName="첨부" className="!px-sm !py-1" />
                        )}
                        <button
                          onClick={() => openContentEdit(content)}
                          className="text-caption text-primary border border-primary/30 rounded-lg px-sm py-1 hover:bg-primary/5 transition-colors"
                        >
                          수정
                        </button>
                        <button
                          onClick={() => handleContentDelete(content.contentId)}
                          className="text-caption text-error border border-error/30 rounded-lg px-sm py-1 hover:bg-error/5 transition-colors"
                        >
                          삭제
                        </button>
                      </div>
                    ))
                  )}
                </div>
              )}
            </section>
          )}

          {/* ASSIGNMENT */}
          {page === "assignment" && <BoothManagementPanel eventId={selectedEventId} />}

          {/* FLOORPLAN */}
          {page === "floorplan" && <FloorplanManagementPanel eventId={selectedEventId} />}

          {/* APPROVAL */}
          {page === "approval" && (
            <section className="space-y-lg">
              <h1 className="font-display-lg text-[26px]">행사 등록 승인 요청</h1>
              {selectedEventId && (
                <div className="bg-white border border-hairline rounded-xl p-lg flex gap-sm">
                  <Link to={`/organizer-admin/events/${selectedEventId}/edit?organizationId=${organizationId || ""}`} className="text-caption px-md py-xs border border-hairline rounded-full">행사 수정</Link>
                  <Link to={`/organizer-admin/events/${selectedEventId}/members?organizationId=${organizationId || ""}`} className="text-caption px-md py-xs border border-hairline rounded-full">담당자 관리</Link>
                </div>
              )}
              <div className="bg-primary-fixed/20 border border-primary-fixed rounded-xl p-lg flex items-center justify-between gap-lg flex-wrap">
                <div>
                  <p className="font-body-strong">{selectedEvent ? `${selectedEvent.name} · ${selectedEvent.status}` : "행사를 선택해주세요"}</p>
                  <p className="text-caption text-ink-muted mt-1">
                    {approvalGuide}
                  </p>
                </div>
                {selectedEvent?.status === "APPROVED" ? (
                  <button onClick={publishSelectedEvent} disabled={publishingEvent} className="px-xl py-sm bg-primary text-white rounded-full font-body-strong disabled:opacity-40 disabled:cursor-not-allowed">
                    {publishingEvent ? "공개 중..." : "행사 공개하기"}
                  </button>
                ) : selectedEvent?.status === "PUBLISHED" ? (
                  <button disabled className="px-xl py-sm bg-status-available text-white rounded-full font-body-strong opacity-80 cursor-default">공개 중</button>
                ) : (
                  <button onClick={submitSelectedEvent} disabled={!approvalRequestReady || submittingEvent} className="px-xl py-sm bg-primary text-white rounded-full font-body-strong disabled:opacity-40 disabled:cursor-not-allowed">{submittingEvent ? "요청 중..." : "승인 요청하기"}</button>
                )}
              </div>
              <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
                <div className="flex items-center gap-sm p-lg"><span className="w-6 h-6 rounded-full bg-status-available text-white flex items-center justify-center"><Icon name="check" className="text-[14px]" /></span>행사 기본정보 등록 <span className="text-ink-muted text-caption ml-auto">완료</span></div>
                <div className="flex items-center gap-sm p-lg">
                  <span className={`w-6 h-6 rounded-full text-white flex items-center justify-center ${selectedEvent?.venueMapEnabled ? "bg-status-pending" : "bg-status-available"}`}>
                    <Icon name={selectedEvent?.venueMapEnabled ? "schedule" : "check"} className="text-[14px]" />
                  </span>
                  행사장 평면도 및 부스 좌표 등록
                  <span className="text-ink-muted text-caption ml-auto">{selectedEvent?.venueMapEnabled ? "평면도 파트 연동 필요" : "해당 없음"}</span>
                </div>
                <div className="flex items-center gap-sm p-lg">
                  <span className={`w-6 h-6 rounded-full text-white flex items-center justify-center ${recruitmentReady ? "bg-status-available" : "bg-status-pending"}`}><Icon name={recruitmentReady ? "check" : "schedule"} className="text-[14px]" /></span>
                  부스 모집 공고 완료 <span className="text-ink-muted text-caption ml-auto">{!selectedEvent?.boothRecruitmentEnabled ? "해당 없음" : recruitmentStatus === "COMPLETED" ? "완료" : recruitmentStatus || "확인 중"}</span>
                </div>
                <div className="flex items-center gap-sm p-lg">
                  <span className={`w-6 h-6 rounded-full text-white flex items-center justify-center ${platformApproved ? "bg-status-available" : "bg-status-blocked"}`}>
                    <Icon name={platformApproved ? "check" : "chevron_right"} className="text-[14px]" />
                  </span>
                  플랫폼 관리자 승인
                  <span className="text-ink-muted text-caption ml-auto">{platformApproved ? "승인 완료" : selectedEvent?.status === "REJECTED" ? "반려" : "대기"}</span>
                </div>
              </div>
            </section>
          )}
        </div>
      </main>

      {/* 반려 사유 입력 모달 (WBS-198) - UNDER_REVIEW 상태에서 반려 버튼 클릭 시 */}
      {rejectTarget && (
        <div className="fixed inset-0 z-[110] bg-black/50 grid place-items-center p-lg" onMouseDown={(e) => e.target === e.currentTarget && setRejectTarget(null)}>
          <section role="dialog" aria-modal="true" className="w-full max-w-[400px] bg-white rounded-2xl shadow-2xl overflow-hidden">
            <div className="flex items-center justify-between px-lg py-md border-b border-hairline">
              <h2 className="font-body-strong text-[16px]">신청 반려</h2>
              <button onClick={() => setRejectTarget(null)} aria-label="닫기"><Icon name="close" className="text-ink-muted" /></button>
            </div>
            <div className="px-lg py-lg space-y-md">
              <p className="text-caption text-ink-muted">
                <span className="font-body-strong text-on-surface">{rejectTarget.teamName}</span>
                (신청번호 {rejectTarget.applicationNo})의 신청을 반려합니다. 반려 시 부스는 다시 선택 가능 상태로 복원됩니다.
              </p>
              <div className="space-y-xs">
                <label className="text-caption font-body-strong">반려 사유 (필수)</label>
                <textarea
                  value={rejectReason}
                  onChange={(e) => setRejectReason(e.target.value)}
                  rows={4}
                  placeholder="반려 사유를 입력하세요"
                  className="w-full border border-hairline rounded-lg px-md py-sm text-caption resize-none focus:border-primary-focus outline-none"
                />
              </div>
            </div>
            <div className="flex gap-sm px-lg py-md border-t border-hairline">
              <button onClick={() => setRejectTarget(null)} className="flex-1 h-[44px] rounded-xl border border-hairline text-caption">취소</button>
              <button
                onClick={handleReject}
                disabled={rejecting || !rejectReason.trim()}
                className="flex-1 h-[44px] rounded-xl bg-status-visited text-white text-caption font-body-strong disabled:opacity-40"
              >
                {rejecting ? "처리 중..." : "반려 확정"}
              </button>
            </div>
          </section>
        </div>
      )}

      {/* 공지·자료 등록/수정 모달 (WBS-200) */}
      {contentForm && (
        <div className="fixed inset-0 z-[110] bg-black/50 grid place-items-center p-lg" onMouseDown={(e) => e.target === e.currentTarget && setContentForm(null)}>
          <section role="dialog" aria-modal="true" className="w-full max-w-[520px] bg-white rounded-2xl shadow-2xl flex flex-col max-h-[90vh]">
            <div className="flex items-center justify-between px-lg py-md border-b border-hairline">
              <h2 className="font-body-strong text-[16px]">{contentForm.contentId ? "공지·자료 수정" : "새 공지·자료 등록"}</h2>
              <button onClick={() => setContentForm(null)} aria-label="닫기"><Icon name="close" className="text-ink-muted" /></button>
            </div>
            <div className="flex-1 overflow-y-auto px-lg py-lg space-y-md">
              <div className="grid grid-cols-2 gap-sm">
                <div className="space-y-xs">
                  <label className="text-caption font-body-strong">유형</label>
                  <select
                    value={contentForm.contentType}
                    onChange={(e) => setContentForm((f) => ({ ...f, contentType: e.target.value }))}
                    className="w-full border border-hairline rounded-lg px-md py-sm text-caption bg-white"
                  >
                    <option value="NOTICE">공지</option>
                    <option value="RESOURCE">자료</option>
                  </select>
                </div>
                <div className="space-y-xs">
                  <label className="text-caption font-body-strong">공개 대상</label>
                  <select
                    value={contentForm.audience}
                    onChange={(e) => setContentForm((f) => ({ ...f, audience: e.target.value }))}
                    className="w-full border border-hairline rounded-lg px-md py-sm text-caption bg-white"
                  >
                    <option value="ALL">전체</option>
                    <option value="EXHIBITOR">참가기업</option>
                    <option value="VISITOR">방문자</option>
                  </select>
                </div>
              </div>
              {/* 자료 유형·버전은 RESOURCE일 때만 노출 */}
              {contentForm.contentType === "RESOURCE" && (
                <div className="grid grid-cols-2 gap-sm">
                  <div className="space-y-xs">
                    <label className="text-caption font-body-strong">자료 유형 (선택)</label>
                    <input
                      type="text"
                      value={contentForm.resourceType}
                      onChange={(e) => setContentForm((f) => ({ ...f, resourceType: e.target.value }))}
                      placeholder="예: 매뉴얼, 서식"
                      className="w-full border border-hairline rounded-lg px-md py-sm text-caption"
                    />
                  </div>
                  <div className="space-y-xs">
                    <label className="text-caption font-body-strong">버전 (선택)</label>
                    <input
                      type="text"
                      value={contentForm.version}
                      onChange={(e) => setContentForm((f) => ({ ...f, version: e.target.value }))}
                      placeholder="예: 1.0"
                      className="w-full border border-hairline rounded-lg px-md py-sm text-caption"
                    />
                  </div>
                </div>
              )}
              <div className="space-y-xs">
                <label className="text-caption font-body-strong">제목 *</label>
                <input
                  type="text"
                  value={contentForm.title}
                  onChange={(e) => setContentForm((f) => ({ ...f, title: e.target.value }))}
                  placeholder="제목을 입력하세요 (최대 200자)"
                  maxLength={200}
                  className="w-full border border-hairline rounded-lg px-md py-sm text-caption"
                />
              </div>
              <div className="space-y-xs">
                <label className="text-caption font-body-strong">내용</label>
                <textarea
                  value={contentForm.body}
                  onChange={(e) => setContentForm((f) => ({ ...f, body: e.target.value }))}
                  rows={5}
                  placeholder="내용을 입력하세요"
                  className="w-full border border-hairline rounded-lg px-md py-sm text-caption resize-none"
                />
              </div>
              {/* 자료(RESOURCE)일 때만 파일 첨부 노출 — 공지(NOTICE)에는 파일 첨부 불필요 */}
              {contentForm.contentType === "RESOURCE" && (
                <div className="space-y-xs">
                  <label className="text-caption font-body-strong">
                    첨부 파일 <span className="text-ink-muted font-normal">(선택)</span>
                  </label>
                  {/* 지원 파일 형식 안내 — BE FileService ALLOWED_EXTENSIONS 기준 */}
                  <p className="text-[11px] text-ink-muted">
                    지원 형식: PDF, Word(.doc/.docx), 이미지(JPG/JPEG/PNG/GIF/WebP) · 최대 10MB
                  </p>
                  <input
                    type="file"
                    accept=".pdf,.doc,.docx,.jpg,.jpeg,.png,.gif,.webp"
                    onChange={(e) => {
                      const file = e.target.files?.[0] || null;
                      setFileError("");
                      if (file) {
                        // BE FileService.ALLOWED_EXTENSIONS와 동일하게 확장자 검사
                        const ext = file.name.split(".").pop().toLowerCase();
                        const allowedExts = ["pdf", "doc", "docx", "jpg", "jpeg", "png", "gif", "webp"];
                        if (!allowedExts.includes(ext)) {
                          setFileError("허용되지 않는 파일 형식입니다. 위 지원 형식을 확인해 주세요.");
                          e.target.value = "";
                          return;
                        }
                      }
                      setContentFile(file);
                    }}
                    className="text-caption"
                  />
                  {/* 파일 형식 에러 — 모달 내부에만 표시 */}
                  {fileError && <p className="text-[11px] text-error">{fileError}</p>}
                  {contentFile && !fileError && (
                    <p className="text-[11px] text-ink-muted">선택됨: {contentFile.name}</p>
                  )}
                  {contentForm.contentId && !contentFile && !fileError && (
                    <p className="text-[11px] text-ink-muted">파일을 선택하지 않으면 기존 첨부가 유지됩니다.</p>
                  )}
                </div>
              )}
              <label className="flex items-center gap-sm text-caption cursor-pointer">
                <input
                  type="checkbox"
                  checked={contentForm.pinned}
                  onChange={(e) => setContentForm((f) => ({ ...f, pinned: e.target.checked }))}
                  className="rounded"
                />
                상단 고정
              </label>
            </div>
            {/* 저장 에러 — 모달 내부 하단에 표시 */}
            {saveError && (
              <p className="text-[12px] text-error px-lg pb-sm">{saveError}</p>
            )}
            <div className="flex gap-sm px-lg py-md border-t border-hairline">
              <button onClick={() => setContentForm(null)} className="flex-1 h-[44px] rounded-xl border border-hairline text-caption">취소</button>
              <button
                onClick={handleContentSave}
                disabled={contentSaving || !contentForm.title.trim()}
                className="flex-1 h-[44px] rounded-xl bg-primary text-white text-caption font-body-strong disabled:opacity-40"
              >
                {contentSaving ? "저장 중..." : "저장"}
              </button>
            </div>
          </section>
        </div>
      )}
    </div>
  );
}

// 시간대별 통계 막대 차트 (STAT-API-001)
// 별도 차트 라이브러리 없이 CSS 높이 비율로 표현 (예약=파랑, QR 방문=초록)
function HourlyBarChart({ stats }) {
  // 마우스를 올린 시간대 (커스텀 툴팁 표시용)
  const [hoveredHour, setHoveredHour] = useState(null);
  // 0~23시 전체 축을 만들고 데이터가 있는 시간대만 값 채움
  const byHour = new Map(stats.map((s) => [s.statHour, s]));
  const hours = Array.from({ length: 24 }, (_, h) => byHour.get(h) || { statHour: h, reservationCount: 0, noShowCount: 0, qrScanCount: 0 });
  const max = Math.max(1, ...hours.map((s) => Math.max(s.reservationCount, s.qrScanCount, s.noShowCount)));
  // 하루 합산 (예약·QR 방문·노쇼)
  const totals = stats.reduce(
    (acc, s) => ({
      reservation: acc.reservation + s.reservationCount,
      qrScan: acc.qrScan + s.qrScanCount,
      noShow: acc.noShow + s.noShowCount,
    }),
    { reservation: 0, qrScan: 0, noShow: 0 }
  );
  return (
    <div className="space-y-md">
      {/* 합산 카드: 선택한 날짜의 예약·방문·노쇼 총합 */}
      <div className="grid grid-cols-3 gap-sm">
        <div className="bg-surface-pearl rounded-lg p-md text-center">
          <p className="text-caption text-ink-muted">예약</p>
          <p className="font-display-md text-[20px] text-primary">{totals.reservation.toLocaleString()}</p>
        </div>
        <div className="bg-surface-pearl rounded-lg p-md text-center">
          <p className="text-caption text-ink-muted">QR 방문</p>
          <p className="font-display-md text-[20px] text-status-available">{totals.qrScan.toLocaleString()}</p>
        </div>
        <div className="bg-surface-pearl rounded-lg p-md text-center">
          <p className="text-caption text-ink-muted">노쇼</p>
          <p className="font-display-md text-[20px] text-status-visited">{totals.noShow.toLocaleString()}</p>
        </div>
      </div>
      {/* 범례 */}
      <div className="flex gap-lg text-caption text-ink-muted">
        <span className="flex items-center gap-xs"><span className="w-3 h-3 rounded-sm bg-primary inline-block" /> 예약</span>
        <span className="flex items-center gap-xs"><span className="w-3 h-3 rounded-sm bg-status-available inline-block" /> QR 방문</span>
        <span className="flex items-center gap-xs"><span className="w-3 h-3 rounded-sm bg-status-visited inline-block" /> 노쇼</span>
      </div>
      {/* 막대 차트: 시간대별 예약·방문 2개 막대
          %높이는 flex 안에서 계산이 불안정해 막대가 기준선을 벗어나는 문제가 있어
          픽셀 단위로 직접 계산한다 (최대값 = 120px) */}
      <div className="flex gap-[3px] pt-[36px]">
        {hours.map((s) => (
          <div
            key={s.statHour}
            className="flex-1 min-w-0 relative"
            onMouseEnter={() => setHoveredHour(s.statHour)}
            onMouseLeave={() => setHoveredHour(null)}
          >
            {/* 마우스 오버 시 해당 시간대 숫자 툴팁 표시 */}
            {hoveredHour === s.statHour && (
              <div className="absolute -top-[34px] left-1/2 -translate-x-1/2 z-10 bg-on-surface text-white text-[11px] rounded-lg px-sm py-xs whitespace-nowrap pointer-events-none shadow-md">
                {s.statHour}시 · 예약 {s.reservationCount} · 방문 {s.qrScanCount} · 노쇼 {s.noShowCount}
              </div>
            )}
            <div className={`flex items-end justify-center gap-[2px] h-[120px] border-b border-hairline transition-colors ${hoveredHour === s.statHour ? "bg-surface-pearl" : ""}`}>
              <div className="w-1/3 max-w-[8px] bg-primary rounded-t-sm" style={{ height: `${Math.round((s.reservationCount / max) * 120)}px` }} />
              <div className="w-1/3 max-w-[8px] bg-status-available rounded-t-sm" style={{ height: `${Math.round((s.qrScanCount / max) * 120)}px` }} />
              <div className="w-1/3 max-w-[8px] bg-status-visited rounded-t-sm" style={{ height: `${Math.round((s.noShowCount / max) * 120)}px` }} />
            </div>
            {/* 3시간 간격으로만 라벨 표시 (24개 전부 표시하면 좁아서 겹침) */}
            <p className="text-[9px] text-ink-muted text-center mt-[2px] h-[12px]">{s.statHour % 3 === 0 ? s.statHour : ""}</p>
          </div>
        ))}
      </div>
    </div>
  );
}
