import { useEffect, useRef, useState } from "react";
import QRCode from "qrcode";
import Icon from "./Icon.jsx";
import useToast from "../hooks/useToast.js";
import { ApiError } from "../api/apiClient.js";
import {
  listBooths,
  createBooth,
  createBoothsBulk,
  updateBooth,
  updateBoothStatus,
  deleteBooth,
  updateBoothIntro,
  issueBoothQr,
} from "../api/boothApi.js";

const STATUS_LABEL = {
  AVAILABLE: "신청 가능",
  APPLICATION_PENDING: "신청 대기",
  ASSIGNED: "배정 완료",
  UNAVAILABLE: "사용 불가",
};
const STATUS_OPTIONS = Object.keys(STATUS_LABEL);
const PAGE_SIZE = 10;

const EMPTY_FILTERS = { status: "", floorName: "", zoneName: "", keyword: "" };
const EMPTY_SPEC_FORM = {
  boothCode: "",
  boothType: "",
  floorName: "",
  zoneName: "",
  locationDescription: "",
  widthMeter: "",
  depthMeter: "",
  areaSqm: "",
  price: "",
  electricityAvailable: false,
  waterAvailable: false,
  drainageAvailable: false,
  internetAvailable: false,
};
const EMPTY_BULK_FORM = { boothCodes: "", ...EMPTY_SPEC_FORM };
const EMPTY_INTRO_FORM = { displayName: "", shortIntro: "", description: "", exhibitionContent: "" };

const toNumberOrThrow = (value, label) => {
  if (value === "" || value == null) return null;
  const parsed = Number(value);
  if (!Number.isFinite(parsed)) {
    throw new Error(`${label} 값이 올바르지 않습니다.`);
  }
  return parsed;
};

const toSpecPayload = (form) => ({
  boothCode: form.boothCode,
  boothType: form.boothType,
  floorName: form.floorName || null,
  zoneName: form.zoneName || null,
  locationDescription: form.locationDescription || null,
  widthMeter: toNumberOrThrow(form.widthMeter, "가로(m)"),
  depthMeter: toNumberOrThrow(form.depthMeter, "세로(m)"),
  areaSqm: toNumberOrThrow(form.areaSqm, "면적(㎡)"),
  electricityAvailable: form.electricityAvailable,
  waterAvailable: form.waterAvailable,
  drainageAvailable: form.drainageAvailable,
  internetAvailable: form.internetAvailable,
  price: toNumberOrThrow(form.price, "가격(원)") ?? 0,
});

function EquipmentCheckboxes({ form, onChange }) {
  const items = [
    ["electricityAvailable", "전기"],
    ["waterAvailable", "급수"],
    ["drainageAvailable", "배수"],
    ["internetAvailable", "인터넷"],
  ];
  return (
    <div className="flex gap-md text-caption md:col-span-2">
      {items.map(([key, label]) => (
        <label key={key} className="flex items-center gap-1">
          <input
            type="checkbox"
            checked={form[key]}
            onChange={(e) => onChange({ ...form, [key]: e.target.checked })}
          />
          {label}
        </label>
      ))}
    </div>
  );
}

