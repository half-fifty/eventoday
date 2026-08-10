import { useEffect, useMemo, useState } from "react";
import { Link, useSearchParams } from "react-router-dom";
import Icon from "../components/Icon.jsx";
import NotificationBell from "../components/NotificationBell.jsx";
import FileUploadField from "../components/FileUploadField.jsx";
import { ApiError } from "../api/apiClient.js";
import { getPublicRecruitment } from "../api/recruitmentApi.js";
import { listPublicBooths } from "../api/boothApi.js";
import { submitApplication } from "../api/boothApplicationApi.js";
import { uploadFile } from "../api/fileApi.js";
import useAuth from "../hooks/useAuth.js";

// 신청 폼 초기값 - BE BoothApplicationSubmitRequestDto 필드와 1:1 대응
const emptyForm = {
  teamName: "",
  contactName: "",
  contactEmail: "",
  contactPhone: "",
  activityDescription: "",
  exhibitionContent: "",
  expectedVisitors: "",
  electricityRequired: false,
  waterRequired: false,
  drainageRequired: false,
  internetRequired: false,
  applicationReason: "",
};

export default function BoothApply() {
  const [params] = useSearchParams();
  const recruitmentId = params.get("recruitmentId");
  const preselectBoothId = params.get("boothId");
  const { member } = useAuth();

  // 참가기업(EXHIBITOR) 조직 - 신청 자격 확인용
  const exhibitorOrg =
    member?.organization?.organizationType === "EXHIBITOR" ? member.organization : null;

  const [recruitment, setRecruitment] = useState(null);
  const [booths, setBooths] = useState([]);
  const [loading, setLoading] = useState(true);
  const [loadError, setLoadError] = useState("");

  const [filters, setFilters] = useState({ elec: false, water: false });
  const [selectedBoothId, setSelectedBoothId] = useState(null);
  const [form, setForm] = useState(emptyForm);
  const [estimateFileId, setEstimateFileId] = useState(null);
  const [otherFiles, setOtherFiles] = useState([]); // { fileId, name }
  const [otherUploading, setOtherUploading] = useState(false);

  const [submitting, setSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState("");
  const [result, setResult] = useState(null); // 제출 성공 시 신청 응답

  // 모집 공고 + 부스 목록 로드
  useEffect(() => {
    if (!recruitmentId) {
      setLoadError("모집 공고 정보가 없습니다. 모집 공고에서 다시 접근해 주세요.");
      setLoading(false);
      return;
    }
    let cancelled = false;
    setLoading(true);
    setLoadError("");
    getPublicRecruitment(recruitmentId)
      .then((rec) => {
        if (cancelled) return null;
        setRecruitment(rec);
        // 부스 목록은 페이지네이션 응답이라 content로 접근
        return listPublicBooths(rec.eventId, { page: 0, size: 100 });
      })
      .then((data) => {
        if (!cancelled && data) setBooths(data.content || []);
      })
      .catch((err) => {
        if (!cancelled) {
          setLoadError(err instanceof ApiError ? err.message : "정보를 불러오지 못했습니다.");
        }
      })
      .finally(() => {
        if (!cancelled) setLoading(false);
      });
    return () => { cancelled = true; };
  }, [recruitmentId]);

  // 로그인 회원 정보로 담당자 이메일 미리 채우기
  useEffect(() => {
    if (member) {
      setForm((prev) => ({
        ...prev,
        contactEmail: prev.contactEmail || member.email || "",
        contactName: prev.contactName || member.nickname || "",
        teamName: prev.teamName || member.organization?.name || "",
      }));
    }
  }, [member]);

  // 신청 가능 여부: AVAILABLE 상태 + 설비 필터 충족
  const isEligible = (booth) =>
    booth.status === "AVAILABLE" &&
    (!filters.elec || booth.electricityAvailable) &&
    (!filters.water || booth.waterAvailable);

  // URL로 전달된 부스 사전 선택 (목록 로드 후 1회)
  useEffect(() => {
    if (preselectBoothId && booths.length > 0) {
      const target = booths.find((b) => String(b.id) === preselectBoothId);
      if (target && target.status === "AVAILABLE") setSelectedBoothId(target.id);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [booths]);

  const toggleFilter = (key) => {
    const next = { ...filters, [key]: !filters[key] };
    setFilters(next);
    // 필터 변경으로 조건 불충족이 된 부스는 선택 해제
    if (selectedBoothId) {
      const booth = booths.find((b) => b.id === selectedBoothId);
      const stillEligible =
        booth &&
        booth.status === "AVAILABLE" &&
        (!next.elec || booth.electricityAvailable) &&
        (!next.water || booth.waterAvailable);
      if (!stillEligible) setSelectedBoothId(null);
    }
  };

  const selectedBooth = useMemo(
    () => booths.find((b) => b.id === selectedBoothId) || null,
    [booths, selectedBoothId]
  );

  const setField = (key, value) => setForm((prev) => ({ ...prev, [key]: value }));

  // BE FileService.ALLOWED_EXTENSIONS와 동일한 허용 확장자 목록
  const ALLOWED_FILE_EXTS = ["pdf", "doc", "docx", "jpg", "jpeg", "png", "gif", "webp"];

  // 기타 첨부파일 업로드 (최대 5개)
  const uploadOtherFile = async (event) => {
    const file = event.target.files?.[0];
    if (!file || otherFiles.length >= 5) return;
    // 업로드 전 확장자 검사 (BE 화이트리스트 기준)
    const ext = file.name.split(".").pop().toLowerCase();
    if (!ALLOWED_FILE_EXTS.includes(ext)) {
      setSubmitError("허용되지 않는 파일 형식입니다. PDF, Word(.doc/.docx), 이미지 파일만 첨부할 수 있어요.");
      event.target.value = "";
      return;
    }
    setSubmitError("");
    setOtherUploading(true);
    try {
      // FileUploadField와 동일하게 PUBLIC 접근 레벨 사용
      const uploaded = await uploadFile(file, "PUBLIC");
      setOtherFiles((prev) => [...prev, { fileId: uploaded.fileId, name: uploaded.originalName || file.name }]);
    } catch (err) {
      setSubmitError(err.message || "파일 업로드에 실패했습니다.");
    } finally {
      setOtherUploading(false);
      event.target.value = "";
    }
  };

  const removeOtherFile = (fileId) =>
    setOtherFiles((prev) => prev.filter((f) => f.fileId !== fileId));

  // 필수값 검증: 부스, 견적서, 필수 텍스트 필드
  const requiredFilled =
    form.teamName.trim() &&
    form.contactName.trim() &&
    form.contactEmail.trim() &&
    form.contactPhone.trim() &&
    form.activityDescription.trim() &&
    form.exhibitionContent.trim();
  const canSubmit =
    Boolean(exhibitorOrg) && Boolean(selectedBoothId) && Boolean(estimateFileId) &&
    requiredFilled && !submitting && !result;

  // APP-API-001 신청 제출
  const handleSubmit = async () => {
    if (!canSubmit) return;
    setSubmitting(true);
    setSubmitError("");
    try {
      const payload = {
        boothId: selectedBoothId,
        applicantOrganizationId: exhibitorOrg.organizationId,
        teamName: form.teamName.trim(),
        contactName: form.contactName.trim(),
        contactEmail: form.contactEmail.trim(),
        contactPhone: form.contactPhone.trim(),
        activityDescription: form.activityDescription.trim(),
        exhibitionContent: form.exhibitionContent.trim(),
        expectedVisitors: form.expectedVisitors === "" ? null : Number(form.expectedVisitors),
        electricityRequired: form.electricityRequired,
        waterRequired: form.waterRequired,
        drainageRequired: form.drainageRequired,
        internetRequired: form.internetRequired,
        applicationReason: form.applicationReason.trim() || null,
        estimateFileId,
        otherFileIds: otherFiles.map((f) => f.fileId),
      };
      const created = await submitApplication(recruitmentId, payload);
      setResult(created);
    } catch (err) {
      setSubmitError(err instanceof ApiError ? err.message : "신청 제출에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const filterBtnCls = (on) =>
    `px-lg py-sm rounded-full border font-body text-body flex items-center gap-xs transition-all ${
      on ? "border-2 border-primary-focus bg-surface-pearl font-body-strong" : "border-hairline"
    }`;

  const inputCls =
    "w-full h-[44px] rounded-lg border border-hairline px-sm focus:border-primary-focus focus:ring-2 focus:ring-primary-focus/20 outline-none transition-all";

  return (
    <div className="bg-background text-on-surface">
      {/* Top Nav */}
      <header className="fixed top-0 w-full h-[44px] z-[100] bg-black flex justify-between items-center px-lg">
        <Link to="/" className="font-hero-display text-tagline text-white">EvenToday</Link>
        <div className="flex items-center gap-sm">
          <NotificationBell />
          <Link
            to={recruitmentId ? `/recruitments/${recruitmentId}` : "/recruitments"}
            className="text-white/80 hover:text-white text-nav-link font-nav-link flex items-center gap-1"
          >
            <Icon name="arrow_back" className="text-[18px]" /> 모집 공고로 돌아가기
          </Link>
        </div>
      </header>

      <main className="max-w-[1200px] mx-auto min-h-screen pt-[80px] pb-section px-lg">
        {loading && <p className="py-xxl text-center text-ink-muted">부스 정보를 불러오는 중입니다...</p>}

        {!loading && loadError && (
          <div className="bg-surface-pearl border border-hairline rounded-2xl p-xxl text-center text-ink-muted">
            <Icon name="error_outline" className="text-[32px] block mb-sm mx-auto" />
            <p>{loadError}</p>
            <Link to="/recruitments" className="inline-block mt-md px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong">
              모집 공고 목록으로
            </Link>
          </div>
        )}

        {/* 참가기업 조직이 아닌 경우 안내 */}
        {!loading && !loadError && !exhibitorOrg && (
          <div className="bg-surface-pearl border border-hairline rounded-2xl p-xxl text-center text-ink-muted mb-lg">
            <Icon name="business" className="text-[32px] block mb-sm mx-auto" />
            <p>부스 신청은 참가기업 계정만 가능합니다.</p>
            <Link to="/business/signup" className="inline-block mt-md px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong">
              기업 회원가입
            </Link>
          </div>
        )}

        {!loading && !loadError && recruitment && (
          <div className="grid grid-cols-1 lg:grid-cols-12 gap-xl">
            {/* Left: 조건 필터 + 부스 선택 */}
            <div className="lg:col-span-7 flex flex-col gap-lg">
              <div className="flex flex-col gap-xs">
                <h1 className="font-hero-display text-[32px] md:text-[40px] font-semibold tracking-tight">참가 부스 선택</h1>
                <p className="text-secondary text-body">{recruitment.title}</p>
                <p className="text-secondary text-caption">필요한 설비 조건을 선택하면 조건에 맞지 않는 부스는 선택할 수 없어요.</p>
              </div>

              {/* 설비 조건 필터 */}
              <div className="bg-white rounded-xl border border-hairline p-lg flex flex-wrap gap-sm">
                <button onClick={() => toggleFilter("elec")} className={filterBtnCls(filters.elec)}>
                  <Icon name="bolt" className="text-[18px]" /> 전기 사용 필요
                </button>
                <button onClick={() => toggleFilter("water")} className={filterBtnCls(filters.water)}>
                  <Icon name="water_drop" className="text-[18px]" /> 급수·배수 필요
                </button>
              </div>

              {/* 부스 그리드 */}
              <div className="bg-white rounded-xl border border-hairline p-lg relative">
                {booths.length === 0 ? (
                  <p className="text-caption text-ink-muted text-center py-lg">등록된 부스가 없습니다.</p>
                ) : (
                  <div className="grid grid-cols-4 md:grid-cols-5 gap-sm">
                    {booths.map((booth) => {
                      const eligible = isEligible(booth);
                      const active = selectedBoothId === booth.id;
                      return (
                        <button
                          key={booth.id}
                          disabled={!eligible}
                          onClick={eligible ? () => setSelectedBoothId(booth.id) : undefined}
                          title={`${booth.boothCode} · ${booth.status}`}
                          className={`booth-cell h-16 rounded-lg text-[11px] font-bold flex flex-col items-center justify-center gap-0.5 transition-all ${
                            !eligible
                              ? "bg-surface-container text-ink-muted cursor-not-allowed opacity-60"
                              : active
                              ? "bg-primary-container text-white ring-2 ring-primary-focus"
                              : "bg-white border border-hairline text-on-surface hover:border-primary"
                          }`}
                        >
                          <span>{booth.boothCode}</span>
                          <span className="flex gap-0.5">
                            {booth.electricityAvailable && <Icon name="bolt" className="text-[11px]" />}
                            {booth.waterAvailable && <Icon name="water_drop" className="text-[11px]" />}
                          </span>
                        </button>
                      );
                    })}
                  </div>
                )}
                <p className="text-[12px] text-primary-focus font-medium mt-md flex items-center gap-1">
                  <Icon name="info" className="text-[16px]" /> 회색 부스는 조건 불일치 또는 이미 신청·배정되어 선택할 수 없습니다.
                </p>
              </div>

              {/* 필수 서류: 견적서 */}
              <div className="bg-white rounded-xl border border-hairline p-lg space-y-md">
                <div>
                  <h3 className="font-body-strong text-body mb-sm">필수 제출 서류</h3>
                  <p className="text-[14px] text-secondary">견적서는 필수 제출이며, 기타 서류는 최대 5개까지 첨부할 수 있어요.</p>
                  {/* 지원 파일 형식 안내 — BE FileService 허용 목록 기준 */}
                  <p className="text-[12px] text-ink-muted mt-xs">
                    지원 형식: PDF, Word(.doc/.docx), 이미지(JPG/JPEG/PNG/GIF/WebP) · 최대 10MB
                  </p>
                </div>
                <FileUploadField
                  label="견적서"
                  required
                  accept=".pdf,.doc,.docx,.jpg,.jpeg,.png,.gif,.webp"
                  value={estimateFileId}
                  onChange={setEstimateFileId}
                />
                {/* 기타 첨부파일 (선택, 최대 5개) */}
                <div className="space-y-sm">
                  <label className="font-body-strong">기타 첨부파일 (선택)</label>
                  {otherFiles.map((file) => (
                    <div key={file.fileId} className="flex items-center gap-sm border border-hairline rounded-xl p-md">
                      <Icon name="attach_file" className="text-ink-muted text-[18px]" />
                      <span className="flex-1 text-sm truncate">{file.name}</span>
                      <button onClick={() => removeOtherFile(file.fileId)} aria-label="첨부 삭제">
                        <Icon name="close" className="text-[16px] text-ink-muted" />
                      </button>
                    </div>
                  ))}
                  {otherFiles.length < 5 && (
                    <label className="flex items-center gap-md border border-dashed border-outline-variant rounded-xl p-md cursor-pointer hover:bg-surface-pearl transition-colors">
                      <Icon name="add" className="text-ink-muted" />
                      <span className="flex-1 text-sm text-secondary">
                        {otherUploading ? "업로드 중..." : "파일 추가하기"}
                      </span>
                      <input type="file" accept=".pdf,.doc,.docx,.jpg,.jpeg,.png,.gif,.webp" className="hidden" disabled={otherUploading} onChange={uploadOtherFile} />
                    </label>
                  )}
                </div>
              </div>
            </div>

            {/* Right: 신청서 폼 */}
            <div className="lg:col-span-5">
              <div className="sticky top-[60px] bg-white rounded-2xl border border-hairline shadow-xl overflow-hidden">
                <div className="p-xl bg-on-primary-fixed text-white">
                  <h2 className="text-[28px] font-semibold tracking-tight">부스 신청서</h2>
                  <p className="text-white/60 text-[14px]">기업 정보를 입력하고 설비 옵션을 확인하세요.</p>
                </div>

                {/* 제출 완료 화면 */}
                {result ? (
                  <div className="p-xl flex flex-col items-center gap-md text-center">
                    <Icon name="check_circle" className="text-[48px] text-status-available" />
                    <h3 className="font-body-strong text-[18px]">신청이 완료되었습니다</h3>
                    <p className="text-caption text-ink-muted">
                      신청번호 <span className="font-body-strong text-on-surface">{result.applicationNo}</span>
                      <br />검토 결과는 담당자 이메일로 안내됩니다.
                    </p>
                    <Link to="/my-applications" className="w-full h-[48px] bg-primary text-white rounded-xl font-body-strong flex items-center justify-center">
                      내 신청 현황 보기
                    </Link>
                  </div>
                ) : (
                  <div className="p-xl flex flex-col gap-lg max-h-[70vh] overflow-y-auto">
                    <div className="space-y-sm">
                      <label className="block text-[14px] font-bold">참가 기업명 *</label>
                      <input type="text" value={form.teamName} onChange={(e) => setField("teamName", e.target.value)} placeholder="공식 기업명을 입력하세요" className={inputCls} />
                    </div>
                    <div className="grid grid-cols-2 gap-sm">
                      <div className="space-y-sm">
                        <label className="block text-[14px] font-bold">담당자명 *</label>
                        <input type="text" value={form.contactName} onChange={(e) => setField("contactName", e.target.value)} placeholder="이름" className={inputCls} />
                      </div>
                      <div className="space-y-sm">
                        <label className="block text-[14px] font-bold">연락처 *</label>
                        <input type="text" value={form.contactPhone} onChange={(e) => setField("contactPhone", e.target.value)} placeholder="010-0000-0000" className={inputCls} />
                      </div>
                    </div>
                    <div className="space-y-sm">
                      <label className="block text-[14px] font-bold">담당자 이메일 *</label>
                      <input type="email" value={form.contactEmail} onChange={(e) => setField("contactEmail", e.target.value)} placeholder="contact@company.com" className={inputCls} />
                    </div>
                    <div className="space-y-sm">
                      <label className="block text-[14px] font-bold">기업 활동 소개 *</label>
                      <textarea value={form.activityDescription} onChange={(e) => setField("activityDescription", e.target.value)} rows={3} placeholder="주요 사업·활동을 소개해 주세요" className="w-full rounded-lg border border-hairline p-sm focus:border-primary-focus focus:ring-2 focus:ring-primary-focus/20 outline-none transition-all resize-none" />
                    </div>
                    <div className="space-y-sm">
                      <label className="block text-[14px] font-bold">전시 내용 *</label>
                      <textarea value={form.exhibitionContent} onChange={(e) => setField("exhibitionContent", e.target.value)} rows={3} placeholder="부스에서 전시·판매할 내용을 입력해 주세요" className="w-full rounded-lg border border-hairline p-sm focus:border-primary-focus focus:ring-2 focus:ring-primary-focus/20 outline-none transition-all resize-none" />
                    </div>
                    <div className="space-y-sm">
                      <label className="block text-[14px] font-bold">예상 방문객 수 (선택)</label>
                      <input type="number" min="0" value={form.expectedVisitors} onChange={(e) => setField("expectedVisitors", e.target.value)} placeholder="예: 500" className={inputCls} />
                    </div>

                    {/* 설비 요청 사항 */}
                    <div className="space-y-sm border-t border-hairline pt-lg">
                      <label className="block text-[14px] font-bold">설비 요청 사항</label>
                      <div className="grid grid-cols-2 gap-sm">
                        {[
                          { key: "electricityRequired", label: "전기", icon: "bolt" },
                          { key: "waterRequired", label: "급수", icon: "water_drop" },
                          { key: "drainageRequired", label: "배수", icon: "waves" },
                          { key: "internetRequired", label: "인터넷", icon: "wifi" },
                        ].map((option) => (
                          <label key={option.key} className={`flex items-center gap-sm border rounded-lg px-md py-sm cursor-pointer transition-all ${form[option.key] ? "border-primary bg-primary/5" : "border-hairline"}`}>
                            <input type="checkbox" checked={form[option.key]} onChange={(e) => setField(option.key, e.target.checked)} className="rounded" />
                            <Icon name={option.icon} className="text-[16px] text-ink-muted" />
                            <span className="text-[13px]">{option.label}</span>
                          </label>
                        ))}
                      </div>
                    </div>

                    <div className="space-y-sm">
                      <label className="block text-[14px] font-bold">신청 사유 (선택)</label>
                      <textarea value={form.applicationReason} onChange={(e) => setField("applicationReason", e.target.value)} rows={2} placeholder="참가 신청 사유를 입력해 주세요" className="w-full rounded-lg border border-hairline p-sm focus:border-primary-focus focus:ring-2 focus:ring-primary-focus/20 outline-none transition-all resize-none" />
                    </div>

                    {/* 선택한 부스 + 견적 요약 */}
                    <div className="space-y-sm border-t border-hairline pt-lg">
                      <label className="block text-[14px] font-bold">선택한 부스</label>
                      {selectedBooth ? (
                        <div className="w-full rounded-lg border-2 border-primary-focus px-sm py-sm bg-white text-[14px] font-body-strong text-primary">
                          {selectedBooth.boothCode} 부스
                          {selectedBooth.areaSqm ? ` · ${selectedBooth.areaSqm}sqm` : ""}
                          {selectedBooth.boothType ? ` · ${selectedBooth.boothType}` : ""}
                        </div>
                      ) : (
                        <div className="w-full h-[44px] rounded-lg border border-hairline px-sm bg-surface-pearl text-[14px] flex items-center text-ink-muted">
                          왼쪽 배치도에서 부스를 선택해 주세요
                        </div>
                      )}
                    </div>
                    <div className="bg-surface-pearl p-lg rounded-xl border border-hairline">
                      <div className="flex justify-between items-center">
                        <span className="text-[17px] font-bold">부스 임차료</span>
                        <span className="text-[21px] font-bold text-primary">
                          ₩ {selectedBooth?.price ? Number(selectedBooth.price).toLocaleString() : "0"}
                        </span>
                      </div>
                    </div>

                    {submitError && (
                      <p className="text-caption text-error bg-error/10 rounded-lg p-sm">{submitError}</p>
                    )}

                    <button
                      onClick={handleSubmit}
                      disabled={!canSubmit}
                      className="w-full text-white h-[52px] rounded-xl font-bold text-[17px] active:scale-95 duration-200 shadow-lg disabled:opacity-40 disabled:cursor-not-allowed bg-primary-focus shadow-primary-focus/20"
                    >
                      {submitting ? "제출 중..." : "부스 신청 완료하기"}
                    </button>
                    <p className="text-[12px] text-ink-muted text-center">
                      {canSubmit
                        ? "모든 조건이 충족되었습니다. 신청서를 제출해 주세요."
                        : "부스 선택, 필수 정보 입력, 견적서 첨부를 완료하면 신청할 수 있어요."}
                    </p>
                  </div>
                )}
              </div>
            </div>
          </div>
        )}
      </main>

      <footer className="bg-surface-container-low text-on-surface py-section w-full">
        <div className="max-w-[1200px] mx-auto px-lg text-center">
          <p className="text-[14px] text-on-surface-variant opacity-60">© 2026 EvenToday. All rights reserved.</p>
        </div>
      </footer>
    </div>
  );
}
