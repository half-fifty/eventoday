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

const MAP_TYPE_LABEL = { RECRUITMENT: "모집 공고용", VISITOR: "관람객용" };
const MAP_TYPE_OPTIONS = Object.keys(MAP_TYPE_LABEL);
const EMPTY_UPLOAD_FORM = { floorName: "", mapType: "VISITOR", file: null };
const BOOTH_PAGE_SIZE = 100; // BoothService.MAX_PAGE_SIZE

// 부스가 100개를 넘는 행사도 팔레트에 전부 뜨도록, 마지막 페이지까지 순차 조회해 합친다.
const listAllBooths = async (eventId) => {
  let page = 0;
  let all = [];
  for (;;) {
    const result = await listBooths(eventId, { page, size: BOOTH_PAGE_SIZE });
    all = all.concat(result.content ?? []);
    if (!result || result.last) break;
    page += 1;
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
  const [selectedMapId, setSelectedMapId] = useState(null);
  const [positions, setPositions] = useState([]);

  const imageRef = useRef(null);
  const draggingBoothIdRef = useRef(null);

  const loadAll = async (id) => {
    setLoading(true);
    setError("");
    try {
      const [mapList, allBooths] = await Promise.all([
        listVenueMaps(id),
        listAllBooths(id),
      ]);
      setMaps(mapList);
      setBooths(allBooths);
    } catch (err) {
      setError(err instanceof ApiError ? `${err.code}: ${err.message}` : "평면도 정보를 불러오지 못했습니다.");
    } finally {
      setLoading(false);
    }
  };

  useEffect(() => {
    setUploadForm(EMPTY_UPLOAD_FORM);
    setSelectedMapId(null);
    setPositions([]);
    setMessage("");
    setError("");
    setMaps([]);
    setBooths([]);
    if (eventId) {
      loadAll(eventId);
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

  const runAction = async (actionFn, successMessage) => {
    if (submitting) return;
    setSubmitting(true);
    setError("");
    setMessage("");
    try {
      await actionFn();
      setMessage(successMessage);
      await loadAll(eventId);
    } catch (err) {
      setError(err instanceof ApiError ? `${err.code}: ${err.message}` : err.message || "요청에 실패했습니다.");
    } finally {
      setSubmitting(false);
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
      setUploadForm(EMPTY_UPLOAD_FORM);
    }, "평면도를 업로드했습니다.");

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

  const unplacedBooths = booths.filter((b) => !positions.some((p) => p.boothId === b.id));

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
              <input type="file" accept="image/*" onChange={handleFileChange} className="text-caption md:col-span-1" />
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
            <div className="bg-white border border-hairline rounded-xl divide-y divide-divider-soft">
              {maps.map((map) => (
                <div key={map.id} className="p-lg space-y-sm">
                  <div className="flex items-center gap-sm flex-wrap">
                    <button
                      onClick={() => selectMap(map)}
                      className="font-body-strong text-left hover:text-primary"
                    >
                      {map.floorName} · {MAP_TYPE_LABEL[map.mapType]} · v{map.version}
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
                                className="absolute w-6 h-6 -ml-3 -mt-6 flex items-center justify-center bg-primary text-white text-[10px] font-bold rounded-full border-2 border-white shadow-md cursor-grab active:cursor-grabbing"
                                style={{ left: `${p.xRatio * 100}%`, top: `${p.yRatio * 100}%` }}
                              >
                                {p.boothCode?.slice(-2) ?? "?"}
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
                            <p className="text-[11px] text-ink-muted">모든 부스가 배치되었습니다.</p>
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
          )}
        </>
      )}
    </section>
  );
}