function SpecFormFields({ form, onChange, includeCode = true }) {
  return (
    <div className="grid grid-cols-1 md:grid-cols-2 gap-sm">
      {includeCode && (
        <input
          placeholder="부스 번호"
          value={form.boothCode}
          onChange={(e) => onChange({ ...form, boothCode: e.target.value })}
          className="border border-hairline rounded-lg px-md py-sm"
        />
      )}
      <input
        placeholder="부스 종류"
        value={form.boothType}
        onChange={(e) => onChange({ ...form, boothType: e.target.value })}
        className="border border-hairline rounded-lg px-md py-sm"
      />
      <input
        placeholder="층"
        value={form.floorName}
        onChange={(e) => onChange({ ...form, floorName: e.target.value })}
        className="border border-hairline rounded-lg px-md py-sm"
      />
      <input
        placeholder="구역"
        value={form.zoneName}
        onChange={(e) => onChange({ ...form, zoneName: e.target.value })}
        className="border border-hairline rounded-lg px-md py-sm"
      />
      <input
        placeholder="위치 설명"
        value={form.locationDescription ?? ""}
        onChange={(e) => onChange({ ...form, locationDescription: e.target.value })}
        className="border border-hairline rounded-lg px-md py-sm md:col-span-2"
      />
      <input
        type="number"
        placeholder="가로(m)"
        value={form.widthMeter}
        onChange={(e) => onChange({ ...form, widthMeter: e.target.value })}
        className="border border-hairline rounded-lg px-md py-sm"
      />
      <input
        type="number"
        placeholder="세로(m)"
        value={form.depthMeter}
        onChange={(e) => onChange({ ...form, depthMeter: e.target.value })}
        className="border border-hairline rounded-lg px-md py-sm"
      />
      <input
        type="number"
        placeholder="면적(㎡)"
        value={form.areaSqm}
        onChange={(e) => onChange({ ...form, areaSqm: e.target.value })}
        className="border border-hairline rounded-lg px-md py-sm"
      />
      <input
        type="number"
        placeholder="가격(원)"
        value={form.price}
        onChange={(e) => onChange({ ...form, price: e.target.value })}
        className="border border-hairline rounded-lg px-md py-sm"
      />
      <EquipmentCheckboxes form={form} onChange={onChange} />
    </div>
  );
}

