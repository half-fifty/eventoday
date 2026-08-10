import { useEffect, useRef } from "react";
import Icon from "./Icon.jsx";

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
        className="bg-white rounded-2xl max-w-[360px] w-full p-xl relative"
        onClick={(e) => e.stopPropagation()}
      >
        <button
          ref={closeButtonRef}
          type="button"
          onClick={onClose}
          aria-label="닫기"
          className="absolute top-lg right-lg text-ink-muted hover:text-on-surface"
        >
          <Icon name="close" className="text-[22px]" />
        </button>
        <h3 id="booth-pin-popup-title" className="font-display-md text-[18px] mb-1">
          {booth.displayName || booth.boothCode}
        </h3>
        <p className="text-caption text-ink-muted">{booth.boothCode}</p>
      </div>
    </div>
  );
}
