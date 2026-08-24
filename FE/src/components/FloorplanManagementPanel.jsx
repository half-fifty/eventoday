import { useCallback, useEffect, useRef, useState } from "react";
import Icon from "./Icon.jsx";
import FloorplanCanvasEditor from "./FloorplanCanvasEditor.jsx";
import { ApiError } from "../api/apiClient.js";
import { uploadFile, fileDownloadUrl } from "../api/fileApi.js";
import {
  listVenueMaps,
  createVenueMap,
  publishVenueMap,
  deleteVenueMap,
  suggestAutoLayout,
  upsertPositions,
} from "../api/venueMapApi.js";
import { listBooths } from "../api/boothApi.js";
import { pinFontSizeClass } from "../utils/venueMapPin.js";

const MAP_TYPE_LABEL = { RECRUITMENT: "모집 공고용", VISITOR: "관람객용" };
const MAP_TYPE_OPTIONS = Object.keys(MAP_TYPE_LABEL);
const EMPTY_UPLOAD_FORM = { floorName: "", mapType: "VISITOR", file: null };
const BOOTH_PAGE_SIZE = 100; // BoothService.MAX_PAGE_SIZE

// 부스가 100개를 넘는 행사도 팔레트에 전부 뜨도록, 마지막 페이지까지 순차 조회해 합친다.
// result.last가 없는 응답이 오더라도 무한 루프에 빠지지 않도록 페이지 상한과
// "가득 차지 않은 페이지" 조건을 함께 종료 조건으로 둔다.
const MAX_BOOTH_PAGES = 50; // 방어적 상한 (최대 5,000개 부스)
const listAllBooths = async (eventId) => {
  let all = [];
  for (let page = 0; page < MAX_BOOTH_PAGES; page += 1) {
    const result = await listBooths(eventId, { page, size: BOOTH_PAGE_SIZE });
    const content = result?.content ?? [];
    all = all.concat(content);
    if (!result || result.last || content.length < BOOTH_PAGE_SIZE) break;
  }
  return all;
};

const readImageDimensions = (file) =>
  new Promise((resolve, reject) => {
    const img = new Image();
    const url = URL.createObjectURL(file);
    img.onload = () => {
      resolve({ width: img.naturalWidth, height: img.naturalHeight });
      URL.revokeObjectURL(url);
    };
    img.onerror = () => {
      URL.revokeObjectURL(url);
      reject(new Error("이미지를 읽을 수 없습니다."));
    };
    img.src = url;
  });