export default function BoothManagementPanel({ eventId }) {
  const { showToast } = useToast();
  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [pageResult, setPageResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState(EMPTY_SPEC_FORM);
  const [showBulkForm, setShowBulkForm] = useState(false);
  const [bulkForm, setBulkForm] = useState(EMPTY_BULK_FORM);

  const [editingBoothId, setEditingBoothId] = useState(null);
  const [editForm, setEditForm] = useState(EMPTY_SPEC_FORM);

  const [introBoothId, setIntroBoothId] = useState(null);
  const [introForm, setIntroForm] = useState(EMPTY_INTRO_FORM);

  // 행사를 빠르게 전환할 때 이전 요청의 응답이 늦게 도착해 현재 화면을 덮어쓰는 것을 막기 위한 버전 가드.
  const requestVersionRef = useRef(0);
  // runAction 안에서 "이 액션이 시작된 뒤 행사가 바뀌었는가"를 판단하기 위한 세대 카운터.
  // eventId는 컴포넌트 prop이라 runAction 클로저 안에서 캡처한 값과 나중에 다시 읽는 값이
  // 항상 같은 렌더의 값이라 절대 달라지지 않는다(클로저이므로) - 그래서 eventId를 직접
  // 비교하면 항상 "안 바뀜"으로 나온다. eventId가 실제로 바뀔 때만 증가하는 이 ref로 비교해야
  // 진짜 세대 변화를 감지할 수 있다.
  const eventGenerationRef = useRef(0);

  const [qrBoothId, setQrBoothId] = useState(null);
  const [qrInfo, setQrInfo] = useState(null);
  const [qrImageUrl, setQrImageUrl] = useState("");
  const [qrError, setQrError] = useState("");

  const showQr = async (boothId, info) => {
    setQrBoothId(boothId);
    setQrInfo(info);
    setQrImageUrl("");
    setQrError("");
    // 스캔하면 부스 상세 페이지로 이동하도록, 토큰 원문 대신 페이지 URL을 인코딩한다.
    const scanUrl = `${window.location.origin}/booth-detail?eventId=${eventId}&boothId=${boothId}&qr=${info.qrToken}`;
    try {
      const dataUrl = await QRCode.toDataURL(scanUrl, { width: 160, margin: 1 });
      setQrImageUrl(dataUrl);
    } catch (err) {
      setQrImageUrl("");
      setQrError(err instanceof Error && err.message ? err.message : "QR 이미지를 생성하지 못했습니다.");
    }
  };

  // loadBooths를 호출할 때마다(같은 행사 안에서 검색/페이지 이동/재조회가 겹치는 경우 포함)
  // 매번 새 버전을 발급해, 나중에 시작됐지만 먼저 끝난 요청만 반영되도록 한다. eventId가
  // 바뀌는 effect도 결국 이 함수를 호출하므로 행사 전환도 자연히 최신 버전으로 갱신된다.
  const loadBooths = async (id, currentFilters, currentPage) => {
    const version = ++requestVersionRef.current;
    setLoading(true);
    try {
      const result = await listBooths(id, { ...currentFilters, page: currentPage, size: PAGE_SIZE });
      if (requestVersionRef.current !== version) return;
      setPageResult(result);
    } catch (err) {
      if (requestVersionRef.current !== version) return;
      setPageResult(null);
      showToast(
        err instanceof ApiError ? err.message : "부스 목록을 불러오지 못했습니다.",
        { type: "error" }
      );
    } finally {
      if (requestVersionRef.current === version) {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    eventGenerationRef.current += 1;
    setPage(0);
    setFilters(EMPTY_FILTERS);
    setEditingBoothId(null);
    setIntroBoothId(null);
    closeQr();
    setPageResult(null);
    // 이전 행사에서 진행 중이던 액션이 있었다면 그 결과는 이제 무의미하다 - runAction의
    // 가드가 그 액션의 후속 상태 변경은 막아주지만, submitting 자체는 그 액션의 finally가
    // (가드 때문에) 건드리지 않으므로 여기서 직접 꺼줘야 다음 행사에서 버튼이 계속
    // 비활성화된 채로 남지 않는다.
    setSubmitting(false);
    if (eventId) {
      loadBooths(eventId, EMPTY_FILTERS, 0);
    } else {
      // 진행 중이던 요청이 있었다면 그 응답은 이제 무의미하므로 버전을 올려 무시하고,
      // 로딩 상태도 여기서 직접 꺼야 한다 (그 요청의 finally는 버전 불일치로 스킵됨).
      requestVersionRef.current += 1;
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  const handleSearch = () => {
    setPage(0);
    loadBooths(eventId, filters, 0);
  };

  const changePage = (nextPage) => {
    setPage(nextPage);
    loadBooths(eventId, filters, nextPage);
  };

  const refresh = () => loadBooths(eventId, filters, page);

  const runAction = async (actionFn, successMessage) => {
    if (submitting) return;
    // 이 액션이 시작된 시점의 "행사 세대"를 기억해뒀다가, 완료 시점에 사용자가 이미 다른
    // 행사로 넘어갔으면(=세대가 달라졌으면) 그 행사 데이터를 재조회/메시지 표시하지 않는다.
    // eventId 값 자체를 비교하면 안 된다 - eventId는 이 클로저가 만들어진 렌더의 값을
    // 그대로 캡처하고 있어서 나중에 다시 읽어도 항상 같은 값이라 절대 안 바뀐 것처럼 보인다.
    const actionGeneration = eventGenerationRef.current;
    setSubmitting(true);
    try {
      await actionFn();
      if (eventGenerationRef.current !== actionGeneration) return;
      showToast(successMessage);
      await refresh();
    } catch (err) {
      if (eventGenerationRef.current !== actionGeneration) return;
      const message = err instanceof Error && err.message ? err.message : "요청에 실패했습니다.";
      showToast(message, { type: "error" });
    } finally {
      if (eventGenerationRef.current === actionGeneration) {
        setSubmitting(false);
      }
    }
  };

  const handleCreate = () =>
    runAction(async () => {
      await createBooth(eventId, toSpecPayload(createForm));
      setCreateForm(EMPTY_SPEC_FORM);
      setShowCreateForm(false);
    }, "부스를 등록했습니다.");

  const handleBulkCreate = () =>
    runAction(async () => {
      const boothCodes = bulkForm.boothCodes
        .split(",")
        .map((code) => code.trim())
        .filter(Boolean);
      await createBoothsBulk(eventId, { ...toSpecPayload(bulkForm), boothCodes });
      setBulkForm(EMPTY_BULK_FORM);
      setShowBulkForm(false);
    }, "부스를 일괄 등록했습니다.");

  const startEdit = (booth) => {
    setIntroBoothId(null);
    setEditingBoothId(booth.id);
    setEditForm({
      boothCode: booth.boothCode,
      boothType: booth.boothType,
      floorName: booth.floorName ?? "",
      zoneName: booth.zoneName ?? "",
      locationDescription: booth.locationDescription ?? "",
      widthMeter: booth.widthMeter ?? "",
      depthMeter: booth.depthMeter ?? "",
      areaSqm: booth.areaSqm ?? "",
      price: booth.price,
      electricityAvailable: booth.electricityAvailable,
      waterAvailable: booth.waterAvailable,
      drainageAvailable: booth.drainageAvailable,
      internetAvailable: booth.internetAvailable,
    });
  };

  const handleUpdate = () =>
    runAction(async () => {
      await updateBooth(eventId, editingBoothId, toSpecPayload(editForm));
      setEditingBoothId(null);
    }, "부스 정보를 수정했습니다.");

  const handleStatusChange = (boothId, status) =>
    runAction(() => updateBoothStatus(eventId, boothId, status), "상태를 변경했습니다.");

  const handleDelete = (boothId) => {
    if (!window.confirm("이 부스를 삭제할까요? 되돌릴 수 없습니다.")) return;
    runAction(() => deleteBooth(eventId, boothId), "삭제했습니다.");
  };

  const startIntroEdit = (booth) => {
    setEditingBoothId(null);
    setIntroBoothId(booth.id);
    setIntroForm({
      displayName: booth.displayName ?? "",
      shortIntro: booth.shortIntro ?? "",
      description: booth.description ?? "",
      exhibitionContent: booth.exhibitionContent ?? "",
    });
  };

  const handleIntroSave = () =>
    runAction(async () => {
      await updateBoothIntro(eventId, introBoothId, introForm);
      setIntroBoothId(null);
    }, "부스 소개를 수정했습니다.");

  const closeQr = () => {
    setQrBoothId(null);
    setQrInfo(null);
    setQrImageUrl("");
    setQrError("");
  };

  // 발급은 멱등이라(이미 있으면 기존 QR을 그대로 돌려줌) 버튼 하나로 발급·조회를 겸한다.
  // 이미 열려있는 부스를 다시 누르면 닫는다.
  const handleToggleQr = (boothId) => {
    if (qrBoothId === boothId) {
      closeQr();
      return;
    }
    return runAction(async () => {
      const result = await issueBoothQr(eventId, boothId);
      await showQr(boothId, result);
    }, "QR을 불러왔습니다.");
  };

  const booths = pageResult?.content ?? [];

  return (
    <section className="space-y-lg">
      <div className="flex justify-between items-center flex-wrap gap-sm">
        <h1 className="font-display-lg text-[26px]">부스 관리</h1>
      </div>

      {!eventId && (
        <div className="bg-white border border-hairline rounded-xl p-lg text-caption text-ink-muted">
          상단에서 관리할 행사를 먼저 선택해 주세요.
        </div>
      )}

      {loading && <p className="text-caption text-ink-muted">불러오는 중...</p>}

      {eventId && (
        <>
          <div className="bg-white border border-hairline rounded-xl p-lg space-y-sm">
            <div className="grid grid-cols-2 md:grid-cols-5 gap-sm">
              <select
                value={filters.status}
                onChange={(e) => setFilters({ ...filters, status: e.target.value })}
                className="border border-hairline rounded-lg px-md py-sm bg-white"
              >
                <option value="">전체 상태</option>
                {STATUS_OPTIONS.map((status) => (
                  <option key={status} value={status}>
                    {STATUS_LABEL[status]}
                  </option>
                ))}
              </select>
              <input
                placeholder="층"
                value={filters.floorName}
                onChange={(e) => setFilters({ ...filters, floorName: e.target.value })}
                className="border border-hairline rounded-lg px-md py-sm"
              />
              <input
                placeholder="구역"
                value={filters.zoneName}
                onChange={(e) => setFilters({ ...filters, zoneName: e.target.value })}
                className="border border-hairline rounded-lg px-md py-sm"
              />
              <input
                placeholder="부스명·기업명·번호 검색"
                value={filters.keyword}
                onChange={(e) => setFilters({ ...filters, keyword: e.target.value })}
                className="border border-hairline rounded-lg px-md py-sm md:col-span-1"
              />
              <button
                onClick={handleSearch}
                disabled={submitting}
                className="px-lg py-sm border border-hairline rounded-full text-caption font-body-strong"
              >
                검색
              </button>
            </div>
            <div className="flex gap-sm">
              <button
                onClick={() => {
                  setShowBulkForm(false);
                  setShowCreateForm((prev) => !prev);
                }}
                className="text-caption border border-hairline rounded-full px-md py-1"
              >
                + 새 부스 등록
              </button>
              <button
                onClick={() => {
                  setShowCreateForm(false);
                  setShowBulkForm((prev) => !prev);
                }}
                className="text-caption border border-hairline rounded-full px-md py-1"
              >
                + 부스 일괄 등록
              </button>
            </div>
          </div>

          {showCreateForm && (
            <div className="bg-white border border-hairline rounded-xl p-lg space-y-sm">
              <p className="font-body-strong">새 부스 등록</p>
              <SpecFormFields form={createForm} onChange={setCreateForm} />
              <button
                onClick={handleCreate}
                disabled={submitting}
                className="px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong disabled:opacity-40"
              >
                등록
              </button>
            </div>
          )}

          {showBulkForm && (
            <div className="bg-white border border-hairline rounded-xl p-lg space-y-sm">
              <p className="font-body-strong">부스 일괄 등록</p>
              <p className="text-[11px] text-ink-muted">부스 번호를 쉼표(,)로 구분해 입력하면 동일한 조건으로 한 번에 등록됩니다.</p>
              <input
                placeholder="예: A-01, A-02, A-03"
                value={bulkForm.boothCodes}
                onChange={(e) => setBulkForm({ ...bulkForm, boothCodes: e.target.value })}
                className="border border-hairline rounded-lg px-md py-sm w-full"
              />
              <SpecFormFields form={bulkForm} onChange={setBulkForm} includeCode={false} />
              <button
                onClick={handleBulkCreate}
                disabled={submitting}
                className="px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong disabled:opacity-40"
              >
                일괄 등록
              </button>
            </div>
          )}

          <div className="bg-white border border-hairline rounded-xl overflow-hidden">
            {booths.length === 0 ? (
              <div className="flex items-center gap-sm text-caption text-ink-muted p-lg">
                <Icon name="info" className="text-[18px]" />
                조건에 맞는 부스가 없습니다.
              </div>
            ) : (
              <div className="divide-y divide-divider-soft">
                {booths.map((booth) => (
                  <div key={booth.id} className="p-lg space-y-sm">
                    <div className="flex items-center gap-sm flex-wrap">
                      <span className="font-body-strong">{booth.boothCode}</span>
                      <span className="text-caption text-ink-muted">{booth.boothType}</span>
                      {(booth.floorName || booth.zoneName) && (
                        <span className="text-caption text-ink-muted">
                          {booth.floorName} {booth.zoneName}
                        </span>
                      )}
                      <span className="text-caption text-ink-muted">{Number(booth.price).toLocaleString()}원</span>
                      <select
                        value={booth.status}
                        onChange={(e) => handleStatusChange(booth.id, e.target.value)}
                        disabled={submitting}
                        className="ml-auto text-[12px] border border-hairline rounded-full px-sm py-1 bg-white"
                      >
                        {STATUS_OPTIONS.map((status) => (
                          <option key={status} value={status}>
                            {STATUS_LABEL[status]}
                          </option>
                        ))}
                      </select>
                    </div>
                    <div className="flex gap-sm flex-wrap">
                      <button
                        onClick={() => startEdit(booth)}
                        className="text-caption border border-hairline rounded-full px-md py-1"
                      >
                        수정
                      </button>
                      {booth.status === "ASSIGNED" && (
                        <button
                          onClick={() => startIntroEdit(booth)}
                          className="text-caption border border-hairline rounded-full px-md py-1"
                        >
                          소개 관리
                        </button>
                      )}
                      {booth.status === "ASSIGNED" && (
                        <button
                          onClick={() => handleToggleQr(booth.id)}
                          disabled={submitting}
                          className="text-caption border border-hairline rounded-full px-md py-1"
                        >
                          {qrBoothId === booth.id ? "QR 닫기" : "QR 보기"}
                        </button>
                      )}
                      <button
                        onClick={() => handleDelete(booth.id)}
                        disabled={submitting || booth.status !== "AVAILABLE"}
                        className="text-caption border border-error text-error rounded-full px-md py-1 disabled:opacity-40 disabled:border-hairline disabled:text-ink-muted"
                      >
                        삭제
                      </button>
                    </div>
                    {qrBoothId === booth.id && qrInfo && (
                      <div className="flex items-center gap-md p-md border border-hairline rounded-lg bg-surface-container-lowest">
                        {qrImageUrl ? (
                          <img src={qrImageUrl} alt={`${booth.boothCode} 부스 QR`} className="w-32 h-32" />
                        ) : qrError ? (
                          <div className="w-32 h-32 flex items-center justify-center text-caption text-error text-center px-sm">
                            {qrError}
                          </div>
                        ) : (
                          <div className="w-32 h-32 flex items-center justify-center text-caption text-ink-muted">
                            생성 중...
                          </div>
                        )}
                        <div className="text-[11px] text-ink-muted space-y-1">
                          <p>발급: {new Date(qrInfo.qrIssuedAt).toLocaleString()}</p>
                          <p>현장 안내용 QR입니다. 외부에 공유하지 마세요.</p>
                        </div>
                      </div>
                    )}

                    {editingBoothId === booth.id && (
                      <div className="border border-hairline rounded-lg p-md space-y-sm bg-surface-container-lowest">
                        <SpecFormFields form={editForm} onChange={setEditForm} />
                        <div className="flex gap-sm">
                          <button
                            onClick={handleUpdate}
                            disabled={submitting}
                            className="px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong disabled:opacity-40"
                          >
                            저장
                          </button>
                          <button
                            onClick={() => setEditingBoothId(null)}
                            className="px-lg py-sm border border-hairline rounded-full text-caption"
                          >
                            취소
                          </button>
                        </div>
                      </div>
                    )}

                    {introBoothId === booth.id && (
                      <div className="border border-hairline rounded-lg p-md space-y-sm bg-surface-container-lowest">
                        <input
                          placeholder="관람객용 부스명"
                          value={introForm.displayName}
                          onChange={(e) => setIntroForm({ ...introForm, displayName: e.target.value })}
                          className="border border-hairline rounded-lg px-md py-sm w-full"
                        />
                        <input
                          placeholder="한 줄 소개"
                          value={introForm.shortIntro}
                          onChange={(e) => setIntroForm({ ...introForm, shortIntro: e.target.value })}
                          className="border border-hairline rounded-lg px-md py-sm w-full"
                        />
                        <textarea
                          placeholder="상세 소개"
                          value={introForm.description}
                          onChange={(e) => setIntroForm({ ...introForm, description: e.target.value })}
                          className="border border-hairline rounded-lg px-md py-sm w-full"
                        />
                        <textarea
                          placeholder="전시·판매 내용"
                          value={introForm.exhibitionContent}
                          onChange={(e) => setIntroForm({ ...introForm, exhibitionContent: e.target.value })}
                          className="border border-hairline rounded-lg px-md py-sm w-full"
                        />
                        <div className="flex gap-sm">
                          <button
                            onClick={handleIntroSave}
                            disabled={submitting}
                            className="px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong disabled:opacity-40"
                          >
                            저장
                          </button>
                          <button
                            onClick={() => setIntroBoothId(null)}
                            className="px-lg py-sm border border-hairline rounded-full text-caption"
                          >
                            취소
                          </button>
                        </div>
                      </div>
                    )}
                  </div>
                ))}
              </div>
            )}
          </div>

          {pageResult && pageResult.totalPages > 1 && (
            <div className="flex items-center justify-center gap-sm">
              <button
                onClick={() => changePage(page - 1)}
                disabled={pageResult.first || loading}
                className="text-caption border border-hairline rounded-full px-md py-1 disabled:opacity-40"
              >
                이전
              </button>
              <span className="text-caption text-ink-muted">
                {pageResult.page + 1} / {pageResult.totalPages}
              </span>
              <button
                onClick={() => changePage(page + 1)}
                disabled={pageResult.last || loading}
                className="text-caption border border-hairline rounded-full px-md py-1 disabled:opacity-40"
              >
                다음
              </button>
            </div>
          )}
        </>
      )}
    </section>
  );
}
