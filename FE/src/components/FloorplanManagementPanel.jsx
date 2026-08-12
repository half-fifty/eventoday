import { useCallback, useEffect, useRef, useState } from "react";
import Icon from "./Icon.jsx";
import { ApiError } from "../api/apiClient.js";
import { uploadFile, fileDownloadUrl } from "../api/fileApi.js";
import {
  listVenueMaps,
  createVenueMap,
  publishVenueMap,
  deleteVenueMap,
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
  const [selectedMapId, setSelectedMapId] = useState(null);
  const [positions, setPositions] = useState([]);

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
    setUploadForm(EMPTY_UPLOAD_FORM);
    setFileInputKey((prev) => prev + 1);
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
    setUploadForm((prev) => ({ ...prev, file }));
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
          <div className="bg-white border border-hairline rounded-xl p-lg space-y-sm">
            <p className="font-body-strong">평면도 업로드</p>
            <div className="grid grid-cols-1 md:grid-cols-4 gap-sm items-center">
              <input
                placeholder="층 이름 (예: 1층)"
                value={uploadForm.floorName}
                onChange={(e) => setUploadForm({ ...uploadForm, floorName: e.target.value })}
                className="border border-hairline rounded-lg px-md py-sm"
              />
              <select
                value={uploadForm.mapType}
                onChange={(e) => setUploadForm({ ...uploadForm, mapType: e.target.value })}
                className="border border-hairline rounded-lg px-md py-sm bg-white"
              >
                {MAP_TYPE_OPTIONS.map((type) => (
                  <option key={type} value={type}>
                    {MAP_TYPE_LABEL[type]}
                  </option>
                ))}
              </select>
              <input
                key={fileInputKey}
                type="file"
                accept="image/*"
                onChange={handleFileChange}
                className="text-caption md:col-span-1"
              />
              <button
                onClick={handleUpload}
                disabled={submitting}
                className="px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong disabled:opacity-40"
              >
                업로드
              </button>
            </div>
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
