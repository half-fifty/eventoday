import { useEffect, useState } from "react";
import QRCode from "qrcode";
import Icon from "./Icon.jsx";
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

const toSpecPayload = (form) => ({
  boothCode: form.boothCode,
  boothType: form.boothType,
  floorName: form.floorName || null,
  zoneName: form.zoneName || null,
  locationDescription: form.locationDescription || null,
  widthMeter: form.widthMeter === "" ? null : Number(form.widthMeter),
  depthMeter: form.depthMeter === "" ? null : Number(form.depthMeter),
  areaSqm: form.areaSqm === "" ? null : Number(form.areaSqm),
  electricityAvailable: form.electricityAvailable,
  waterAvailable: form.waterAvailable,
  drainageAvailable: form.drainageAvailable,
  internetAvailable: form.internetAvailable,
  price: Number(form.price || 0),
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
  const [loadedEventId, setLoadedEventId] = useState(null);

  const [filters, setFilters] = useState(EMPTY_FILTERS);
  const [page, setPage] = useState(0);
  const [pageResult, setPageResult] = useState(null);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const [showCreateForm, setShowCreateForm] = useState(false);
  const [createForm, setCreateForm] = useState(EMPTY_SPEC_FORM);
  const [showBulkForm, setShowBulkForm] = useState(false);
  const [bulkForm, setBulkForm] = useState(EMPTY_BULK_FORM);

  const [editingBoothId, setEditingBoothId] = useState(null);
  const [editForm, setEditForm] = useState(EMPTY_SPEC_FORM);

  const [introBoothId, setIntroBoothId] = useState(null);
  const [introForm, setIntroForm] = useState(EMPTY_INTRO_FORM);

  const [qrBoothId, setQrBoothId] = useState(null);
  const [qrInfo, setQrInfo] = useState(null);
  const [qrImageUrl, setQrImageUrl] = useState("");

  const showQr = async (boothId, info) => {
    setQrBoothId(boothId);
    setQrInfo(info);
    setQrImageUrl("");
    // 스캔하면 부스 상세 페이지로 이동하도록, 토큰 원문 대신 페이지 URL을 인코딩한다.
    const scanUrl = `${window.location.origin}/booth-detail?boothId=${boothId}&qr=${info.qrToken}`;
    try {
      const dataUrl = await QRCode.toDataURL(scanUrl, { width: 160, margin: 1 });
      setQrImageUrl(dataUrl);
    } catch {
      setQrImageUrl("");
    }
  };

  const loadBooths = async (id, currentFilters, currentPage) => {
    setLoading(true);
    setError("");
    try {
      const result = await listBooths(id, { ...currentFilters, page: currentPage, size: PAGE_SIZE });
      setPageResult(result);
      setLoadedEventId(id);
    } catch (err) {
      setPageResult(null);
      setError(err instanceof ApiError ? `${err.code}: ${err.message}` : "부스 목록을 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setPage(0);
    setFilters(EMPTY_FILTERS);
    if (eventId) {
      loadBooths(eventId, EMPTY_FILTERS, 0);
    } else {
      setLoadedEventId(null);
      setPageResult(null);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  const handleSearch = () => {
    setPage(0);
    loadBooths(loadedEventId, filters, 0);
  };

  const changePage = (nextPage) => {
    setPage(nextPage);
    loadBooths(loadedEventId, filters, nextPage);
  };

  const refresh = () => loadBooths(loadedEventId, filters, page);

  const runAction = async (actionFn, successMessage) => {
    if (submitting) return;
    setSubmitting(true);
    setError("");
    setMessage("");
    try {
      await actionFn();
      setMessage(successMessage);
      await refresh();
    } catch (err) {
      setError(err instanceof ApiError ? `${err.code}: ${err.message}` : "요청에 실패했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  const handleCreate = () =>
    runAction(async () => {
      await createBooth(loadedEventId, toSpecPayload(createForm));
      setCreateForm(EMPTY_SPEC_FORM);
      setShowCreateForm(false);
    }, "부스를 등록했습니다.");

  const handleBulkCreate = () =>
    runAction(async () => {
      const boothCodes = bulkForm.boothCodes
        .split(",")
        .map((code) => code.trim())
        .filter(Boolean);
      await createBoothsBulk(loadedEventId, { ...toSpecPayload(bulkForm), boothCodes });
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
      await updateBooth(loadedEventId, editingBoothId, toSpecPayload(editForm));
      setEditingBoothId(null);
    }, "부스 정보를 수정했습니다.");

  const handleStatusChange = (boothId, status) =>
    runAction(() => updateBoothStatus(loadedEventId, boothId, status), "상태를 변경했습니다.");

  const handleDelete = (boothId) => {
    if (!window.confirm("이 부스를 삭제할까요? 되돌릴 수 없습니다.")) return;
    runAction(() => deleteBooth(loadedEventId, boothId), "삭제했습니다.");
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
      await updateBoothIntro(loadedEventId, introBoothId, introForm);
      setIntroBoothId(null);
    }, "부스 소개를 수정했습니다.");

  const closeQr = () => {
    setQrBoothId(null);
    setQrInfo(null);
    setQrImageUrl("");
  };

  // 발급은 멱등이라(이미 있으면 기존 QR을 그대로 돌려줌) 버튼 하나로 발급·조회를 겸한다.
  // 이미 열려있는 부스를 다시 누르면 닫는다.
  const handleToggleQr = (boothId) => {
    if (qrBoothId === boothId) {
      closeQr();
      return;
    }
    return runAction(async () => {
      const result = await issueBoothQr(loadedEventId, boothId);
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
      {error && <p className="text-caption text-error">{error}</p>}
      {message && <p className="text-caption text-status-available">{message}</p>}

      {loadedEventId && (
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
                      <button
                        onClick={() => handleToggleQr(booth.id)}
                        disabled={submitting}
                        className="text-caption border border-hairline rounded-full px-md py-1"
                      >
                        {qrBoothId === booth.id ? "QR 닫기" : "QR 보기"}
                      </button>
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
                        ) : (
                          <div className="w-32 h-32 flex items-center justify-center text-caption text-ink-muted">
                            생성 중...
                          </div>
                        )}
                        <div className="text-[11px] text-ink-muted space-y-1">
                          <p>발급: {new Date(qrInfo.qrIssuedAt).toLocaleString()}</p>
                          <p className="break-all">토큰: {qrInfo.qrToken}</p>
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
