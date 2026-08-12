import { useCallback, useEffect, useMemo, useState } from "react";
import { Link } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import TopNav from "../components/TopNav.jsx";
import useAuth from "../hooks/useAuth.js";
import {
  cancelApplication,
  getOrganizationApplications,
} from "../api/boothApplicationApi.js";
import { getPublicRecruitment } from "../api/recruitmentApi.js";
import { listAllPublicBooths, updateBoothIntro } from "../api/boothApi.js";
import { listReservationSlots, createReservationSlot, updateReservationSlot, closeReservationSlot, reopenReservationSlot, deleteReservationSlot, listReservationsForManager, markReservationAttendance } from "../api/boothReservationApi.js";
import { uploadFile, fileDownloadUrl } from "../api/fileApi.js";

const buildReservationSlotRange = (date, startTime, endTime) => {
  const [year, month, day] = date.split("-").map(Number);
  const [startHour, startMinute] = startTime.split(":").map(Number);
  const [endHour, endMinute] = endTime.split(":").map(Number);

  const start = new Date(year, month - 1, day, startHour, startMinute);
  const end = new Date(year, month - 1, day, endHour, endMinute);

  return {
    startAt: start.toISOString(),
    endAt: end.toISOString(),
  };
};

const EMPTY_INTRO_FORM = { displayName: "", shortIntro: "", description: "", exhibitionContent: "" };
// 예약 시간대는 요일을 따지지 않는 시간 블록으로만 설정한다(예: 11:00~11:30).
// 백엔드는 실제 날짜시각으로 저장해야 해서, 오늘 날짜를 내부적으로만 붙여 만든다.
const todayValue = () => {
  const now = new Date();
  const month = String(now.getMonth() + 1).padStart(2, "0");
  const day = String(now.getDate()).padStart(2, "0");
  return `${now.getFullYear()}-${month}-${day}`;
};
const EMPTY_SLOT_FORM = { startTime: "11:00", endTime: "11:30", capacity: "" };

const formatSlotRange = (startAt, endAt) => {
  const timeLabel = (iso) => new Date(iso).toLocaleTimeString("ko-KR", { hour: "2-digit", minute: "2-digit" });
  return `${timeLabel(startAt)}~${timeLabel(endAt)}`;
};

const isoToTimeValue = (iso) => {
  const d = new Date(iso);
  return `${String(d.getHours()).padStart(2, "0")}:${String(d.getMinutes()).padStart(2, "0")}`;
};

const reservationStatusMeta = {
  RESERVED: ["예약중", "bg-status-available/10 text-status-available"],
  CHECKED_IN: ["입장 완료", "bg-primary/10 text-primary"],
  COMPLETED: ["방문 완료", "bg-primary/10 text-primary"],
  NO_SHOW: ["노쇼", "bg-status-visited/10 text-status-visited"],
  CANCELLED: ["취소됨", "bg-surface-container text-ink-muted"],
};

const menuItems = [
  { key: "dashboard", label: "대시보드", icon: "dashboard" },
  { key: "applications", label: "부스 신청 현황", icon: "assignment" },
  { key: "booths", label: "운영 부스", icon: "storefront" },
  { key: "settings", label: "조직·계정 정보", icon: "manage_accounts" },
];

const statusMeta = {
  SUBMITTED: ["신청 완료", "bg-status-pending/10 text-status-pending"],
  UNDER_REVIEW: ["검토 중", "bg-primary/10 text-primary"],
  APPROVED: ["승인", "bg-status-available/10 text-status-available"],
  REJECTED: ["반려", "bg-status-visited/10 text-status-visited"],
  CANCELLED: ["취소", "bg-surface-container text-ink-muted"],
};

const formatDate = (value) => value
  ? new Intl.DateTimeFormat("ko-KR", { dateStyle: "medium" }).format(new Date(value))
  : "-";

