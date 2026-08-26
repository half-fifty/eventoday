import { useEffect, useRef } from "react";
import Icon from "./Icon.jsx";
import { fileDownloadUrl } from "../api/fileApi.js";

const FOCUSABLE_ELEMENT_SELECTOR = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  '[tabindex]:not([tabindex="-1"])',
].join(",");

// 평면도 핀 클릭 시 뜨는 부스 정보 팝업.
export default function BoothPinPopup({ booth, onClose }) {
  const panelRef = useRef(null);
  const closeButtonRef = useRef(null);
  const previousFocusRef = useRef(null);

  useEffect(() => {
    if (!booth) return undefined;

    previousFocusRef.current = document.activeElement;
    const focusFrame = window.requestAnimationFrame(() => {
      closeButtonRef.current?.focus();
    });

    const handleKeyDown = (event) => {
      if (event.key === "Escape") {
        event.preventDefault();
        onClose();
        return;
      }

      if (event.key !== "Tab") return;

      const panel = panelRef.current;
      if (panel === null) return;

      const focusableElements = Array.from(panel.querySelectorAll(FOCUSABLE_ELEMENT_SELECTOR));
      if (focusableElements.length === 0) {
        event.preventDefault();
        panel.focus();
        return;
      }

      const firstElement = focusableElements[0];
      const lastElement = focusableElements[focusableElements.length - 1];
      const focusIsOutsidePanel = !panel.contains(document.activeElement);

      if (event.shiftKey && (document.activeElement === firstElement || focusIsOutsidePanel)) {
        event.preventDefault();
        lastElement.focus();
        return;
      }

      if (!event.shiftKey && (document.activeElement === lastElement || focusIsOutsidePanel)) {
        event.preventDefault();
        firstElement.focus();
      }
    };

    window.addEventListener("keydown", handleKeyDown);
    return () => {
      window.cancelAnimationFrame(focusFrame);
      window.removeEventListener("keydown", handleKeyDown);

      const previousFocus = previousFocusRef.current;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
        previousFocus.focus();
      }
      previousFocusRef.current = null;
    };
  }, [booth, onClose]);

  if (!booth) return null;

  return (
    <div
      className="fixed inset-0 z-[200] bg-black/50 flex items-center justify-center p-lg"
      onClick={onClose}
    >
      <div
        ref={panelRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby="booth-pin-popup-title"
        className="bg-white rounded-2xl max-w-[520px] w-full overflow-hidden relative shadow-2xl"
        onClick={(e) => e.stopPropagation()}
      >
        {booth.representativeFileId && (
          <img
            src={fileDownloadUrl(booth.representativeFileId)}
            alt={`${booth.displayName || booth.boothCode} 대표 이미지`}
            className="w-full aspect-[16/9] object-cover bg-surface-container"
          />
        )}
        <button
          ref={closeButtonRef}
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="absolute top-md right-md w-9 h-9 rounded-full bg-white/95 shadow grid place-items-center text-ink-muted hover:text-on-surface"
        >
          <Icon name="close" className="text-[22px]" />
        </button>
        <div className="max-h-[70vh] overflow-y-auto p-xl">
          <p className="text-caption font-bold text-primary mb-xs">BOOTH · {booth.boothCode}</p>
          <h3 id="booth-pin-popup-title" className="font-display-md text-[22px]">
            {booth.displayName || booth.boothCode}
          </h3>
          <p className="text-caption text-ink-muted mt-xs">
            {[booth.floorName, booth.zoneName, booth.locationDescription].filter(Boolean).join(" · ") || "행사장 배치도에서 위치를 확인해 주세요."}
          </p>
          {booth.shortIntro && <p className="mt-md font-body-strong">{booth.shortIntro}</p>}
          {booth.description && <p className="mt-sm text-body text-on-surface-variant whitespace-pre-line leading-6">{booth.description}</p>}
          {booth.exhibitionContent && (
            <div className="mt-md rounded-xl bg-primary/5 p-md">
              <p className="text-caption font-bold text-primary">전시 · 판매 내용</p>
              <p className="mt-xs text-caption whitespace-pre-line leading-6">{booth.exhibitionContent}</p>
            </div>
          )}
        </div>
      </div>
    </div>
  );
}