export default function FloorplanManagementPanel({ eventId }) {
  const [maps, setMaps] = useState([]);
  const [booths, setBooths] = useState([]);
  const [loading, setLoading] = useState(false);
  const [submitting, setSubmitting] = useState(false);
  const [error, setError] = useState("");
  const [message, setMessage] = useState("");

  const [uploadForm, setUploadForm] = useState(EMPTY_UPLOAD_FORM);
  // <input type="file">는 uncontrolled라 uploadForm.file을 null로 되돌려도 브라우저가 보여주는
  // 파일명은 그대로 남는다. key를 바꿔 매 업로드 후 input을 새로 마운트해서 강제로 비운다.
  const [fileInputKey, setFileInputKey] = useState(0);
  const [showCanvasEditor, setShowCanvasEditor] = useState(false);
  // <input type="file">는 실제로 고른 파일만 자기 화면에 표시하므로, 캔버스로 직접 그려서
  // uploadForm.file에 채워 넣은 경우엔 input이 계속 "선택된 파일 없음"으로 보인다.
  // 이 경우를 구분해서 별도 안내 문구를 보여주기 위한 플래그.
  const [fileIsDrawn, setFileIsDrawn] = useState(false);
  const [selectedMapId, setSelectedMapId] = useState(null);
  const [positions, setPositions] = useState([]);
  // 선택된(또는 직접 그린) 파일의 썸네일 미리보기 - 업로드 전에 뭘 골랐는지 눈으로 확인시켜준다.
  const [filePreviewUrl, setFilePreviewUrl] = useState(null);

  const fileInputRef = useRef(null);
  const imageRef = useRef(null);
  const draggingBoothIdRef = useRef(null);
  // 행사를 빠르게 전환할 때 이전 요청의 응답이 늦게 도착해 현재 화면을 덮어쓰는 것을 막기 위한 버전 가드.
  const requestVersionRef = useRef(0);
  // runAction 안에서 "이 액션이 시작된 뒤 행사가 바뀌었는가"를 판단하기 위한 세대 카운터.
  // eventId는 컴포넌트 prop이라 runAction 클로저 안에서 캡처한 값과 나중에 다시 읽는 값이
  // 항상 같은 렌더의 값이라 절대 달라지지 않는다(클로저이므로) - 그래서 eventId를 직접
  // 비교하면 항상 "안 바뀜"으로 나온다. eventId가 실제로 바뀔 때만 증가하는 이 ref로 비교해야
  // 진짜 세대 변화를 감지할 수 있다.
  const eventGenerationRef = useRef(0);
  // handleAutoLayout이 응답을 받았을 때 "그 사이에 다른 평면도를 선택하지 않았는가"를
  // 판단하기 위한 세대 카운터. selectedMapId를 직접 비교하면 안 되는 이유는 eventId와
  // 동일하다 - 클로저가 캡처한 렌더 시점의 값이라 나중에 다시 읽어도 항상 같다.
  const mapSelectionGenerationRef = useRef(0);

  // 호출할 때마다 새 버전을 발급해, 나중에 시작됐지만 먼저 끝난 요청만 반영되도록 한다.
  // eventId가 바뀌는 effect도 결국 이 함수를 호출하므로 행사 전환도 자연히 최신 버전으로 갱신된다.
  const loadAll = async (id) => {
    const version = ++requestVersionRef.current;
    setLoading(true);
    setError("");
    try {
      const [mapList, allBooths] = await Promise.all([
        listVenueMaps(id),
        listAllBooths(id),
      ]);
      if (requestVersionRef.current !== version) return;
      setMaps(mapList);
      setBooths(allBooths);
    } catch (err) {
      if (requestVersionRef.current !== version) return;
      setError(err instanceof ApiError ? `${err.code}: ${err.message}` : "평면도 정보를 불러오지 못했습니다.");
    } finally {
      if (requestVersionRef.current === version) {
        setLoading(false);
      }
    }
  };

  useEffect(() => {
    eventGenerationRef.current += 1;
    mapSelectionGenerationRef.current += 1;
    setUploadForm(EMPTY_UPLOAD_FORM);
    setFileInputKey((prev) => prev + 1);
    setFileIsDrawn(false);
    setShowCanvasEditor(false);
    setSelectedMapId(null);
    setPositions([]);
    setMessage("");
    setError("");
    setMaps([]);
    setBooths([]);
    // 이전 행사에서 진행 중이던 액션이 있었다면 그 결과는 이제 무의미하다 - runAction의
    // 가드가 후속 상태 변경은 막아주지만 submitting 자체는 그 액션의 finally가(가드 때문에)
    // 건드리지 않으므로 여기서 직접 꺼줘야 다음 행사에서 버튼이 계속 비활성화된 채로 남지 않는다.
    setSubmitting(false);
    if (eventId) {
      loadAll(eventId);
    } else {
      // 진행 중이던 요청이 있었다면 그 응답은 이제 무의미하므로 버전을 올려 무시하고,
      // 로딩 상태도 여기서 직접 꺼야 한다 (그 요청의 finally는 버전 불일치로 스킵됨).
      requestVersionRef.current += 1;
      setLoading(false);
    }
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, [eventId]);

  const selectedMap = maps.find((m) => m.id === selectedMapId) || null;

  const selectMap = (map) => {
    mapSelectionGenerationRef.current += 1;
    // handleAutoLayout의 finally는 이 세대가 바뀌면 더 이상 실행되지 않으므로, 그 사이에
    // 진행 중이던 요청이 있었다면 여기서 직접 꺼야 submitting이 계속 true로 남지 않는다.
    setSubmitting(false);
    setSelectedMapId(map.id);
    setPositions(
      (map.positions ?? []).map((p) => ({
        boothId: p.boothId,
        boothCode: p.boothCode,
        xRatio: Number(p.xRatio),
        yRatio: Number(p.yRatio),
      }))
    );
  };

  // onSuccess: 재조회/메시지 표시와 마찬가지로 "행사가 안 바뀐 경우에만" 실행되어야 하는
  // 부수 효과(예: 업로드 폼 초기화)를 넘긴다. actionFn 안에서 바로 호출하면 이 가드를 우회하게 된다.
  const runAction = async (actionFn, successMessage, onSuccess) => {
    if (submitting) return;
    // 이 액션이 시작된 시점의 "행사 세대"를 기억해뒀다가, 완료 시점에 사용자가 이미 다른
    // 행사로 넘어갔으면(=세대가 달라졌으면) 그 행사 데이터를 재조회/메시지 표시하지 않는다.
    // eventId 값 자체를 비교하면 안 된다 - eventId는 이 클로저가 만들어진 렌더의 값을
    // 그대로 캡처하고 있어서 나중에 다시 읽어도 항상 같은 값이라 절대 안 바뀐 것처럼 보인다.
    const actionGeneration = eventGenerationRef.current;
    setSubmitting(true);
    setError("");
    setMessage("");
    try {
      await actionFn();
      if (eventGenerationRef.current !== actionGeneration) return;
      onSuccess?.();
      setMessage(successMessage);
      await loadAll(eventId);
    } catch (err) {
      if (eventGenerationRef.current !== actionGeneration) return;
      setError(err instanceof ApiError ? `${err.code}: ${err.message}` : err.message || "요청에 실패했습니다.");
    } finally {
      if (eventGenerationRef.current === actionGeneration) {
        setSubmitting(false);
      }
    }
  };

  const handleFileChange = (e) => {
    const file = e.target.files?.[0] || null;
    setFileIsDrawn(false);
    setUploadForm((prev) => ({ ...prev, file }));
  };

  // uploadForm.file이 바뀔 때마다(파일 선택이든 직접 그리기든) 썸네일 미리보기를 새로 만들고,
  // 이전 objectURL은 반드시 해제해야 메모리에 계속 쌓이지 않는다.
  useEffect(() => {
    if (!uploadForm.file) {
      setFilePreviewUrl(null);
      return undefined;
    }
    const url = URL.createObjectURL(uploadForm.file);
    setFilePreviewUrl(url);
    return () => URL.revokeObjectURL(url);
  }, [uploadForm.file]);

  // FloorplanCanvasEditor가 만든 이미지를 실제 파일을 고른 것과 동일하게 취급한다 - 그 뒤
  // dimensions 계산/업로드/평면도 생성은 handleUpload의 기존 로직을 그대로 탄다.
  const handleCanvasEditorComplete = (file) => {
    setFileIsDrawn(true);
    setUploadForm((prev) => ({ ...prev, file }));
    setShowCanvasEditor(false);
  };

  const handleUpload = () =>
    runAction(async () => {
      if (!uploadForm.file) throw new Error("이미지를 선택해 주세요.");
      if (!uploadForm.floorName.trim()) throw new Error("층 이름을 입력해 주세요.");

      const dimensions = await readImageDimensions(uploadForm.file);
      const uploaded = await uploadFile(uploadForm.file, "PUBLIC");
      await createVenueMap(eventId, {
        mapType: uploadForm.mapType,
        floorName: uploadForm.floorName.trim(),
        imageFileId: uploaded.fileId,
        originalWidth: dimensions.width,
        originalHeight: dimensions.height,
      });
    }, "평면도를 업로드했습니다.", () => {
      setUploadForm(EMPTY_UPLOAD_FORM);
      setFileInputKey((prev) => prev + 1);
      setFileIsDrawn(false);
    });

  const handlePublish = (mapId) => runAction(() => publishVenueMap(eventId, mapId), "게시했습니다.");

  const handleDelete = (mapId) => {
    if (!window.confirm("이 평면도를 삭제할까요? 되돌릴 수 없습니다.")) return;
    runAction(async () => {
      await deleteVenueMap(eventId, mapId);
      if (selectedMapId === mapId) {
        setSelectedMapId(null);
        setPositions([]);
      }
    }, "삭제했습니다.");
  };

  const handleSavePositions = () =>
    runAction(
      () =>
        upsertPositions(
          eventId,
          selectedMapId,
          positions.map(({ boothId, xRatio, yRatio }) => ({ boothId, xRatio, yRatio }))
        ),
      "좌표를 저장했습니다."
    );

  // 좌표를 서버에 저장하지 않는 "제안"이라 loadAll을 다시 부르지 않는다 - 이미 배치된
  // 부스는 관리자가 수동으로 잡은 위치를 덮어쓰지 않도록 건너뛰고, "+ 부스" 버튼과 동일하게
  // 같은 용도의 다른 층에 이미 배치된 부스도 여기서 중복 배치되지 않도록 건너뛴다.
  const handleAutoLayout = async () => {
    if (!selectedMapId || submitting) return;
    const actionGeneration = eventGenerationRef.current;
    const actionMapId = selectedMapId;
    const actionMapGeneration = mapSelectionGenerationRef.current;
    setSubmitting(true);
    setError("");
    setMessage("");
    try {
      const suggestions = await suggestAutoLayout(eventId, actionMapId);
      if (eventGenerationRef.current !== actionGeneration
          || mapSelectionGenerationRef.current !== actionMapGeneration) {
        return;
      }

      // setPositions에 넘기는 업데이터 함수는 React가 나중에(비동기로) 실행하므로, 그 안에서
      // 부수효과로 카운터를 세면 아래 메시지 계산 시점엔 아직 반영되지 않은 값을 읽게 된다.
      // 그래서 분류/카운트는 여기서 미리 순수하게 끝내고, setPositions엔 결과만 넘긴다.
      const placedIds = new Set(positions.map((p) => p.boothId));
      const additions = [];
      let skippedElsewhereCount = 0;
      for (const s of suggestions) {
        if (!s.matched || placedIds.has(s.boothId)) continue;
        if (elsewherePlacementByBoothId.has(s.boothId)) {
          skippedElsewhereCount += 1;
          continue;
        }
        additions.push({
          boothId: s.boothId,
          boothCode: s.boothCode,
          xRatio: Number(s.xRatio),
          yRatio: Number(s.yRatio),
        });
      }
      setPositions((prev) => [...prev, ...additions]);

      const addedCount = additions.length;
      const unmatchedLabels = suggestions.filter((s) => !s.matched).map((s) => s.label);
      const notes = [];
      if (unmatchedLabels.length > 0) {
        notes.push(`부스 코드와 매칭되지 않은 라벨 ${unmatchedLabels.length}개: ${unmatchedLabels.join(", ")}`);
      }
      if (skippedElsewhereCount > 0) {
        notes.push(`다른 층에 이미 배치되어 제외한 부스 ${skippedElsewhereCount}개`);
      }
      setMessage(
        notes.length > 0
          ? `${addedCount}개 제안을 적용했습니다. ${notes.join(" / ")}`
          : `${addedCount}개 제안을 적용했습니다. 저장 전에 위치를 확인해 주세요.`
      );
    } catch (err) {
      if (eventGenerationRef.current !== actionGeneration
          || mapSelectionGenerationRef.current !== actionMapGeneration) {
        return;
      }
      setError(err instanceof ApiError ? `${err.code}: ${err.message}` : "자동 배치 제안을 가져오지 못했습니다.");
    } finally {
      if (eventGenerationRef.current === actionGeneration
          && mapSelectionGenerationRef.current === actionMapGeneration) {
        setSubmitting(false);
      }
    }
  };

  const placeBooth = (booth) => {
    setPositions((prev) => [...prev, { boothId: booth.id, boothCode: booth.boothCode, xRatio: 0.5, yRatio: 0.5 }]);
  };

  const removePosition = (boothId) => {
    setPositions((prev) => prev.filter((p) => p.boothId !== boothId));
  };

  const updateRatioFromPoint = (clientX, clientY) => {
    const el = imageRef.current;
    if (!el) return null;
    const rect = el.getBoundingClientRect();
    return {
      x: Math.min(1, Math.max(0, (clientX - rect.left) / rect.width)),
      y: Math.min(1, Math.max(0, (clientY - rect.top) / rect.height)),
    };
  };

  // 렌더마다 함수 identity가 바뀌면 addEventListener/removeEventListener 짝이 안 맞으므로 useCallback으로 고정한다.
  const handleWindowMouseMove = useCallback((e) => {
    const boothId = draggingBoothIdRef.current;
    if (boothId == null) return;
    const ratio = updateRatioFromPoint(e.clientX, e.clientY);
    if (!ratio) return;
    setPositions((prev) =>
      prev.map((p) => (p.boothId === boothId ? { ...p, xRatio: ratio.x, yRatio: ratio.y } : p))
    );
  }, []);

  const handleWindowMouseUp = useCallback(() => {
    draggingBoothIdRef.current = null;
    window.removeEventListener("mousemove", handleWindowMouseMove);
    window.removeEventListener("mouseup", handleWindowMouseUp);
    // eslint-disable-next-line react-hooks/exhaustive-deps
  }, []);

  const handlePinMouseDown = (boothId) => (e) => {
    e.preventDefault();
    draggingBoothIdRef.current = boothId;
    window.addEventListener("mousemove", handleWindowMouseMove);
    window.addEventListener("mouseup", handleWindowMouseUp);
  };

  // 드래그 중 컴포넌트가 언마운트(행사 전환 등)되면 window 리스너가 남아
  // 사라진 컴포넌트의 setPositions를 계속 호출하는 것을 막는다.
  useEffect(() => {
    return () => {
      window.removeEventListener("mousemove", handleWindowMouseMove);
      window.removeEventListener("mouseup", handleWindowMouseUp);
    };
  }, [handleWindowMouseMove, handleWindowMouseUp]);

  // 같은 용도(모집 공고용/관람객용)의 다른 층 평면도에 이미 배치된 부스는 중복 배치를 막는다.
  // 서로 다른 용도끼리는 별개 배치판이라 중복을 허용한다(예: 모집 공고용과 관람객용에 각각 1번씩).
  const elsewherePlacementByBoothId = new Map();
  if (selectedMap) {
    maps.forEach((m) => {
      if (m.mapType === selectedMap.mapType && m.id !== selectedMap.id) {
        (m.positions ?? []).forEach((p) => {
          if (!elsewherePlacementByBoothId.has(p.boothId)) {
            elsewherePlacementByBoothId.set(p.boothId, m.floorName);
          }
        });
      }
    });
  }

  const unplacedBooths = booths.filter(
    (b) => !positions.some((p) => p.boothId === b.id) && !elsewherePlacementByBoothId.has(b.id)
  );
  const placedElsewhereBooths = booths.filter(
    (b) => !positions.some((p) => p.boothId === b.id) && elsewherePlacementByBoothId.has(b.id)
  );

  return (
    <section className="space-y-lg">
      <div className="flex justify-between items-center flex-wrap gap-sm">
        <h1 className="font-display-lg text-[26px]">평면도 · 좌표 관리</h1>
      </div>

      {!eventId && (
        <div className="bg-white border border-hairline rounded-xl p-lg text-caption text-ink-muted">
          상단에서 관리할 행사를 먼저 선택해 주세요.
        </div>
      )}

      {loading && <p className="text-caption text-ink-muted">불러오는 중...</p>}
      {error && <p className="text-caption text-error">{error}</p>}
      {message && <p className="text-caption text-status-available">{message}</p>}

      {eventId && (
        <>
          <div className="bg-white border border-hairline rounded-xl p-lg space-y-md">
            <div className="flex items-center justify-between gap-md">
              <div>
                <p className="font-body-strong">평면도 업로드</p>
                <p className="text-caption text-ink-muted mt-1">
                  층별로 평면도 이미지를 등록하면, 이후 부스 위치를 배치할 수 있어요.
                </p>
              </div>
              {!showCanvasEditor && (
                <button
                  onClick={() => setShowCanvasEditor(true)}
                  className="shrink-0 text-caption border border-hairline rounded-full px-md py-1"
                >
                  <Icon name="draw" className="text-[13px] mr-1" />
                  직접 그리기
                </button>
              )}
            </div>

            {showCanvasEditor ? (
              <FloorplanCanvasEditor
                onComplete={handleCanvasEditorComplete}
                onCancel={() => {
                  // 취소 시 파일 입력창은 빈 채로 다시 보이는데 uploadForm.file을 그대로 두면
                  // 화면엔 아무것도 선택 안 된 것처럼 보이면서 실제로는 이전 파일이 업로드된다.
                  setShowCanvasEditor(false);
                  setFileIsDrawn(false);
                  setUploadForm((prev) => ({ ...prev, file: null }));
                  setFileInputKey((prev) => prev + 1);
                }}
              />
            ) : (
              <div className="space-y-md">
                <div className="grid grid-cols-1 md:grid-cols-2 gap-md">
                  <label className="block">
                    <span className="text-caption font-body-strong text-ink-muted">층 이름</span>
                    <input
                      placeholder="예: 1층, 지하 1층"
                      value={uploadForm.floorName}
                      onChange={(e) => setUploadForm({ ...uploadForm, floorName: e.target.value })}
                      className="mt-1 w-full border border-hairline rounded-lg px-md py-sm"
                    />
                  </label>
                  <label className="block">
                    <span className="text-caption font-body-strong text-ink-muted">게시 대상</span>
                    <select
                      value={uploadForm.mapType}
                      onChange={(e) => setUploadForm({ ...uploadForm, mapType: e.target.value })}
                      className="mt-1 w-full border border-hairline rounded-lg px-md py-sm bg-white"
                    >
                      {MAP_TYPE_OPTIONS.map((type) => (
                        <option key={type} value={type}>
                          {MAP_TYPE_LABEL[type]}
                        </option>
                      ))}
                    </select>
                  </label>
                </div>

                <div>
                  <span className="text-caption font-body-strong text-ink-muted">평면도 이미지</span>
                  <button
                    type="button"
                    onClick={() => fileInputRef.current?.click()}
                    className="mt-1 w-full flex items-center gap-md border border-dashed border-hairline rounded-lg p-md text-left hover:border-primary hover:bg-surface-container-lowest transition-colors"
                  >
                    {filePreviewUrl ? (
                      <img
                        src={filePreviewUrl}
                        alt=""
                        className="w-14 h-14 object-cover rounded-md border border-hairline shrink-0"
                      />
                    ) : (
                      <span className="w-14 h-14 shrink-0 flex items-center justify-center rounded-md bg-surface-container-lowest text-ink-muted">
                        <Icon name="image" className="text-[22px]" />
                      </span>
                    )}
                    <span className="min-w-0">
                      {uploadForm.file ? (
                        <>
                          <span className="block text-caption font-body-strong truncate">
                            {uploadForm.file.name}
                          </span>
                          <span className="block text-[11px] text-ink-muted">
                            {fileIsDrawn
                              ? "직접 그린 평면도예요 · 클릭해서 다른 이미지로 바꿀 수 있어요"
                              : "클릭해서 다른 이미지로 바꿀 수 있어요"}
                          </span>
                        </>
                      ) : (
                        <>
                          <span className="block text-caption font-body-strong">이미지를 선택하세요</span>
                          <span className="block text-[11px] text-ink-muted">
                            PNG, JPG 등 이미지 파일 · 클릭해서 선택
                          </span>
                        </>
                      )}
                    </span>
                  </button>
                  <input
                    ref={fileInputRef}
                    key={fileInputKey}
                    type="file"
                    accept="image/*"
                    onChange={handleFileChange}
                    className="hidden"
                  />
                </div>

                <button
                  onClick={handleUpload}
                  disabled={submitting}
                  className="w-full md:w-auto px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong disabled:opacity-40"
                >
                  {submitting ? "업로드 중..." : "업로드"}
                </button>
              </div>
            )}
          </div>

          {maps.length === 0 ? (
            <div className="bg-surface-pearl border-2 border-dashed border-hairline rounded-xl p-xxl text-center text-ink-muted">
              <Icon name="map" className="text-[32px] block mb-sm" />
              평면도 이미지가 아직 없습니다. 업로드하면 부스를 드래그해 좌표를 지정할 수 있어요.
            </div>
          ) : (
            MAP_TYPE_OPTIONS.map((type) => {
              const mapsOfType = maps.filter((m) => m.mapType === type);
              if (mapsOfType.length === 0) return null;
              return (
                <div key={type} className="space-y-sm">
                  <p className="text-caption font-body-strong text-ink-muted">{MAP_TYPE_LABEL[type]}</p>
                  <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
                    {mapsOfType.map((map) => (
                <div key={map.id} className="p-lg space-y-sm">
                  <div className="flex items-center gap-sm flex-wrap">
                    <button
                      onClick={() => selectMap(map)}
                      className="font-body-strong text-left hover:text-primary"
                    >
                      {map.floorName} · v{map.version}
                    </button>
                    <span
                      className={`text-[11px] font-bold px-sm py-1 rounded-full ${
                        map.status === "PUBLISHED"
                          ? "bg-status-available/10 text-status-available"
                          : "bg-status-pending/10 text-status-pending"
                      }`}
                    >
                      {map.status === "PUBLISHED" ? "게시됨" : "임시저장"}
                    </span>
                    <div className="ml-auto flex gap-sm">
                      {map.status === "DRAFT" && (
                        <>
                          <button
                            onClick={() => handlePublish(map.id)}
                            disabled={submitting}
                            className="text-caption border border-hairline rounded-full px-md py-1"
                          >
                            게시
                          </button>
                          <button
                            onClick={() => handleDelete(map.id)}
                            disabled={submitting}
                            className="text-caption border border-error text-error rounded-full px-md py-1"
                          >
                            삭제
                          </button>
                        </>
                      )}
                    </div>
                  </div>

                  {selectedMapId === map.id && (
                    <div className="border border-hairline rounded-lg p-md space-y-md bg-surface-container-lowest">
                      <div className="flex gap-md flex-wrap">
                        <div className="flex-grow min-w-[280px]">
                          <div
                            className="relative inline-block border border-hairline rounded-lg overflow-hidden select-none"
                            style={{ maxWidth: "100%" }}
                          >
                            <img
                              ref={imageRef}
                              src={fileDownloadUrl(map.imageFileId)}
                              alt={`${map.floorName} 평면도`}
                              className="block max-w-full"
                              draggable={false}
                            />
                            {positions.map((p) => (
                              <div
                                key={p.boothId}
                                onMouseDown={handlePinMouseDown(p.boothId)}
                                title={p.boothCode}
                                className={`absolute -translate-x-1/2 -translate-y-full min-w-[18px] h-[18px] px-1 flex items-center justify-center bg-primary text-white font-bold leading-none rounded-full border border-white shadow-md cursor-grab active:cursor-grabbing whitespace-nowrap ${pinFontSizeClass(p.boothCode)}`}
                                style={{ left: `${p.xRatio * 100}%`, top: `${p.yRatio * 100}%` }}
                              >
                                {p.boothCode || "?"}
                              </div>
                            ))}
                          </div>
                          <p className="text-[11px] text-ink-muted mt-xs">
                            핀을 드래그해서 위치를 옮긴 뒤 "좌표 저장"을 눌러주세요.
                          </p>
                          <button
                            onClick={handleAutoLayout}
                            disabled={submitting}
                            className="mt-sm text-caption border border-primary text-primary rounded-full px-md py-1 disabled:opacity-40"
                          >
                            <Icon name="auto_awesome" className="text-[13px] mr-1" />
                            AI로 부스 위치 제안받기
                          </button>
                          <p className="text-[11px] text-ink-muted mt-xs">
                            AI가 도면을 읽어 위치를 제안합니다. 라벨을 잘못 읽을 수 있으니 반드시 확인 후 저장하세요.
                          </p>
                        </div>

                        <div className="w-full md:w-[220px] space-y-sm">
                          <p className="text-caption font-body-strong">배치된 부스</p>
                          {positions.length === 0 && (
                            <p className="text-[11px] text-ink-muted">아직 배치된 부스가 없습니다.</p>
                          )}
                          <div className="flex flex-wrap gap-xs">
                            {positions.map((p) => (
                              <span
                                key={p.boothId}
                                className="text-[11px] bg-white border border-hairline rounded-full px-sm py-1 flex items-center gap-1"
                              >
                                {p.boothCode}
                                <button onClick={() => removePosition(p.boothId)} className="text-error">
                                  <Icon name="close" className="text-[12px]" />
                                </button>
                              </span>
                            ))}
                          </div>

                          <p className="text-caption font-body-strong mt-md">미배치 부스</p>
                          {unplacedBooths.length === 0 ? (
                            <p className="text-[11px] text-ink-muted">배치할 수 있는 부스가 없습니다.</p>
                          ) : (
                            <div className="flex flex-wrap gap-xs">
                              {unplacedBooths.map((b) => (
                                <button
                                  key={b.id}
                                  onClick={() => placeBooth(b)}
                                  className="text-[11px] border border-hairline rounded-full px-sm py-1 hover:bg-surface-container"
                                >
                                  + {b.boothCode}
                                </button>
                              ))}
                            </div>
                          )}

                          {placedElsewhereBooths.length > 0 && (
                            <>
                              <p className="text-caption font-body-strong mt-md">
                                다른 층에 이미 배치됨 ({MAP_TYPE_LABEL[selectedMap.mapType]})
                              </p>
                              <div className="flex flex-wrap gap-xs">
                                {placedElsewhereBooths.map((b) => (
                                  <span
                                    key={b.id}
                                    title={`${elsewherePlacementByBoothId.get(b.id)}에 배치됨`}
                                    className="text-[11px] border border-dashed border-hairline text-ink-muted rounded-full px-sm py-1 cursor-not-allowed"
                                  >
                                    {b.boothCode} · {elsewherePlacementByBoothId.get(b.id)}
                                  </span>
                                ))}
                              </div>
                            </>
                          )}

                          <button
                            onClick={handleSavePositions}
                            disabled={submitting}
                            className="w-full mt-md px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong disabled:opacity-40"
                          >
                            좌표 저장
                          </button>
                        </div>
                      </div>
                    </div>
                  )}
                </div>
                    ))}
                  </div>
                </div>
              );
            })
          )}
        </>
      )}
    </section>
  );
}
