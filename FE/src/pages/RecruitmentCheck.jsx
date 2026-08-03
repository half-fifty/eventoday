import { useCallback, useEffect, useState } from "react";
import TopNav from "../components/TopNav.jsx";
import Icon from "../components/Icon.jsx";
import useAuth from "../hooks/useAuth.js";
import { ApiError } from "../api/apiClient.js";
import {
  listPublicRecruitments,
  getPublicRecruitment,
  getManagementRecruitment,
  createRecruitment,
  updateRecruitment,
  deleteRecruitment,
  closeRecruitment,
  completeRecruitment,
} from "../api/recruitmentApi.js";

const STATUS_LABEL = {
  BEFORE_OPEN: "모집 준비",
  OPEN: "모집 중",
  CLOSED: "모집 마감",
  COMPLETED: "모집 완료",
};

const EMPTY_FORM = {
  title: "",
  recruitmentStartAt: "",
  recruitmentEndAt: "",
  participantTarget: "",
  qualification: "",
  selectionMethod: "",
  contactName: "",
  contactEmail: "",
  contactPhone: "",
  notice: "",
};

const toIsoOffset = (datetimeLocalValue) => {
  if (!datetimeLocalValue) return "";
  return new Date(datetimeLocalValue).toISOString();
};

const toDatetimeLocal = (isoValue) => {
  if (!isoValue) return "";
  return new Date(isoValue).toISOString().slice(0, 16);
};

