import { useCallback, useEffect, useRef, useState } from "react";
import Icon from "./Icon.jsx";

// 평면도 이미지가 없는 주최자를 위한 최소 그리기 도구. 흰 캔버스에 사각형(부스 칸)을
// 추가/이동/크기조절하고 라벨을 적은 뒤, 이 결과를 실제 이미지 파일로 렌더링해서 반환한다.
// 반환된 File은 기존 업로드 흐름(uploadFile → createVenueMap)에 그대로 태워지므로,
// 이후의 비전 자동배치 파이프라인도 손댈 필요 없이 재사용된다.
const CANVAS_WIDTH = 1000;
const CANVAS_HEIGHT = 700;
const DEFAULT_BOX_WIDTH = 90;
const DEFAULT_BOX_HEIGHT = 70;
const MIN_BOX_SIZE = 20;
const RESIZE_HANDLE_SIZE = 14;
const PASTE_OFFSET = 24;
// 편집 중에만 보이는 정렬용 격자 칸 크기 - 완성된 이미지는 handleComplete에서 별도
// 캔버스에 다시 그리므로 이 격자는 결과물에 남지 않는다.
const GRID_SIZE = 10;

let nextBoxId = 1;

const clamp = (value, min, max) => Math.min(max, Math.max(min, value));
const snapToGrid = (value) => Math.round(value / GRID_SIZE) * GRID_SIZE;

// "A01" -> "A02"처럼 라벨 끝의 숫자를 1 증가시킨다 - 여러 부스를 붙여넣을 때 매번 라벨을
// 다시 입력하지 않도록. 끝에 숫자가 없으면 그냥 구분용 접미사를 붙인다.
const incrementLabel = (label) => {
  const match = label.match(/^(.*?)(\d+)$/);
  if (match) {
    const [, prefix, numStr] = match;
    const nextNum = String(Number(numStr) + 1).padStart(numStr.length, "0");
    return prefix + nextNum;
  }
  return label ? `${label}-2` : "";
};