export default function ExhibitorAdmin() {
  const { member } = useAuth();
  const organization = member.organization;
  const [page, setPage] = useState("dashboard");
  const [sidebarOpen, setSidebarOpen] = useState(false);
  const [applications, setApplications] = useState([]);
  const [loading, setLoading] = useState(true);
  const [error, setError] = useState("");
  const [cancellingId, setCancellingId] = useState(null);

  const loadApplications = useCallback(async () => {
    setLoading(true);
    setError("");
    try {
      const data = await getOrganizationApplications(organization.organizationId);
      setApplications(Array.isArray(data) ? data : []);
    } catch (requestError) {
      setError(requestError.message || "부스 신청 현황을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  }, [organization.organizationId]);

  useEffect(() => {
    loadApplications();
  }, [loadApplications]);

  const counts = useMemo(() => ({
    total: applications.length,
    reviewing: applications.filter((item) => ["SUBMITTED", "UNDER_REVIEW"].includes(item.status)).length,
    approved: applications.filter((item) => item.status === "APPROVED").length,
    rejected: applications.filter((item) => item.status === "REJECTED").length,
  }), [applications]);

  const approvedApplications = useMemo(
    () => applications.filter((item) => item.status === "APPROVED"),
    [applications]
  );

  // 부스 신청 응답에는 eventId가 없어(recruitmentId만 있음), 소개 수정 API를 부르려면
  // 모집공고에서 eventId를 알아내고, 공개 부스 목록에서 현재 소개 내용을 찾아와야 한다.
  const [boothInfoByApplicationId, setBoothInfoByApplicationId] = useState({});
  const [loadingBoothInfo, setLoadingBoothInfo] = useState(false);
  const [editingApplicationId, setEditingApplicationId] = useState(null);
  const [editForm, setEditForm] = useState(EMPTY_INTRO_FORM);
  const [editImageFile, setEditImageFile] = useState(null);
  const [savingIntro, setSavingIntro] = useState(false);
  const [introError, setIntroError] = useState("");

  useEffect(() => {
    if (approvedApplications.length === 0) {
      setBoothInfoByApplicationId({});
      return;
    }
    let cancelled = false;
    setLoadingBoothInfo(true);

    (async () => {
      const eventIdByRecruitmentId = new Map();
      const boothsByEventId = new Map();
      const result = {};

      for (const application of approvedApplications) {
        try {
          let eventId = eventIdByRecruitmentId.get(application.recruitmentId);
          if (!eventId) {
            const recruitment = await getPublicRecruitment(application.recruitmentId);
            eventId = recruitment.eventId;
            eventIdByRecruitmentId.set(application.recruitmentId, eventId);
          }

          let booths = boothsByEventId.get(eventId);
          if (!booths) {
            booths = await listAllPublicBooths(eventId);
            boothsByEventId.set(eventId, booths);
          }

          const booth = booths.find((b) => b.id === application.boothId) ?? null;
          result[application.id] = { eventId, booth };
        } catch {
          result[application.id] = { eventId: null, booth: null };
        }
      }

      if (!cancelled) {
        setBoothInfoByApplicationId(result);
        setLoadingBoothInfo(false);
      }
    })();

    return () => {
      cancelled = true;
    };
  }, [approvedApplications]);

  const openIntroEdit = (application) => {
    const booth = boothInfoByApplicationId[application.id]?.booth;
    setEditingApplicationId(application.id);
    setSlotManagerApplicationId(null);
    setReservationManagerApplicationId(null);
    setIntroError("");
    setEditImageFile(null);
    setEditForm({
      displayName: booth?.displayName ?? "",
      shortIntro: booth?.shortIntro ?? "",
      description: booth?.description ?? "",
      exhibitionContent: booth?.exhibitionContent ?? "",
    });
  };

  const handleSaveIntro = async (application) => {
    const info = boothInfoByApplicationId[application.id];
    if (!info?.eventId) {
      setIntroError("행사 정보를 확인할 수 없습니다.");
      return;
    }
    setSavingIntro(true);
    setIntroError("");
    try {
      let representativeFileId = info.booth?.representativeFileId ?? null;
      if (editImageFile) {
        const uploaded = await uploadFile(editImageFile, "PUBLIC");
        representativeFileId = uploaded.fileId;
      }
      const updated = await updateBoothIntro(info.eventId, application.boothId, {
        ...editForm,
        representativeFileId,
      });
      setBoothInfoByApplicationId((prev) => ({
        ...prev,
        [application.id]: { eventId: info.eventId, booth: { ...prev[application.id]?.booth, ...updated } },
      }));
      setEditingApplicationId(null);
      setEditImageFile(null);
    } catch (requestError) {
      setIntroError(requestError.message || "부스 소개를 저장하지 못했습니다.");
    } finally {
      setSavingIntro(false);
    }
  };

  // 예약 시간대 관리 (설정): 부스별로 시간대를 만들고, 필요하면 마감한다.
  const [slotsByApplicationId, setSlotsByApplicationId] = useState({});
  const [slotManagerApplicationId, setSlotManagerApplicationId] = useState(null);
  const [loadingSlots, setLoadingSlots] = useState(false);
  const [slotForm, setSlotForm] = useState(EMPTY_SLOT_FORM);
  const [savingSlot, setSavingSlot] = useState(false);
  const [closingSlotId, setClosingSlotId] = useState(null);
  const [deletingSlotId, setDeletingSlotId] = useState(null);
  const [slotError, setSlotError] = useState("");
  const [editingSlotId, setEditingSlotId] = useState(null);
  const [editSlotForm, setEditSlotForm] = useState(EMPTY_SLOT_FORM);
  const [savingSlotEdit, setSavingSlotEdit] = useState(false);

  const loadSlots = async (application) => {
    setLoadingSlots(true);
    try {
      const data = await listReservationSlots(application.boothId);
      setSlotsByApplicationId((prev) => ({ ...prev, [application.id]: data }));
    } catch {
      setSlotsByApplicationId((prev) => ({ ...prev, [application.id]: [] }));
    } finally {
      setLoadingSlots(false);
    }
  };

  const openSlotManager = (application) => {
    setEditingApplicationId(null);
    setReservationManagerApplicationId(null);
    setSlotManagerApplicationId(application.id);
    setSlotForm(EMPTY_SLOT_FORM);
    setSlotError("");
    setConfirmingDeleteSlotId(null);
    setEditingSlotId(null);
    loadSlots(application);
  };

  const handleCreateSlot = async (application) => {
    if (savingSlot) return;
    if (!slotForm.startTime || !slotForm.endTime) {
      setSlotError("시작·종료 시간을 입력해 주세요.");
      return;
    }
    if (slotForm.endTime <= slotForm.startTime) {
      setSlotError("종료 시간은 시작 시간보다 늦어야 합니다.");
      return;
    }
    const capacity = Number(slotForm.capacity);
    if (!Number.isInteger(capacity) || capacity <= 0) {
      setSlotError("정원은 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    const today = todayValue();
    const { startAt: startAtIso, endAt: endAtIso } = buildReservationSlotRange(today, slotForm.startTime, slotForm.endTime);
    setSavingSlot(true);
    setSlotError("");
    try {
      await createReservationSlot(application.boothId, { startAt: startAtIso, endAt: endAtIso, capacity });
      setSlotForm(EMPTY_SLOT_FORM);
      await loadSlots(application);
    } catch (requestError) {
      setSlotError(requestError.message || "시간대를 추가하지 못했습니다.");
    } finally {
      setSavingSlot(false);
    }
  };

  const startEditingSlot = (slot) => {
    setConfirmingDeleteSlotId(null);
    setEditingSlotId(slot.id);
    setEditSlotForm({
      startTime: isoToTimeValue(slot.startAt),
      endTime: isoToTimeValue(slot.endAt),
      capacity: String(slot.capacity),
    });
    setSlotError("");
  };

  const cancelEditingSlot = () => {
    setEditingSlotId(null);
    setSlotError("");
  };

  const handleUpdateSlot = async (application, slot) => {
    if (savingSlotEdit) return;
    if (!editSlotForm.startTime || !editSlotForm.endTime) {
      setSlotError("시작·종료 시간을 입력해 주세요.");
      return;
    }
    if (editSlotForm.endTime <= editSlotForm.startTime) {
      setSlotError("종료 시간은 시작 시간보다 늦어야 합니다.");
      return;
    }
    const capacity = Number(editSlotForm.capacity);
    if (!Number.isInteger(capacity) || capacity <= 0) {
      setSlotError("정원은 1 이상의 숫자로 입력해 주세요.");
      return;
    }
    const today = todayValue();
    const { startAt: startAtIso, endAt: endAtIso } = buildReservationSlotRange(today, editSlotForm.startTime, editSlotForm.endTime);
    setSavingSlotEdit(true);
    setSlotError("");
    try {
      await updateReservationSlot(application.boothId, slot.id, { startAt: startAtIso, endAt: endAtIso, capacity });
      setEditingSlotId(null);
      await loadSlots(application);
    } catch (requestError) {
      setSlotError(requestError.message || "시간대를 수정하지 못했습니다.");
    } finally {
      setSavingSlotEdit(false);
    }
  };

  // 시간대 운영 on/off 토글. 끄면 예약을 못 받고, 다시 켜면 예약을 받을 수 있다.
  const handleToggleSlot = async (application, slot) => {
    if (closingSlotId) return;
    setClosingSlotId(slot.id);
    setSlotError("");
    try {
      if (slot.status === "OPEN") {
        await closeReservationSlot(application.boothId, slot.id);
      } else {
        await reopenReservationSlot(application.boothId, slot.id);
      }
      await loadSlots(application);
    } catch (requestError) {
      setSlotError(requestError.message || "운영 상태를 변경하지 못했습니다.");
    } finally {
      setClosingSlotId(null);
    }
  };

  // window.confirm 대신 버튼을 두 번 눌러야 삭제되는 방식(1차: 확인 상태로 전환, 2차: 실제 삭제).
  const [confirmingDeleteSlotId, setConfirmingDeleteSlotId] = useState(null);

  const handleDeleteSlot = async (application, slot) => {
    if (deletingSlotId) return;
    if (confirmingDeleteSlotId !== slot.id) {
      setConfirmingDeleteSlotId(slot.id);
      return;
    }
    setConfirmingDeleteSlotId(null);
    setDeletingSlotId(slot.id);
    setSlotError("");
    try {
      await deleteReservationSlot(application.boothId, slot.id);
      await loadSlots(application);
    } catch (requestError) {
      setSlotError(requestError.message || "시간대를 삭제하지 못했습니다.");
    } finally {
      setDeletingSlotId(null);
    }
  };

  // 예약 관리 (조회): 부스에 접수된 예약자 목록 - 누가, 어떤 시간에, 몇 명 예약했는지.
  const [reservationsByApplicationId, setReservationsByApplicationId] = useState({});
  const [reservationManagerApplicationId, setReservationManagerApplicationId] = useState(null);
  const [loadingReservations, setLoadingReservations] = useState(false);
  const [reservationsError, setReservationsError] = useState("");
  const [markingAttendanceId, setMarkingAttendanceId] = useState(null);

  const handleMarkAttendance = async (application, reservation, attended) => {
    if (markingAttendanceId) return;
    setMarkingAttendanceId(reservation.id);
    setReservationsError("");
    try {
      await markReservationAttendance(application.boothId, reservation.id, attended);
      await loadReservations(application);
    } catch (requestError) {
      setReservationsError(requestError.message || "출석 처리를 하지 못했습니다.");
    } finally {
      setMarkingAttendanceId(null);
    }
  };

  const loadReservations = async (application) => {
    setLoadingReservations(true);
    setReservationsError("");
    try {
      const data = await listReservationsForManager(application.boothId);
      setReservationsByApplicationId((prev) => ({ ...prev, [application.id]: data }));
    } catch (requestError) {
      setReservationsError(requestError.message || "예약자 목록을 불러오지 못했습니다.");
    } finally {
      setLoadingReservations(false);
    }
  };

  const openReservationManager = (application) => {
    setEditingApplicationId(null);
    setSlotManagerApplicationId(null);
    setReservationManagerApplicationId(application.id);
    loadReservations(application);
  };

  const movePage = (nextPage) => {
    setPage(nextPage);
    setSidebarOpen(false);
  };

  const handleCancel = async (applicationId) => {
    if (!window.confirm("이 부스 신청을 취소하시겠습니까?")) return;
    setCancellingId(applicationId);
    try {
      await cancelApplication(applicationId);
      await loadApplications();
    } catch (requestError) {
      window.alert(requestError.message || "신청을 취소하지 못했습니다.");
    } finally {
      setCancellingId(null);
    }
  };

  const menuClass = (active) =>
    `flex w-full items-center gap-sm rounded-r-xl border-l-[3px] px-md py-sm text-left transition-colors ${active
      ? "border-primary bg-primary/10 font-body-strong text-primary"
      : "border-transparent text-on-surface-variant hover:bg-surface-container"}`;

  const ApplicationList = ({ items }) => {
    if (loading) return <p className="p-xl text-center text-caption text-ink-muted">불러오는 중입니다.</p>;
    if (error) return <div className="p-xl text-center"><p className="text-caption text-error">{error}</p><button type="button" onClick={loadApplications} className="mt-sm text-caption font-body-strong text-primary">다시 시도</button></div>;
    if (items.length === 0) return <p className="p-xl text-center text-caption text-ink-muted">표시할 신청이 없습니다.</p>;

    return (
      <div className="divide-y divide-divider-soft">
        {items.map((application) => {
          const [statusLabel, statusClass] = statusMeta[application.status] || [application.status, "bg-surface-container text-ink-muted"];
          return (
            <article key={application.id} className="flex flex-col gap-md p-lg sm:flex-row sm:items-center">
              <div className="flex h-11 w-11 flex-shrink-0 items-center justify-center rounded-xl bg-primary/10 text-primary"><Icon name="storefront" /></div>
              <div className="min-w-0 flex-1">
                <p className="font-body-strong">{application.teamName}</p>
                <p className="text-caption text-ink-muted">신청번호 {application.applicationNo} · 부스 #{application.boothId}</p>
                <p className="text-[11px] text-ink-muted">{formatDate(application.submittedAt)}</p>
              </div>
              <div className="flex items-center gap-sm">
                <span className={`rounded-full px-sm py-1 text-[11px] font-bold ${statusClass}`}>{statusLabel}</span>
                {application.status === "SUBMITTED" && (
                  <button type="button" onClick={() => handleCancel(application.id)} disabled={cancellingId === application.id} className="rounded-full border border-hairline px-sm py-1 text-[11px] font-bold text-error disabled:opacity-50">
                    {cancellingId === application.id ? "취소 중" : "신청 취소"}
                  </button>
                )}
              </div>
            </article>
          );
        })}
      </div>
    );
  };

  return (
    <div className="min-h-screen bg-surface-container-lowest pt-[44px] text-on-surface">
      <TopNav active="exhibitor" />
      <aside className={`fixed bottom-0 left-0 top-[44px] z-50 flex w-[280px] flex-col border-r border-hairline bg-white shadow-[12px_0_40px_rgba(15,23,42,0.04)] transition-transform md:translate-x-0 ${sidebarOpen ? "translate-x-0" : "-translate-x-full"}`}>
        <div className="flex items-start justify-between border-b border-hairline bg-gradient-to-br from-primary/10 via-white to-primary-container/10 px-lg py-lg">
          <div><span className="text-[10px] font-bold tracking-[0.18em] text-primary">EVENTODAY</span><h1 className="mt-1 font-display-md text-[20px]">부스 관리센터</h1><p className="mt-1 text-[11px] text-ink-muted">신청부터 부스 운영까지 관리하세요</p></div>
          <button type="button" onClick={() => setSidebarOpen(false)} aria-label="부스 관리센터 메뉴 닫기" className="md:hidden"><Icon name="close" /></button>
        </div>
        <div className="px-md pt-md"><Link to="/recruitments" className="flex w-full items-center justify-center gap-xs rounded-xl bg-primary px-md py-sm text-caption font-body-strong text-white"><Icon name="search" className="text-[18px]" /> 모집 공고 찾기</Link></div>
        <nav className="flex-1 space-y-1 overflow-y-auto px-md py-md" aria-label="부스 관리센터 메뉴">
          {menuItems.map((item) => <button key={item.key} type="button" onClick={() => movePage(item.key)} className={menuClass(page === item.key)}><Icon name={item.icon} /><span>{item.label}</span></button>)}
        </nav>
        <div className="border-t border-hairline p-lg"><div className="flex items-center gap-sm"><div className="flex h-8 w-8 items-center justify-center rounded-full bg-surface-container-high"><Icon name="business" /></div><div className="min-w-0"><p className="truncate text-caption font-body-strong">{organization.name}</p><p className="text-[10px] text-ink-muted">{organization.organizationRole}</p></div></div></div>
      </aside>
      {sidebarOpen && <div className="fixed inset-0 z-40 bg-black/40 md:hidden" onClick={() => setSidebarOpen(false)} />}

      <main className="min-h-[calc(100vh-44px)] md:ml-[280px]">
        <header className="sticky top-[44px] z-30 flex h-[64px] items-center justify-between border-b border-hairline bg-white/70 px-lg backdrop-blur-xl"><div className="flex items-center gap-sm"><button type="button" onClick={() => setSidebarOpen(true)} className="md:hidden"><Icon name="menu" /></button><h2 className="font-display-md text-[20px]">{menuItems.find((item) => item.key === page)?.label}</h2></div><Link to="/recruitments" className="rounded-full bg-primary px-md py-xs text-caption font-body-strong text-white">+ 부스 신청</Link></header>
        <div className="mx-auto max-w-[1200px] space-y-section p-lg md:p-xl">
          {page === "dashboard" && <section className="space-y-xl"><div><h1 className="font-display-lg text-[28px]">{organization.name}</h1><p className="text-lead text-on-surface-variant">부스 참가 및 운영 현황</p></div><div className="grid grid-cols-1 gap-lg sm:grid-cols-2 lg:grid-cols-4">{[["전체 신청", counts.total, "assignment"], ["검토 중", counts.reviewing, "schedule"], ["승인된 부스", counts.approved, "storefront"], ["반려", counts.rejected, "cancel"]].map(([label, count, icon]) => <div key={label} className="rounded-xl border border-hairline bg-surface-pearl p-lg"><div className="mb-md flex justify-between"><span className="text-caption text-on-surface-variant">{label}</span><Icon name={icon} className="text-primary" /></div><span className="font-display-md text-[26px]">{loading ? "-" : count}</span></div>)}</div><div className="overflow-hidden rounded-xl border border-hairline bg-white"><div className="flex items-center justify-between border-b border-hairline px-lg py-md"><h3 className="font-body-strong">최근 신청 현황</h3><button type="button" onClick={() => movePage("applications")} className="text-caption font-body-strong text-primary">전체 보기</button></div><ApplicationList items={applications.slice(0, 3)} /></div></section>}
          {page === "applications" && <section className="space-y-lg"><div><h1 className="font-display-lg text-[26px]">부스 신청 현황</h1><p className="mt-xs text-caption text-ink-muted">우리 조직이 제출한 신청과 심사 상태입니다.</p></div><div className="overflow-hidden rounded-xl border border-hairline bg-white"><ApplicationList items={applications} /></div></section>}
          {page === "booths" && (
            <section className="space-y-lg">
              <div>
                <h1 className="font-display-lg text-[26px]">운영 부스</h1>
                <p className="mt-xs text-caption text-ink-muted">
                  승인된 부스의 소개와 대표 이미지를 수정할 수 있어요. 여기서 수정한 내용은 행사 진행중 화면(참가 부스)에 바로 반영됩니다.
                </p>
              </div>

              {loading && <p className="p-xl text-center text-caption text-ink-muted">불러오는 중입니다.</p>}
              {!loading && approvedApplications.length === 0 && (
                <p className="p-xl text-center text-caption text-ink-muted">아직 운영 중인 부스가 없습니다.</p>
              )}

              {!loading && approvedApplications.length > 0 && (
                <div className="space-y-md">
                  {approvedApplications.map((application) => {
                    const info = boothInfoByApplicationId[application.id];
                    const booth = info?.booth;
                    const isEditing = editingApplicationId === application.id;
                    const isSlotManaging = slotManagerApplicationId === application.id;
                    const slots = slotsByApplicationId[application.id] ?? [];
                    const isReservationManaging = reservationManagerApplicationId === application.id;
                    const reservations = reservationsByApplicationId[application.id] ?? [];

                    return (
                      <div key={application.id} className="overflow-hidden rounded-xl border border-hairline bg-white">
                        <div className="flex flex-col gap-md p-lg sm:flex-row sm:items-center">
                          <div className="h-16 w-16 flex-shrink-0 overflow-hidden rounded-xl bg-surface-container-low">
                            {booth?.representativeFileId ? (
                              <img src={fileDownloadUrl(booth.representativeFileId)} alt={booth.displayName || booth.boothCode} className="h-full w-full object-cover" />
                            ) : (
                              <div className="flex h-full w-full items-center justify-center text-primary"><Icon name="storefront" /></div>
                            )}
                          </div>
                          <div className="min-w-0 flex-1">
                            <p className="font-body-strong">{booth?.displayName || application.teamName}</p>
                            <p className="text-caption text-ink-muted">{booth ? `부스 ${booth.boothCode}` : loadingBoothInfo ? "부스 정보를 불러오는 중..." : "부스 정보를 찾을 수 없습니다."}</p>
                            {booth?.shortIntro && <p className="mt-1 text-[11px] text-ink-muted truncate">{booth.shortIntro}</p>}
                          </div>
                          {!isEditing && (
                            <button
                              type="button"
                              onClick={() => openIntroEdit(application)}
                              disabled={!info?.eventId}
                              className="flex-shrink-0 rounded-full border border-hairline px-md py-1 text-caption font-body-strong disabled:opacity-40"
                            >
                              소개 수정
                            </button>
                          )}
                          {!isSlotManaging && (
                            <button
                              type="button"
                              onClick={() => openSlotManager(application)}
                              className="flex-shrink-0 rounded-full border border-hairline px-md py-1 text-caption font-body-strong"
                            >
                              예약시간 관리
                            </button>
                          )}
                          {!isReservationManaging && (
                            <button
                              type="button"
                              onClick={() => openReservationManager(application)}
                              className="flex-shrink-0 rounded-full border border-hairline px-md py-1 text-caption font-body-strong"
                            >
                              예약 관리
                            </button>
                          )}
                        </div>

                        {isEditing && (
                          <div className="space-y-sm border-t border-hairline bg-surface-container-lowest p-lg">
                            <input
                              placeholder="관람객용 부스명"
                              value={editForm.displayName}
                              onChange={(e) => setEditForm({ ...editForm, displayName: e.target.value })}
                              className="w-full rounded-lg border border-hairline px-md py-sm"
                            />
                            <input
                              placeholder="한 줄 소개"
                              value={editForm.shortIntro}
                              onChange={(e) => setEditForm({ ...editForm, shortIntro: e.target.value })}
                              className="w-full rounded-lg border border-hairline px-md py-sm"
                            />
                            <textarea
                              placeholder="상세 소개"
                              value={editForm.description}
                              onChange={(e) => setEditForm({ ...editForm, description: e.target.value })}
                              className="w-full rounded-lg border border-hairline px-md py-sm"
                            />
                            <textarea
                              placeholder="전시·판매 내용"
                              value={editForm.exhibitionContent}
                              onChange={(e) => setEditForm({ ...editForm, exhibitionContent: e.target.value })}
                              className="w-full rounded-lg border border-hairline px-md py-sm"
                            />
                            <div>
                              <p className="mb-1 text-[11px] text-ink-muted">대표 이미지</p>
                              <div className="flex items-center gap-md">
                                <div className="h-16 w-16 flex-shrink-0 overflow-hidden rounded-lg border border-hairline bg-white">
                                  {editImageFile ? (
                                    <img src={URL.createObjectURL(editImageFile)} alt="선택한 이미지 미리보기" className="h-full w-full object-cover" />
                                  ) : booth?.representativeFileId ? (
                                    <img src={fileDownloadUrl(booth.representativeFileId)} alt="현재 대표 이미지" className="h-full w-full object-cover" />
                                  ) : (
                                    <div className="flex h-full w-full items-center justify-center text-ink-muted"><Icon name="image" /></div>
                                  )}
                                </div>
                                <input
                                  type="file"
                                  accept="image/*"
                                  onChange={(e) => setEditImageFile(e.target.files?.[0] ?? null)}
                                  className="text-caption"
                                />
                              </div>
                            </div>

                            {introError && <p className="text-caption text-error">{introError}</p>}

                            <div className="flex gap-sm">
                              <button
                                type="button"
                                onClick={() => handleSaveIntro(application)}
                                disabled={savingIntro}
                                className="rounded-full bg-primary px-lg py-sm text-caption font-body-strong text-white disabled:opacity-40"
                              >
                                {savingIntro ? "저장 중..." : "저장"}
                              </button>
                              <button
                                type="button"
                                onClick={() => setEditingApplicationId(null)}
                                disabled={savingIntro}
                                className="rounded-full border border-hairline px-lg py-sm text-caption"
                              >
                                취소
                              </button>
                            </div>
                          </div>
                        )}

                        {isSlotManaging && (
                          <div className="space-y-sm border-t border-hairline bg-surface-container-lowest p-lg">
                            <p className="font-body-strong text-caption">예약 시간대</p>
                            {loadingSlots && <p className="text-[11px] text-ink-muted">불러오는 중...</p>}
                            {!loadingSlots && slots.length === 0 && (
                              <p className="text-[11px] text-ink-muted">아직 등록된 시간대가 없습니다.</p>
                            )}
                            {!loadingSlots && slots.length > 0 && (
                              <div className="space-y-1">
                                {slots.map((slot) => {
                                  const isOn = slot.status === "OPEN";
                                  const isEditing = editingSlotId === slot.id;

                                  if (isEditing) {
                                    return (
                                      <div key={slot.id} className="space-y-sm rounded-lg border border-primary px-md py-sm text-[11px]">
                                        <div className="grid grid-cols-1 gap-sm sm:grid-cols-3">
                                          <label className="text-[11px] text-ink-muted">
                                            시작 시간
                                            <input
                                              type="time"
                                              value={editSlotForm.startTime}
                                              onChange={(e) => setEditSlotForm({ ...editSlotForm, startTime: e.target.value })}
                                              className="mt-1 w-full rounded-lg border border-hairline px-sm py-1.5"
                                            />
                                          </label>
                                          <label className="text-[11px] text-ink-muted">
                                            종료 시간
                                            <input
                                              type="time"
                                              value={editSlotForm.endTime}
                                              onChange={(e) => setEditSlotForm({ ...editSlotForm, endTime: e.target.value })}
                                              className="mt-1 w-full rounded-lg border border-hairline px-sm py-1.5"
                                            />
                                          </label>
                                          <label className="text-[11px] text-ink-muted">
                                            정원
                                            <input
                                              type="number"
                                              min="1"
                                              value={editSlotForm.capacity}
                                              onChange={(e) => setEditSlotForm({ ...editSlotForm, capacity: e.target.value })}
                                              className="mt-1 w-full rounded-lg border border-hairline px-sm py-1.5"
                                            />
                                          </label>
                                        </div>
                                        <div className="flex gap-sm">
                                          <button
                                            type="button"
                                            onClick={() => handleUpdateSlot(application, slot)}
                                            disabled={savingSlotEdit}
                                            className="rounded-full bg-primary px-lg py-1 font-body-strong text-white disabled:opacity-40"
                                          >
                                            {savingSlotEdit ? "저장 중..." : "저장"}
                                          </button>
                                          <button type="button" onClick={cancelEditingSlot} className="text-ink-muted">
                                            취소
                                          </button>
                                        </div>
                                      </div>
                                    );
                                  }

                                  return (
                                    <div key={slot.id} className="flex items-center justify-between gap-sm rounded-lg border border-hairline px-md py-sm text-[11px]">
                                      <span className={isOn ? "" : "text-ink-muted line-through"}>
                                        {formatSlotRange(slot.startAt, slot.endAt)} · {slot.reservedCount}/{slot.capacity}명
                                      </span>
                                      <div className="flex flex-shrink-0 items-center gap-sm">
                                        <button
                                          type="button"
                                          onClick={() => startEditingSlot(slot)}
                                          aria-label="이 시간대 수정"
                                          title="수정"
                                          className="text-ink-muted hover:text-primary"
                                        >
                                          <Icon name="edit" className="text-[16px]" />
                                        </button>
                                        <button
                                          type="button"
                                          onClick={() => handleToggleSlot(application, slot)}
                                          disabled={closingSlotId === slot.id}
                                          aria-pressed={isOn}
                                          aria-label={isOn ? "이 시간대 운영 끄기" : "이 시간대 운영 켜기"}
                                          className={`relative h-5 w-9 rounded-full transition-colors disabled:opacity-40 ${isOn ? "bg-primary" : "bg-surface-container-highest"}`}
                                        >
                                          <span
                                            className="absolute top-0.5 h-4 w-4 rounded-full bg-white shadow transition-all"
                                            style={{ left: isOn ? "18px" : "2px" }}
                                          />
                                        </button>
                                        {confirmingDeleteSlotId === slot.id ? (
                                          <div className="flex items-center gap-1">
                                            <button
                                              type="button"
                                              onClick={() => handleDeleteSlot(application, slot)}
                                              disabled={deletingSlotId === slot.id}
                                              className="rounded-full bg-error px-sm py-0.5 font-body-strong text-white disabled:opacity-40"
                                            >
                                              {deletingSlotId === slot.id ? "삭제 중..." : "정말 삭제"}
                                            </button>
                                            <button
                                              type="button"
                                              onClick={() => setConfirmingDeleteSlotId(null)}
                                              className="text-ink-muted"
                                            >
                                              취소
                                            </button>
                                          </div>
                                        ) : (
                                          <button
                                            type="button"
                                            onClick={() => handleDeleteSlot(application, slot)}
                                            aria-label="이 시간대 삭제"
                                            title="삭제"
                                            className="text-ink-muted hover:text-error"
                                          >
                                            <Icon name="delete" className="text-[16px]" />
                                          </button>
                                        )}
                                      </div>
                                    </div>
                                  );
                                })}
                              </div>
                            )}

                            <div className="grid grid-cols-1 gap-sm pt-sm sm:grid-cols-3">
                              <label className="text-[11px] text-ink-muted">
                                시작 시간
                                <input
                                  type="time"
                                  value={slotForm.startTime}
                                  onChange={(e) => setSlotForm({ ...slotForm, startTime: e.target.value })}
                                  className="mt-1 w-full rounded-lg border border-hairline px-sm py-1.5"
                                />
                              </label>
                              <label className="text-[11px] text-ink-muted">
                                종료 시간
                                <input
                                  type="time"
                                  value={slotForm.endTime}
                                  onChange={(e) => setSlotForm({ ...slotForm, endTime: e.target.value })}
                                  className="mt-1 w-full rounded-lg border border-hairline px-sm py-1.5"
                                />
                              </label>
                              <label className="text-[11px] text-ink-muted">
                                정원
                                <input
                                  type="number"
                                  min="1"
                                  value={slotForm.capacity}
                                  onChange={(e) => setSlotForm({ ...slotForm, capacity: e.target.value })}
                                  className="mt-1 w-full rounded-lg border border-hairline px-sm py-1.5"
                                />
                              </label>
                            </div>

                            {slotError && <p className="text-caption text-error">{slotError}</p>}

                            <div className="flex gap-sm">
                              <button
                                type="button"
                                onClick={() => handleCreateSlot(application)}
                                disabled={savingSlot}
                                className="rounded-full bg-primary px-lg py-sm text-caption font-body-strong text-white disabled:opacity-40"
                              >
                                {savingSlot ? "추가 중..." : "시간대 추가"}
                              </button>
                              <button
                                type="button"
                                onClick={() => {
                                  setSlotManagerApplicationId(null);
                                  setConfirmingDeleteSlotId(null);
                                }}
                                className="rounded-full border border-hairline px-lg py-sm text-caption"
                              >
                                닫기
                              </button>
                            </div>
                          </div>
                        )}

                        {isReservationManaging && (
                          <div className="space-y-sm border-t border-hairline bg-surface-container-lowest p-lg">
                            <div className="flex items-center justify-between">
                              <p className="font-body-strong text-caption">예약자 목록</p>
                              <button
                                type="button"
                                onClick={() => setReservationManagerApplicationId(null)}
                                className="text-[11px] text-ink-muted"
                              >
                                닫기
                              </button>
                            </div>
                            {loadingReservations && <p className="text-[11px] text-ink-muted">불러오는 중...</p>}
                            {reservationsError && <p className="text-caption text-error">{reservationsError}</p>}
                            {!loadingReservations && !reservationsError && reservations.length === 0 && (
                              <p className="text-[11px] text-ink-muted">아직 예약한 방문객이 없습니다.</p>
                            )}
                            {!loadingReservations && reservations.length > 0 && (
                              <div className="space-y-1">
                                {reservations.map((reservation) => {
                                  const [statusLabel, statusClass] = reservationStatusMeta[reservation.status] ?? [reservation.status, "bg-surface-container text-ink-muted"];
                                  const isMarking = markingAttendanceId === reservation.id;
                                  return (
                                    <div key={reservation.id} className="flex flex-wrap items-center justify-between gap-sm rounded-lg border border-hairline px-md py-sm text-[11px]">
                                      <div className="min-w-0">
                                        <p className="font-body-strong">{formatSlotRange(reservation.startAt, reservation.endAt)} · {reservation.partySize}명</p>
                                        <p className="truncate text-ink-muted">{reservation.memberNickname ?? "알 수 없음"} · {reservation.memberEmail ?? "-"}</p>
                                      </div>
                                      <div className="flex flex-shrink-0 items-center gap-sm">
                                        {reservation.status === "RESERVED" && (
                                          <>
                                            <button
                                              type="button"
                                              onClick={() => handleMarkAttendance(application, reservation, true)}
                                              disabled={isMarking}
                                              className="rounded-full bg-status-available/10 px-sm py-0.5 font-body-strong text-status-available disabled:opacity-40"
                                            >
                                              방문 확인
                                            </button>
                                            <button
                                              type="button"
                                              onClick={() => handleMarkAttendance(application, reservation, false)}
                                              disabled={isMarking}
                                              className="rounded-full bg-status-visited/10 px-sm py-0.5 font-body-strong text-status-visited disabled:opacity-40"
                                            >
                                              노쇼 처리
                                            </button>
                                          </>
                                        )}
                                        <span className={`rounded-full px-sm py-0.5 font-body-strong ${statusClass}`}>{statusLabel}</span>
                                      </div>
                                    </div>
                                  );
                                })}
                              </div>
                            )}
                          </div>
                        )}
                      </div>
                    );
                  })}
                </div>
              )}
            </section>
          )}
          {page === "settings" && <section className="space-y-lg"><div><h1 className="font-display-lg text-[26px]">조직·계정 정보</h1></div><div className="divide-y divide-divider-soft rounded-xl border border-hairline bg-white"><div className="p-lg"><p className="text-[11px] text-ink-muted">회사명</p><p>{organization.name}</p></div><div className="p-lg"><p className="text-[11px] text-ink-muted">조직 유형</p><p>부스측</p></div><div className="p-lg"><p className="text-[11px] text-ink-muted">조직 권한</p><p>{organization.organizationRole}</p></div><div className="p-lg"><p className="text-[11px] text-ink-muted">닉네임</p><p>{member.nickname}</p></div><div className="p-lg"><p className="text-[11px] text-ink-muted">이메일</p><p>{member.email}</p></div></div></section>}
        </div>
      </main>
    </div>
  );
}
