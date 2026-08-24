import { createContext, useCallback, useMemo, useState } from "react";
import Icon from "../components/Icon.jsx";
import useModalFocusTrap from "../hooks/useModalFocusTrap.js";

// 검증 실패 등 "오류 메시지"를 window.alert 대신 통일된 팝업으로 띄우기 위한 프로바이더.
// window.alert는 브라우저 기본 UI라 디자인을 못 맞추고 접근성 처리도 화면마다 제각각이었다.
export const AlertModalContext = createContext(null);

export default function AlertModalProvider({ children }) {
  const [state, setState] = useState(null); // { message, title } | null

  const closeAlert = useCallback(() => setState(null), []);

  const showAlert = useCallback((message, options = {}) => {
    setState({ message, title: options.title ?? "안내" });
  }, []);

  const { panelRef, initialFocusRef } = useModalFocusTrap(state !== null, closeAlert);

  const value = useMemo(() => ({ showAlert }), [showAlert]);

  return (
    <AlertModalContext.Provider value={value}>
      {children}
      {state !== null && (
        <div
          className="fixed inset-0 z-[400] flex items-center justify-center bg-black/50 p-lg"
          onClick={closeAlert}
        >
          <div
            ref={panelRef}
            tabIndex={-1}
            role="alertdialog"
            aria-modal="true"
            aria-labelledby="alert-modal-title"
            aria-describedby="alert-modal-message"
            className="w-full max-w-[380px] rounded-2xl bg-white p-xl shadow-2xl"
            onClick={(event) => event.stopPropagation()}
          >
            <div className="mb-md flex items-center gap-sm">
              <span className="flex h-9 w-9 items-center justify-center rounded-full bg-error/10 text-error">
                <Icon name="error" className="text-[20px]" />
              </span>
              <h2 id="alert-modal-title" className="font-body-strong text-body-strong">
                {state.title}
              </h2>
            </div>
            <p id="alert-modal-message" className="text-body text-on-surface-variant whitespace-pre-line">
              {state.message}
            </p>
            <button
              ref={initialFocusRef}
              type="button"
              onClick={closeAlert}
              className="mt-lg h-[40px] w-full rounded-xl bg-primary text-white font-body-strong"
            >
              확인
            </button>
          </div>
        </div>
      )}
    </AlertModalContext.Provider>
  );
}
