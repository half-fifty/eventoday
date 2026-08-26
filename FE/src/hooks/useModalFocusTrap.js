import { useEffect, useRef } from "react";

// BoothPinPopup.jsx의 포커스 트랩 구현을 모달에서 재사용할 수 있도록 훅으로 추출한 것.
// 같은 로직을 패널마다 복사하지 않기 위해 분리했다.
const FOCUSABLE_ELEMENT_SELECTOR = [
  "a[href]",
  "button:not([disabled])",
  "input:not([disabled])",
  "select:not([disabled])",
  "textarea:not([disabled])",
  '[tabindex]:not([tabindex="-1"])',
].join(",");

/**
 * 모달 접근성 처리 훅
 * - 열릴 때 모달 내부로 포커스 이동
 * - Tab 포커스를 모달 안에 가둠
 * - Escape로 닫기
 * - 닫을 때 직전 포커스 요소로 복원
 *
 * @param {boolean}  isOpen  모달 표시 여부
 * @param {Function} onClose 닫기 핸들러 (Escape 시 호출)
 * @returns {{ panelRef: object, initialFocusRef: object }}
 *          panelRef는 모달 패널 요소에, initialFocusRef는 처음 포커스할 요소에 연결한다.
 */
export default function useModalFocusTrap(isOpen, onClose) {
  const panelRef = useRef(null);
  const initialFocusRef = useRef(null);
  const previousFocusRef = useRef(null);

  useEffect(() => {
    if (!isOpen) return undefined;

    previousFocusRef.current = document.activeElement;
    // 렌더 직후 DOM이 붙은 뒤 포커스를 옮긴다
    const focusFrame = window.requestAnimationFrame(() => {
      (initialFocusRef.current || panelRef.current)?.focus();
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

      // 모달을 연 버튼으로 포커스를 되돌린다
      const previousFocus = previousFocusRef.current;
      if (previousFocus instanceof HTMLElement && previousFocus.isConnected) {
        previousFocus.focus();
      }
      previousFocusRef.current = null;
    };
  }, [isOpen, onClose]);

  return { panelRef, initialFocusRef };
}