export default function RecruitmentCheck() {
  const { isAuthenticated, member } = useAuth();

  const [publicList, setPublicList] = useState([]);
  const [publicListError, setPublicListError] = useState("");
  const [statusFilter, setStatusFilter] = useState("");

  const [detailId, setDetailId] = useState("");
  const [detailResult, setDetailResult] = useState(null);
  const [detailError, setDetailError] = useState("");

  const [eventId, setEventId] = useState("");
  const [managementResult, setManagementResult] = useState(null);
  const [managementError, setManagementError] = useState("");
  const [form, setForm] = useState(EMPTY_FORM);
  const [actionMessage, setActionMessage] = useState("");

  const loadPublicList = useCallback(async () => {
    setPublicListError("");
    try {
      const data = await listPublicRecruitments(statusFilter || undefined);
      setPublicList(data);
    } catch (error) {
      setPublicListError(error instanceof ApiError ? error.message : "목록 조회에 실패했습니다.");
    }
  }, [statusFilter]);

  useEffect(() => {
    loadPublicList();
  }, [loadPublicList]);

  const handleDetailLookup = async () => {
    setDetailError("");
    setDetailResult(null);
    if (!detailId) return;

    try {
      const data = await getPublicRecruitment(detailId);
      setDetailResult(data);
    } catch (error) {
      setDetailError(error instanceof ApiError ? `${error.code}: ${error.message}` : "상세 조회에 실패했습니다.");
    }
  };

  const fillFormFromResult = (data) => {
    setForm({
      title: data.title ?? "",
      recruitmentStartAt: toDatetimeLocal(data.recruitmentStartAt),
      recruitmentEndAt: toDatetimeLocal(data.recruitmentEndAt),
      participantTarget: data.participantTarget ?? "",
      qualification: data.qualification ?? "",
      selectionMethod: data.selectionMethod ?? "",
      contactName: data.contactName ?? "",
      contactEmail: data.contactEmail ?? "",
      contactPhone: data.contactPhone ?? "",
      notice: data.notice ?? "",
    });
  };

  const handleManagementLookup = async () => {
    setManagementError("");
    setActionMessage("");
    if (!eventId) return;

    try {
      const data = await getManagementRecruitment(eventId);
      setManagementResult(data);
      fillFormFromResult(data);
    } catch (error) {
      setManagementResult(null);
      if (error instanceof ApiError && error.status === 404) {
        setManagementError("이 행사에는 아직 모집 공고가 없습니다. 아래 폼으로 생성해 보세요.");
      } else {
        setManagementError(error instanceof ApiError ? `${error.code}: ${error.message}` : "조회에 실패했습니다.");
      }
    }
  };

  const buildPayload = () => ({
    ...form,
    recruitmentStartAt: toIsoOffset(form.recruitmentStartAt),
    recruitmentEndAt: toIsoOffset(form.recruitmentEndAt),
  });

  const runAction = async (actionFn, successMessage) => {
    setManagementError("");
    setActionMessage("");

    try {
      const data = await actionFn();
      setManagementResult(data);
      setActionMessage(successMessage);
      loadPublicList();
    } catch (error) {
      setManagementError(error instanceof ApiError ? `${error.code}: ${error.message}` : "요청에 실패했습니다.");
    }
  };

  const handleCreate = () => runAction(() => createRecruitment(eventId, buildPayload()), "생성되었습니다.");
  const handleUpdate = () => runAction(() => updateRecruitment(eventId, buildPayload()), "수정되었습니다.");
  const handleClose = () => runAction(() => closeRecruitment(eventId), "조기 마감 처리되었습니다.");
  const handleComplete = () => runAction(() => completeRecruitment(eventId), "완료 처리되었습니다.");

  const handleDelete = async () => {
    if (!window.confirm("이 모집 공고를 삭제할까요? 되돌릴 수 없습니다.")) return;

    setManagementError("");
    setActionMessage("");

    try {
      await deleteRecruitment(eventId);
      setManagementResult(null);
      setForm(EMPTY_FORM);
      setActionMessage("삭제되었습니다.");
      loadPublicList();
    } catch (error) {
      setManagementError(error instanceof ApiError ? `${error.code}: ${error.message}` : "삭제에 실패했습니다.");
    }
  };

  return (
    <div className="bg-surface text-on-surface min-h-screen">
      <TopNav active="recruiting" />

      <main className="max-w-[900px] mx-auto px-lg pt-[80px] pb-section space-y-section">
        <div>
          <h1 className="font-display-lg text-[26px] mb-xs">모집 공고 API 확인용 페이지</h1>
          <p className="text-caption text-ink-muted">
            recruitment API(REC-API-001~007)를 실제로 호출해서 동작을 확인하는 개발용 화면입니다.
          </p>
        </div>

        {/* 공개 목록 */}
        <section className="bg-white border border-hairline rounded-2xl p-lg space-y-md">
          <div className="flex items-center justify-between">
            <h2 className="font-body-strong text-body">공개 모집 공고 목록</h2>
            <div className="flex items-center gap-sm">
              <select
                value={statusFilter}
                onChange={(e) => setStatusFilter(e.target.value)}
                className="border border-hairline rounded-lg text-caption px-sm py-1"
              >
                <option value="">전체</option>
                <option value="OPEN">모집 중</option>
                <option value="CLOSED">모집 마감</option>
                <option value="COMPLETED">모집 완료</option>
              </select>
              <button onClick={loadPublicList} className="text-caption text-primary font-body-strong flex items-center gap-1">
                <Icon name="refresh" className="text-[16px]" /> 새로고침
              </button>
            </div>
          </div>

          {publicListError && <p className="text-caption text-error">{publicListError}</p>}

          {publicList.length === 0 ? (
            <p className="text-caption text-ink-muted">
              공개된 모집 공고가 없습니다. (BEFORE_OPEN 상태는 목록에서 숨겨집니다)
            </p>
          ) : (
            <div className="divide-y divide-divider-soft">
              {publicList.map((r) => (
                <div key={r.id} className="py-sm flex items-center justify-between">
                  <div>
                    <p className="font-body-strong text-[14px]">{r.title}</p>
                    <p className="text-caption text-ink-muted">eventId {r.eventId} · id {r.id}</p>
                  </div>
                  <div className="flex items-center gap-sm">
                    <span className="text-[11px] font-bold px-sm py-1 rounded-full bg-surface-container">
                      {STATUS_LABEL[r.status] ?? r.status}
                    </span>
                    <button
                      onClick={() => setDetailId(String(r.id))}
                      className="text-caption text-primary underline"
                    >
                      상세보기
                    </button>
                  </div>
                </div>
              ))}
            </div>
          )}
        </section>

        {/* 공개 상세 */}
        <section className="bg-white border border-hairline rounded-2xl p-lg space-y-md">
          <h2 className="font-body-strong text-body">모집 공고 상세 조회</h2>
          <div className="flex gap-sm">
            <input
              type="number"
              placeholder="recruitmentId"
              value={detailId}
              onChange={(e) => setDetailId(e.target.value)}
              className="flex-1 border border-hairline rounded-lg px-md py-sm"
            />
            <button onClick={handleDetailLookup} className="bg-primary text-white px-lg py-sm rounded-lg font-body-strong">
              조회
            </button>
          </div>
          {detailError && <p className="text-caption text-error">{detailError}</p>}
          {detailResult && (
            <pre className="bg-surface-container rounded-lg p-md text-[12px] overflow-x-auto">
              {JSON.stringify(detailResult, null, 2)}
            </pre>
          )}
        </section>

        {/* 관리 (인증 필요) */}
        <section className="bg-white border border-hairline rounded-2xl p-lg space-y-md">
          <h2 className="font-body-strong text-body">행사별 모집 공고 관리 (EVENT_MANAGER 권한 필요)</h2>

          {!isAuthenticated ? (
            <p className="text-caption text-ink-muted">로그인이 필요합니다. 상단 로그인 버튼을 눌러주세요.</p>
          ) : (
            <p className="text-caption text-ink-muted">{member.nickname}님으로 로그인됨 (memberId {member.memberId})</p>
          )}

          <div className="flex gap-sm">
            <input
              type="number"
              placeholder="eventId"
              value={eventId}
              onChange={(e) => setEventId(e.target.value)}
              className="flex-1 border border-hairline rounded-lg px-md py-sm"
            />
            <button
              onClick={handleManagementLookup}
              disabled={!isAuthenticated}
              className="bg-primary text-white px-lg py-sm rounded-lg font-body-strong disabled:opacity-40"
            >
              관리용 조회
            </button>
          </div>

          {managementError && <p className="text-caption text-error">{managementError}</p>}
          {actionMessage && <p className="text-caption text-status-available">{actionMessage}</p>}

          {managementResult && (
            <div className="flex items-center gap-sm text-caption">
              <span>현재 상태:</span>
              <span className="font-bold px-sm py-1 rounded-full bg-surface-container">
                {STATUS_LABEL[managementResult.status] ?? managementResult.status}
              </span>
              <button
                onClick={handleClose}
                disabled={!["BEFORE_OPEN", "OPEN"].includes(managementResult.status)}
                className="ml-auto border border-hairline rounded-full px-md py-1 disabled:opacity-40"
              >
                조기 마감
              </button>
              <button
                onClick={handleComplete}
                disabled={managementResult.status !== "CLOSED"}
                className="border border-hairline rounded-full px-md py-1 disabled:opacity-40"
              >
                완료 처리
              </button>
              <button
                onClick={handleDelete}
                disabled={managementResult.status !== "BEFORE_OPEN"}
                className="border border-error text-error rounded-full px-md py-1 disabled:opacity-40 disabled:border-hairline disabled:text-ink-muted"
              >
                삭제
              </button>
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-sm">
            <input
              placeholder="제목"
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              className="border border-hairline rounded-lg px-md py-sm md:col-span-2"
            />
            <label className="text-caption text-ink-muted md:col-span-2 -mb-2">모집 시작</label>
            <input
              type="datetime-local"
              value={form.recruitmentStartAt}
              onChange={(e) => setForm({ ...form, recruitmentStartAt: e.target.value })}
              className="border border-hairline rounded-lg px-md py-sm"
            />
            <div className="hidden md:block" />
            <label className="text-caption text-ink-muted md:col-span-2 -mb-2">모집 종료</label>
            <input
              type="datetime-local"
              value={form.recruitmentEndAt}
              onChange={(e) => setForm({ ...form, recruitmentEndAt: e.target.value })}
              className="border border-hairline rounded-lg px-md py-sm"
            />
            <div className="hidden md:block" />
            <input
              placeholder="참가 대상"
              value={form.participantTarget}
              onChange={(e) => setForm({ ...form, participantTarget: e.target.value })}
              className="border border-hairline rounded-lg px-md py-sm md:col-span-2"
            />
            <input
              placeholder="자격 요건 (선택)"
              value={form.qualification}
              onChange={(e) => setForm({ ...form, qualification: e.target.value })}
              className="border border-hairline rounded-lg px-md py-sm"
            />
            <input
              placeholder="선정 방식 (선택)"
              value={form.selectionMethod}
              onChange={(e) => setForm({ ...form, selectionMethod: e.target.value })}
              className="border border-hairline rounded-lg px-md py-sm"
            />
            <input
              placeholder="담당자명"
              value={form.contactName}
              onChange={(e) => setForm({ ...form, contactName: e.target.value })}
              className="border border-hairline rounded-lg px-md py-sm"
            />
            <input
              placeholder="담당자 이메일"
              value={form.contactEmail}
              onChange={(e) => setForm({ ...form, contactEmail: e.target.value })}
              className="border border-hairline rounded-lg px-md py-sm"
            />
            <input
              placeholder="담당자 연락처"
              value={form.contactPhone}
              onChange={(e) => setForm({ ...form, contactPhone: e.target.value })}
              className="border border-hairline rounded-lg px-md py-sm md:col-span-2"
            />
            <textarea
              placeholder="안내사항 (선택)"
              value={form.notice}
              onChange={(e) => setForm({ ...form, notice: e.target.value })}
              className="border border-hairline rounded-lg px-md py-sm md:col-span-2"
            />
          </div>

          <div className="flex gap-sm">
            <button
              onClick={handleCreate}
              disabled={!isAuthenticated || !eventId}
              className="bg-primary text-white px-lg py-sm rounded-lg font-body-strong disabled:opacity-40"
            >
              생성
            </button>
            <button
              onClick={handleUpdate}
              disabled={!isAuthenticated || !eventId}
              className="border border-primary text-primary px-lg py-sm rounded-lg font-body-strong disabled:opacity-40"
            >
              수정
            </button>
          </div>
        </section>
      </main>
    </div>
  );
}
