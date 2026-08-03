import { useState } from "react";
import { useSearchParams } from "react-router-dom";
import Icon from "./Icon.jsx";
import { ApiError } from "../api/apiClient.js";
import { toIsoOffset, toDatetimeLocal } from "../utils/datetime.js";
import {
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

export default function RecruitmentManagementPanel() {
  const [searchParams, setSearchParams] = useSearchParams();
  const [eventId, setEventId] = useState(searchParams.get("eventId") ?? "");
  const [loading, setLoading] = useState(false);
  const [loaded, setLoaded] = useState(false);
  const [recruitment, setRecruitment] = useState(null);
  const [form, setForm] = useState(EMPTY_FORM);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");
  const [submitting, setSubmitting] = useState(false);

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

  const loadRecruitment = async (id) => {
    setLoading(true);
    setError("");
    setMessage("");

    try {
      const data = await getManagementRecruitment(id);
      setRecruitment(data);
      fillFormFromResult(data);
    } catch (err) {
      setRecruitment(null);
      setForm(EMPTY_FORM);
      if (!(err instanceof ApiError && err.status === 404)) {
        setError(err instanceof ApiError ? `${err.code}: ${err.message}` : "조회에 실패했습니다.");
      }
    } finally {
      setLoading(false);
      setLoaded(true);
    }
  };

  const handleLoadClick = () => {
    if (!eventId || loading) return;
    setSearchParams({ eventId });
    loadRecruitment(eventId);
  };

  const buildPayload = () => ({
    ...form,
    recruitmentStartAt: toIsoOffset(form.recruitmentStartAt),
    recruitmentEndAt: toIsoOffset(form.recruitmentEndAt),
  });

  const runAction = async (actionFn, successMessage) => {
    if (submitting) return;

    setSubmitting(true);
    setError("");
    setMessage("");

    try {
      const data = await actionFn();
      setRecruitment(data);
      setMessage(successMessage);
    } catch (err) {
      setError(err instanceof ApiError ? `${err.code}: ${err.message}` : "요청에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleCreate = () => runAction(() => createRecruitment(eventId, buildPayload()), "모집 공고를 등록했습니다.");
  const handleUpdate = () => runAction(() => updateRecruitment(eventId, buildPayload()), "수정했습니다.");
  const handleClose = () => runAction(() => closeRecruitment(eventId), "조기 마감 처리했습니다.");
  const handleComplete = () => runAction(() => completeRecruitment(eventId), "완료 처리했습니다.");

  const handleDelete = async () => {
    if (submitting) return;
    if (!window.confirm("이 모집 공고를 삭제할까요? 되돌릴 수 없습니다.")) return;

    setSubmitting(true);
    setError("");
    setMessage("");

    try {
      await deleteRecruitment(eventId);
      setRecruitment(null);
      setForm(EMPTY_FORM);
      setMessage("삭제했습니다.");
    } catch (err) {
      setError(err instanceof ApiError ? `${err.code}: ${err.message}` : "삭제에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <section className="space-y-lg">
      <div className="flex justify-between items-center flex-wrap gap-sm">
        <h1 className="font-display-lg text-[26px]">부스 모집 공고</h1>
      </div>

      <div className="bg-white border border-hairline rounded-xl p-lg space-y-md">
        <div className="flex items-center gap-sm">
          <label className="text-caption text-ink-muted whitespace-nowrap">관리할 행사 ID</label>
          <input
            type="number"
            value={eventId}
            onChange={(e) => setEventId(e.target.value)}
            placeholder="eventId"
            className="border border-hairline rounded-lg px-md py-1.5 text-caption w-32"
          />
          <button
            onClick={handleLoadClick}
            disabled={!eventId || loading}
            className="px-lg py-1.5 bg-primary text-white rounded-full text-caption font-body-strong disabled:opacity-40"
          >
            불러오기
          </button>
        </div>
        <p className="text-[11px] text-ink-muted">
          행사 등록 화면이 아직 없어서, 담당 중인 행사의 ID를 직접 입력해 관리합니다.
        </p>
      </div>

      {loading && <p className="text-caption text-ink-muted">불러오는 중...</p>}
      {error && <p className="text-caption text-error">{error}</p>}
      {message && <p className="text-caption text-status-available">{message}</p>}

      {loaded && !loading && (
        <div className="bg-white border border-hairline rounded-xl p-lg space-y-lg">
          {recruitment ? (
            <div className="flex justify-between items-start mb-sm flex-wrap gap-sm">
              <div>
                <p className="font-body-strong">{recruitment.title}</p>
                <p className="text-caption text-ink-muted">
                  모집 기간 {new Date(recruitment.recruitmentStartAt).toLocaleDateString()} – {new Date(recruitment.recruitmentEndAt).toLocaleDateString()}
                </p>
              </div>
              <div className="flex items-center gap-sm">
                <span className="px-sm py-1 bg-primary-container/10 text-primary-focus text-[12px] rounded-full font-body-strong">
                  {STATUS_LABEL[recruitment.status] ?? recruitment.status}
                </span>
                <button
                  onClick={handleClose}
                  disabled={submitting || !["BEFORE_OPEN", "OPEN"].includes(recruitment.status)}
                  className="text-caption border border-hairline rounded-full px-md py-1 disabled:opacity-40"
                >
                  조기 마감
                </button>
                <button
                  onClick={handleComplete}
                  disabled={submitting || recruitment.status !== "CLOSED"}
                  className="text-caption border border-hairline rounded-full px-md py-1 disabled:opacity-40"
                >
                  완료 처리
                </button>
                <button
                  onClick={handleDelete}
                  disabled={submitting || recruitment.status !== "BEFORE_OPEN"}
                  className="text-caption border border-error text-error rounded-full px-md py-1 disabled:opacity-40 disabled:border-hairline disabled:text-ink-muted"
                >
                  삭제
                </button>
              </div>
            </div>
          ) : (
            <div className="flex items-center gap-sm text-caption text-ink-muted">
              <Icon name="info" className="text-[18px]" />
              이 행사에는 아직 모집 공고가 없습니다. 아래 폼을 채우고 등록하세요.
            </div>
          )}

          <div className="grid grid-cols-1 md:grid-cols-2 gap-sm">
            <input
              placeholder="제목"
              value={form.title}
              onChange={(e) => setForm({ ...form, title: e.target.value })}
              className="border border-hairline rounded-lg px-md py-sm md:col-span-2"
            />
            <div>
              <label className="text-caption text-ink-muted block mb-1">모집 시작</label>
              <input
                type="datetime-local"
                value={form.recruitmentStartAt}
                onChange={(e) => setForm({ ...form, recruitmentStartAt: e.target.value })}
                className="border border-hairline rounded-lg px-md py-sm w-full"
              />
            </div>
            <div>
              <label className="text-caption text-ink-muted block mb-1">모집 종료</label>
              <input
                type="datetime-local"
                value={form.recruitmentEndAt}
                onChange={(e) => setForm({ ...form, recruitmentEndAt: e.target.value })}
                className="border border-hairline rounded-lg px-md py-sm w-full"
              />
            </div>
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
            {recruitment ? (
              <button
                onClick={handleUpdate}
                disabled={submitting}
                className="px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong disabled:opacity-40"
              >
                수정 저장
              </button>
            ) : (
              <button
                onClick={handleCreate}
                disabled={submitting}
                className="px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong disabled:opacity-40"
              >
                + 새 공고 등록
              </button>
            )}
          </div>
        </div>
      )}
    </section>
  );
}