export default function FloorplanCanvasEditor({ onComplete, onCancel }) {
  const [boxes, setBoxes] = useState([]);
  // 여러 칸을 한꺼번에 선택해서 같이 옮길 수 있도록 단일 id 대신 Set으로 관리한다.
  const [selectedIds, setSelectedIds] = useState(() => new Set());
  const [clipboardBox, setClipboardBox] = useState(null);
  const [bulkForm, setBulkForm] = useState({
    rows: 2,
    cols: 4,
    width: DEFAULT_BOX_WIDTH,
    height: DEFAULT_BOX_HEIGHT,
    gap: 16,
    prefix: "A",
    start: 1,
  });

  const surfaceRef = useRef(null);
  // 드래그 중인 박스와 동작(이동/크기조절)을 함께 들고 있어야, mousemove 핸들러가
  // 매번 boxes 배열을 다시 뒤지지 않고도 어떤 박스를 어떻게 바꿀지 알 수 있다.
  const dragStateRef = useRef(null);
  // 방금 클릭으로 새로 만든 박스의 id - 렌더 후 해당 라벨 input에 자동으로 포커스를 주기 위함.
  // 안 그러면 박스를 만들어도 아무 입력창에 포커스가 없어서 바로 타이핑해도 라벨에 안 들어간다.
  const pendingFocusIdRef = useRef(null);
  // 새 칸을 만드는 중(빈 캔버스를 눌러 드래그하는 동안) 미리보기로 보여줄 사각형.
  const [draftBox, setDraftBox] = useState(null);

  // 여러 칸이 선택된 동안엔 라벨/크기 편집 패널을 보여줄 대상이 하나로 정해지지 않으니,
  // 정확히 한 칸만 선택됐을 때만 해당 박스를 돌려준다.
  const selectedBox =
    selectedIds.size === 1 ? boxes.find((box) => box.id === [...selectedIds][0]) || null : null;

  const getSurfacePoint = (clientX, clientY) => {
    const rect = surfaceRef.current.getBoundingClientRect();
    return {
      x: clamp(((clientX - rect.left) / rect.width) * CANVAS_WIDTH, 0, CANVAS_WIDTH),
      y: clamp(((clientY - rect.top) / rect.height) * CANVAS_HEIGHT, 0, CANVAS_HEIGHT),
    };
  };

  // 빈 캔버스에서 mousedown - 클릭 한 번으로 고정 크기 칸이 생기는 대신, 드래그한 만큼의
  // 크기로 칸을 새로 만든다. 박스 위에서 시작된 mousedown은 handleBoxMouseDown이
  // stopPropagation으로 막으므로 여기까지 오지 않는다.
  const handleSurfaceMouseDown = (e) => {
    if (e.target !== surfaceRef.current) return;
    e.preventDefault();
    // 빈 곳을 누르면 일단 선택 해제 - 실제 드래그 거리가 충분하면 mouseup에서 칸을 만든다.
    setSelectedIds(new Set());
    const point = getSurfacePoint(e.clientX, e.clientY);
    const draft = { x: point.x, y: point.y, width: 0, height: 0 };
    dragStateRef.current = { mode: "create", startPoint: point, draft };
    setDraftBox(draft);
    window.addEventListener("mousemove", handleWindowMouseMove);
    window.addEventListener("mouseup", handleWindowMouseUp);
  };

  const updateBox = (id, patch) => {
    setBoxes((prev) => prev.map((box) => (box.id === id ? { ...box, ...patch } : box)));
  };

  // 가로/세로를 숫자로 직접 입력했을 때 - 캔버스를 벗어나지 않도록 현재 위치 기준
  // 남은 공간으로 clamp한다.
  const updateBoxSize = (id, field, rawValue) => {
    const value = Math.max(0, Number(rawValue) || 0);
    setBoxes((prev) =>
      prev.map((box) => {
        if (box.id !== id) return box;
        if (field === "width") {
          return { ...box, width: clamp(value, 0, CANVAS_WIDTH - box.x) };
        }
        return { ...box, height: clamp(value, 0, CANVAS_HEIGHT - box.y) };
      })
    );
  };

  // 입력 도중에는 그대로 두고(자유롭게 타이핑), 입력을 마치고 포커스를 벗어날 때만
  // 10px 단위로 맞춘다 - 매 키 입력마다 스냅하면 값이 튀어서 자유롭게 타이핑할 수 없다.
  const snapBoxSizeOnBlur = (id, field) => {
    setBoxes((prev) =>
      prev.map((box) => {
        if (box.id !== id) return box;
        if (field === "width") {
          return { ...box, width: clamp(snapToGrid(box.width), MIN_BOX_SIZE, CANVAS_WIDTH - box.x) };
        }
        return { ...box, height: clamp(snapToGrid(box.height), MIN_BOX_SIZE, CANVAS_HEIGHT - box.y) };
      })
    );
  };

  const removeBox = (id) => {
    setBoxes((prev) => prev.filter((box) => box.id !== id));
    setSelectedIds((prev) => {
      if (!prev.has(id)) return prev;
      const next = new Set(prev);
      next.delete(id);
      return next;
    });
  };

  const removeSelectedBoxes = () => {
    setBoxes((prev) => prev.filter((box) => !selectedIds.has(box.id)));
    setSelectedIds(new Set());
  };

  const copySelectedBox = () => {
    if (!selectedBox) return;
    setClipboardBox({ ...selectedBox });
  };

  const pasteBox = () => {
    if (!clipboardBox) return;
    const id = nextBoxId++;
    const x = clamp(clipboardBox.x + PASTE_OFFSET, 0, CANVAS_WIDTH - clipboardBox.width);
    const y = clamp(clipboardBox.y + PASTE_OFFSET, 0, CANVAS_HEIGHT - clipboardBox.height);
    const pasted = {
      id,
      x,
      y,
      width: clipboardBox.width,
      height: clipboardBox.height,
      label: incrementLabel(clipboardBox.label),
    };
    setBoxes((prev) => [...prev, pasted]);
    setSelectedIds(new Set([id]));
    // 연속 붙여넣기가 대각선으로 계속 퍼지도록, 클립보드 자체를 방금 붙인 위치/라벨로 갱신한다.
    setClipboardBox(pasted);
  };

  // 격자 형태로 여러 부스 칸을 한 번에 만든다. 라벨은 접두사+번호로 순서대로 채운다.
  const addBulkGrid = () => {
    const rows = Math.max(1, Math.round(Number(bulkForm.rows) || 1));
    const cols = Math.max(1, Math.round(Number(bulkForm.cols) || 1));
    const width = snapToGrid(Math.max(MIN_BOX_SIZE, Number(bulkForm.width) || DEFAULT_BOX_WIDTH));
    const height = snapToGrid(Math.max(MIN_BOX_SIZE, Number(bulkForm.height) || DEFAULT_BOX_HEIGHT));
    const gap = snapToGrid(Math.max(0, Number(bulkForm.gap) || 0));
    const start = Math.max(0, Math.round(Number(bulkForm.start) || 0));
    const labelPadding = String(start + rows * cols - 1).length;
    // 격자를 항상 (0,0)부터 그리면 이미 개별로 만들어둔 칸을 그대로 덮어버려서
    // 그 칸이 클릭도 안 되고(맨 위에 새 칸이 깔림) 선택/삭제도 안 되는 것처럼 보인다.
    // 기존 칸들 중 가장 아래쪽 끝 아래로 새 격자를 배치해 겹치지 않게 한다.
    const originY =
      boxes.length > 0
        ? clamp(Math.max(...boxes.map((b) => b.y + b.height)) + gap, 0, Math.max(0, CANVAS_HEIGHT - height))
        : 0;

    const created = [];
    let counter = start;
    for (let row = 0; row < rows; row += 1) {
      for (let col = 0; col < cols; col += 1) {
        const x = col * (width + gap);
        const y = originY + row * (height + gap);
        if (x + width > CANVAS_WIDTH || y + height > CANVAS_HEIGHT) continue;
        created.push({
          id: nextBoxId++,
          x,
          y,
          width,
          height,
          label: `${bulkForm.prefix}${String(counter).padStart(labelPadding, "0")}`,
        });
        counter += 1;
      }
    }
    setBoxes((prev) => [...prev, ...created]);
  };

  // Shift+클릭이면 선택에 추가/제거하고, 이미 여러 칸이 선택된 상태에서 그중 하나를
  // 그냥 클릭했으면 선택을 유지한다(다 같이 드래그하려는 의도일 수 있으니) - 그 외엔
  // 해당 칸만 선택한다. 박스 컨테이너의 mousedown과 라벨 input의 click 양쪽에서 같은
  // 규칙을 써야 한다 - input이 박스 대부분을 덮고 있어서, input 쪽만 규칙이 다르면
  // 라벨을 클릭할 때마다 다중 선택이 조용히 1개로 풀려버린다.
  const computeNextSelection = (box, e) => {
    if (e.shiftKey) {
      const next = new Set(selectedIds);
      if (next.has(box.id)) {
        next.delete(box.id);
      } else {
        next.add(box.id);
      }
      return next;
    }
    if (selectedIds.has(box.id) && selectedIds.size > 1) {
      return selectedIds;
    }
    return new Set([box.id]);
  };

  const handleBoxMouseDown = (box) => (e) => {
    e.preventDefault();
    e.stopPropagation();

    const nextSelection = computeNextSelection(box, e);
    setSelectedIds(nextSelection);

    // 선택된 여러 칸을 함께 드래그로 옮길 수 있도록, 드래그 시작 시점의 위치를 그룹 전체에 대해 기록한다.
    const groupIds = nextSelection.has(box.id) ? nextSelection : new Set([box.id]);
    const groupBoxes = boxes.filter((b) => groupIds.has(b.id));
    const startPositions = new Map(groupBoxes.map((b) => [b.id, { x: b.x, y: b.y }]));
    // 그룹이 대형으로 움직이도록, dx/dy를 박스마다 따로 clamp하지 않고 그룹 전체에 대해
    // 한 번만 clamp한다 - 그렇지 않으면 크기가 다른 박스들이 가장자리에서 서로 다른
    // 시점에 멈춰서 그룹이 흩어져 보인다.
    const dxMin = Math.max(...groupBoxes.map((b) => -b.x));
    const dxMax = Math.min(...groupBoxes.map((b) => CANVAS_WIDTH - b.width - b.x));
    const dyMin = Math.max(...groupBoxes.map((b) => -b.y));
    const dyMax = Math.min(...groupBoxes.map((b) => CANVAS_HEIGHT - b.height - b.y));

    dragStateRef.current = {
      mode: "move",
      startClientX: e.clientX,
      startClientY: e.clientY,
      startPositions,
      dxMin,
      dxMax,
      dyMin,
      dyMax,
    };
    window.addEventListener("mousemove", handleWindowMouseMove);
    window.addEventListener("mouseup", handleWindowMouseUp);
  };

  const handleResizeMouseDown = (box) => (e) => {
    e.preventDefault();
    e.stopPropagation();
    // 크기 조절은 한 칸 단위로만 의미가 있으므로, 여러 칸이 선택돼 있었더라도 이 칸만 선택한다.
    setSelectedIds(new Set([box.id]));
    dragStateRef.current = {
      mode: "resize",
      boxId: box.id,
      startClientX: e.clientX,
      startClientY: e.clientY,
      startWidth: box.width,
      startHeight: box.height,
    };
    window.addEventListener("mousemove", handleWindowMouseMove);
    window.addEventListener("mouseup", handleWindowMouseUp);
  };

  // 렌더마다 함수 identity가 바뀌면 addEventListener/removeEventListener 짝이 안 맞으므로(특히
  // 언마운트 cleanup에서 최초 렌더의 낡은 참조를 지우려다 실패할 수 있음) useCallback으로 고정한다.
  // ref/setState/모듈 상수만 참조하므로 의존성 없이 고정 가능하다.
  const handleWindowMouseMove = useCallback((e) => {
    const drag = dragStateRef.current;
    if (!drag || !surfaceRef.current) return;
    const rect = surfaceRef.current.getBoundingClientRect();
    const scaleX = CANVAS_WIDTH / rect.width;
    const scaleY = CANVAS_HEIGHT / rect.height;
    const dx = (e.clientX - drag.startClientX) * scaleX;
    const dy = (e.clientY - drag.startClientY) * scaleY;

    if (drag.mode === "move") {
      // 그룹 전체에 대해 한 번만 clamp/snap한 delta를 모든 박스에 동일하게 적용해야
      // 크기가 다른 박스들끼리도 상대 위치가 흐트러지지 않고 한 덩어리로 움직인다.
      const groupDx = clamp(snapToGrid(dx), drag.dxMin, drag.dxMax);
      const groupDy = clamp(snapToGrid(dy), drag.dyMin, drag.dyMax);
      setBoxes((prev) =>
        prev.map((box) => {
          const start = drag.startPositions.get(box.id);
          if (!start) return box;
          return {
            ...box,
            x: start.x + groupDx,
            y: start.y + groupDy,
          };
        })
      );
    } else if (drag.mode === "resize") {
      setBoxes((prev) =>
        prev.map((box) =>
          box.id === drag.boxId
            ? {
                ...box,
                width: clamp(snapToGrid(drag.startWidth + dx), MIN_BOX_SIZE, CANVAS_WIDTH - box.x),
                height: clamp(snapToGrid(drag.startHeight + dy), MIN_BOX_SIZE, CANVAS_HEIGHT - box.y),
              }
            : box
        )
      );
    } else if (drag.mode === "create") {
      const current = getSurfacePoint(e.clientX, e.clientY);
      const draft = {
        x: Math.min(drag.startPoint.x, current.x),
        y: Math.min(drag.startPoint.y, current.y),
        width: Math.abs(current.x - drag.startPoint.x),
        height: Math.abs(current.y - drag.startPoint.y),
      };
      // mouseup에서 최신 값을 읽어야 하니, 리렌더용 state와 별개로 ref에도 그대로 담아둔다.
      drag.draft = draft;
      setDraftBox(draft);
    }
  }, []);

  const handleWindowMouseUp = useCallback(() => {
    const drag = dragStateRef.current;
    if (drag?.mode === "create" && drag.draft) {
      const { draft } = drag;
      // 드래그 거리가 너무 짧으면(=사실상 그냥 클릭) 칸을 만들지 않고 선택 해제로만 처리한다.
      // 이게 없으면 다중 선택을 취소하려고 빈 곳을 클릭할 때마다 작은 칸이 생겨버린다.
      if (draft.width >= GRID_SIZE && draft.height >= GRID_SIZE) {
        const width = clamp(snapToGrid(draft.width), MIN_BOX_SIZE, CANVAS_WIDTH);
        const height = clamp(snapToGrid(draft.height), MIN_BOX_SIZE, CANVAS_HEIGHT);
        const x = clamp(snapToGrid(draft.x), 0, CANVAS_WIDTH - width);
        const y = clamp(snapToGrid(draft.y), 0, CANVAS_HEIGHT - height);
        const id = nextBoxId++;
        pendingFocusIdRef.current = id;
        setBoxes((prev) => [...prev, { id, x, y, width, height, label: "" }]);
        setSelectedIds(new Set([id]));
      }
      setDraftBox(null);
    }
    dragStateRef.current = null;
    window.removeEventListener("mousemove", handleWindowMouseMove);
    window.removeEventListener("mouseup", handleWindowMouseUp);
  }, [handleWindowMouseMove]);

  // 드래그 중(박스 이동/크기조절/새 칸 만들기) 부모가 이 컴포넌트를 언마운트하면(취소, 완료,
  // 평면도 전환 등) window 리스너가 남아 사라진 컴포넌트의 setBoxes/setDraftBox를 계속
  // 호출하는 것을 막는다. FloorplanManagementPanel의 핀 드래그도 같은 이유로 이 패턴을 쓴다.
  useEffect(() => {
    return () => {
      window.removeEventListener("mousemove", handleWindowMouseMove);
      window.removeEventListener("mouseup", handleWindowMouseUp);
    };
  }, [handleWindowMouseMove, handleWindowMouseUp]);

  // 화면에 그린 박스들을 실제 PNG 이미지로 렌더링한다 - 이렇게 만들어진 File은 일반
  // 업로드 이미지와 구분되지 않으므로, 이후 비전 자동배치 등 기존 파이프라인을 그대로 탄다.
  const handleComplete = () => {
    const canvas = document.createElement("canvas");
    canvas.width = CANVAS_WIDTH;
    canvas.height = CANVAS_HEIGHT;
    const ctx = canvas.getContext("2d");

    ctx.fillStyle = "#ffffff";
    ctx.fillRect(0, 0, CANVAS_WIDTH, CANVAS_HEIGHT);

    ctx.lineWidth = 2;
    ctx.strokeStyle = "#111111";
    ctx.fillStyle = "#111111";
    ctx.font = "bold 16px sans-serif";
    ctx.textAlign = "center";
    ctx.textBaseline = "middle";

    boxes.forEach((box) => {
      ctx.strokeRect(box.x, box.y, box.width, box.height);
      if (box.label.trim()) {
        ctx.fillText(
          box.label.trim(),
          box.x + box.width / 2,
          box.y + box.height / 2,
          box.width - 8
        );
      }
    });

    canvas.toBlob((blob) => {
      if (!blob) return;
      const file = new File([blob], "floorplan-drawing.png", { type: "image/png" });
      onComplete(file, { width: CANVAS_WIDTH, height: CANVAS_HEIGHT });
    }, "image/png");
  };

  return (
    <div className="space-y-sm">
      <div className="flex items-center justify-between">
        <p className="text-caption text-ink-muted">
          빈 화면을 누른 채 드래그하면 그 크기만큼 부스 칸이 새로 생겨요. 칸을 드래그해
          옮기거나 모서리를 끌어 크기를 바꿀 수 있어요. 칸을 클릭해 선택하면 아래에서
          정확한 크기를 입력하거나 복사·붙여넣기 할 수 있어요. Shift를 누른 채 클릭하면
          여러 칸을 한 번에 선택해서 같이 옮기거나 삭제할 수 있어요.
        </p>
        <button
          onClick={onCancel}
          className="shrink-0 text-caption border border-hairline rounded-full px-md py-1"
        >
          취소
        </button>
      </div>

      <div className="bg-surface-container-lowest border border-hairline rounded-lg p-sm flex flex-wrap content-start items-center gap-sm min-h-[140px]">
        <span className="text-[11px] font-body-strong text-ink-muted">선택한 칸</span>
        {selectedBox ? (
          <>
            <label className="text-[11px] text-ink-muted flex items-center gap-1">
              라벨
              <input
                type="text"
                value={selectedBox.label}
                onChange={(e) => updateBox(selectedBox.id, { label: e.target.value })}
                placeholder="부스 번호"
                className="w-[80px] border border-hairline rounded px-1 py-0.5"
              />
            </label>
            <label className="text-[11px] text-ink-muted flex items-center gap-1">
              가로
              <input
                type="number"
                min={MIN_BOX_SIZE}
                step={GRID_SIZE}
                value={Math.round(selectedBox.width)}
                onChange={(e) => updateBoxSize(selectedBox.id, "width", e.target.value)}
                onBlur={() => snapBoxSizeOnBlur(selectedBox.id, "width")}
                className="w-[64px] border border-hairline rounded px-1 py-0.5"
              />
            </label>
            <label className="text-[11px] text-ink-muted flex items-center gap-1">
              세로
              <input
                type="number"
                min={MIN_BOX_SIZE}
                step={GRID_SIZE}
                value={Math.round(selectedBox.height)}
                onChange={(e) => updateBoxSize(selectedBox.id, "height", e.target.value)}
                onBlur={() => snapBoxSizeOnBlur(selectedBox.id, "height")}
                className="w-[64px] border border-hairline rounded px-1 py-0.5"
              />
            </label>
            <span className="text-[10px] text-ink-muted basis-full">
              가로·세로는 격자와 맞도록 10px 단위로 저장돼요. 입력을 마치고 다른 곳을 클릭하면 가까운 10 단위로 반올림돼요.
            </span>
            <button
              onClick={copySelectedBox}
              className="text-[11px] border border-hairline rounded-full px-sm py-1"
            >
              복사
            </button>
            <button
              onClick={() => removeBox(selectedBox.id)}
              className="text-[11px] border border-error text-error rounded-full px-sm py-1"
            >
              삭제
            </button>
          </>
        ) : selectedIds.size > 1 ? (
          <>
            <span className="text-[11px] text-ink-muted">
              {selectedIds.size}칸 선택됨 - 드래그하면 함께 움직여요.
            </span>
            <button
              onClick={removeSelectedBoxes}
              className="text-[11px] border border-error text-error rounded-full px-sm py-1"
            >
              선택한 {selectedIds.size}칸 삭제
            </button>
          </>
        ) : (
          <span className="text-[11px] text-ink-muted">
            캔버스에서 칸을 선택하세요. (Shift+클릭으로 여러 칸 선택)
          </span>
        )}
        <button
          onClick={pasteBox}
          disabled={!clipboardBox}
          className="text-[11px] border border-hairline rounded-full px-sm py-1 disabled:opacity-40 ml-auto"
        >
          붙여넣기{clipboardBox ? ` (${clipboardBox.label || "라벨 없음"})` : ""}
        </button>
      </div>

      <div className="bg-surface-container-lowest border border-hairline rounded-lg p-sm flex flex-wrap items-end gap-sm">
        <span className="text-[11px] font-body-strong text-ink-muted w-full">여러 칸 한 번에 추가</span>
        <label className="text-[11px] text-ink-muted flex flex-col gap-0.5">
          줄 x 칸
          <span className="flex items-center gap-1">
            <input
              type="number"
              min={1}
              value={bulkForm.rows}
              onChange={(e) => setBulkForm((prev) => ({ ...prev, rows: e.target.value }))}
              className="w-[48px] border border-hairline rounded px-1 py-0.5"
            />
            x
            <input
              type="number"
              min={1}
              value={bulkForm.cols}
              onChange={(e) => setBulkForm((prev) => ({ ...prev, cols: e.target.value }))}
              className="w-[48px] border border-hairline rounded px-1 py-0.5"
            />
          </span>
        </label>
        <label className="text-[11px] text-ink-muted flex flex-col gap-0.5">
          칸 크기(가로x세로)
          <span className="flex items-center gap-1">
            <input
              type="number"
              min={MIN_BOX_SIZE}
              step={GRID_SIZE}
              value={bulkForm.width}
              onChange={(e) => setBulkForm((prev) => ({ ...prev, width: e.target.value }))}
              onBlur={() =>
                setBulkForm((prev) => ({ ...prev, width: snapToGrid(Math.max(MIN_BOX_SIZE, Number(prev.width) || DEFAULT_BOX_WIDTH)) }))
              }
              className="w-[56px] border border-hairline rounded px-1 py-0.5"
            />
            x
            <input
              type="number"
              min={MIN_BOX_SIZE}
              step={GRID_SIZE}
              value={bulkForm.height}
              onChange={(e) => setBulkForm((prev) => ({ ...prev, height: e.target.value }))}
              onBlur={() =>
                setBulkForm((prev) => ({ ...prev, height: snapToGrid(Math.max(MIN_BOX_SIZE, Number(prev.height) || DEFAULT_BOX_HEIGHT)) }))
              }
              className="w-[56px] border border-hairline rounded px-1 py-0.5"
            />
          </span>
        </label>
        <label className="text-[11px] text-ink-muted flex flex-col gap-0.5">
          칸 간격
          <input
            type="number"
            min={0}
            step={GRID_SIZE}
            value={bulkForm.gap}
            onChange={(e) => setBulkForm((prev) => ({ ...prev, gap: e.target.value }))}
            onBlur={() => setBulkForm((prev) => ({ ...prev, gap: snapToGrid(Math.max(0, Number(prev.gap) || 0)) }))}
            className="w-[56px] border border-hairline rounded px-1 py-0.5"
          />
        </label>
        <span className="text-[10px] text-ink-muted basis-full">
          칸 크기·간격도 격자와 맞도록 10px 단위로 저장돼요. 입력 후 다른 곳을 클릭하면 가까운 10 단위로 반올림돼요.
        </span>
        <label className="text-[11px] text-ink-muted flex flex-col gap-0.5">
          라벨 접두사
          <input
            value={bulkForm.prefix}
            onChange={(e) => setBulkForm((prev) => ({ ...prev, prefix: e.target.value }))}
            className="w-[56px] border border-hairline rounded px-1 py-0.5"
          />
        </label>
        <label className="text-[11px] text-ink-muted flex flex-col gap-0.5">
          시작 번호
          <input
            type="number"
            min={0}
            value={bulkForm.start}
            onChange={(e) => setBulkForm((prev) => ({ ...prev, start: e.target.value }))}
            className="w-[56px] border border-hairline rounded px-1 py-0.5"
          />
        </label>
        <button
          onClick={addBulkGrid}
          className="text-[11px] bg-primary text-white rounded-full px-md py-1"
        >
          추가
        </button>
      </div>

      <div
        ref={surfaceRef}
        onMouseDown={handleSurfaceMouseDown}
        className="relative bg-white border border-hairline rounded-lg overflow-hidden select-none cursor-crosshair"
        style={{
          width: "100%",
          aspectRatio: `${CANVAS_WIDTH} / ${CANVAS_HEIGHT}`,
          // 정렬 참고용 격자 - 편집 화면에만 CSS로 그려지며 handleComplete가 만드는
          // 결과 이미지에는 포함되지 않는다.
          backgroundImage:
            "linear-gradient(to right, var(--color-hairline, #e2e2e2) 1px, transparent 1px)," +
            "linear-gradient(to bottom, var(--color-hairline, #e2e2e2) 1px, transparent 1px)",
          backgroundSize: `${(GRID_SIZE / CANVAS_WIDTH) * 100}% ${(GRID_SIZE / CANVAS_HEIGHT) * 100}%`,
        }}
      >
        {boxes.map((box) => (
          <div
            key={box.id}
            onMouseDown={handleBoxMouseDown(box)}
            className={`absolute border-2 bg-white/60 cursor-move flex items-center justify-center ${
              selectedIds.has(box.id) ? "border-primary" : "border-ink"
            }`}
            style={{
              left: `${(box.x / CANVAS_WIDTH) * 100}%`,
              top: `${(box.y / CANVAS_HEIGHT) * 100}%`,
              width: `${(box.width / CANVAS_WIDTH) * 100}%`,
              height: `${(box.height / CANVAS_HEIGHT) * 100}%`,
            }}
          >
            <input
              ref={(el) => {
                if (el && pendingFocusIdRef.current === box.id) {
                  // 새로 만든 칸에 포커스를 주더라도 브라우저가 그 위치로 스크롤하며
                  // 화면이 흔들리지 않도록 preventScroll을 준다.
                  el.focus({ preventScroll: true });
                  pendingFocusIdRef.current = null;
                }
              }}
              value={box.label}
              onChange={(e) => updateBox(box.id, { label: e.target.value })}
              onMouseDown={(e) => e.stopPropagation()}
              onClick={(e) => {
                e.stopPropagation();
                setSelectedIds(computeNextSelection(box, e));
              }}
              placeholder="부스 번호"
              className="w-[85%] text-center text-[12px] font-bold bg-transparent outline-none"
            />
            {selectedIds.has(box.id) && (
              <button
                onMouseDown={(e) => e.stopPropagation()}
                onClick={(e) => {
                  e.stopPropagation();
                  removeBox(box.id);
                }}
                className="absolute -top-2 -right-2 w-[16px] h-[16px] rounded-full bg-error text-white flex items-center justify-center"
              >
                <Icon name="close" className="text-[10px]" />
              </button>
            )}
            <div
              onMouseDown={handleResizeMouseDown(box)}
              className="absolute bottom-0 right-0 cursor-se-resize"
              style={{ width: RESIZE_HANDLE_SIZE, height: RESIZE_HANDLE_SIZE }}
            >
              <div className="w-full h-full border-r-2 border-b-2 border-ink" />
            </div>
          </div>
        ))}
        {draftBox && (
          <div
            className="absolute border-2 border-dashed border-primary bg-primary/10 pointer-events-none"
            style={{
              left: `${(draftBox.x / CANVAS_WIDTH) * 100}%`,
              top: `${(draftBox.y / CANVAS_HEIGHT) * 100}%`,
              width: `${(draftBox.width / CANVAS_WIDTH) * 100}%`,
              height: `${(draftBox.height / CANVAS_HEIGHT) * 100}%`,
            }}
          />
        )}
      </div>

      <div className="flex items-center justify-between">
        <p className="text-[11px] text-ink-muted">부스 칸 {boxes.length}개</p>
        <button
          onClick={handleComplete}
          disabled={boxes.length === 0}
          className="px-lg py-sm bg-primary text-white rounded-full text-caption font-body-strong disabled:opacity-40"
        >
          그리기 완료 → 이미지로 사용
        </button>
      </div>
    </div>
  );
}
