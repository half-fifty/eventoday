import { useCallback, useEffect, useRef, useState } from "react";
import Icon from "./Icon.jsx";
import RichTextViewer from "./RichTextViewer.jsx";
import { ApiError } from "../api/apiClient.js";
import useModalFocusTrap from "../hooks/useModalFocusTrap.js";

// 공지 AI 작성 보조 모달
//
// AI는 초안을 만들 뿐이고 저장하지 않는다. 결과는 미리보기로만 보여주고,
// 관리자가 "결과 적용"을 눌렀을 때만 onApply로 전달한다.
// 실패해도 작성 중이던 내용은 그대로 두기 위해, 이 컴포넌트는 에디터 상태를 직접 바꾸지 않는다.

const ACTIONS = [
  { value: "GENERATE", label: "새 글 작성", icon: "auto_awesome",
    hint: "쓰고 싶은 내용을 간단히 적으면 제목과 본문을 만들어 드립니다.",
    needsPrompt: true, needsContent: false },
  { value: "TITLE_SUGGEST", label: "제목 추천", icon: "title",
    hint: "작성한 본문을 읽고 어울리는 제목 후보를 제안합니다.",
    needsPrompt: false, needsContent: true },
  { value: "POLISH", label: "내용 다듬기", icon: "auto_fix_high",
    hint: "뜻은 그대로 두고 문장을 자연스럽게 고칩니다.",
    needsPrompt: false, needsContent: true },
  { value: "SUMMARIZE", label: "요약", icon: "compress",
    hint: "긴 내용을 핵심 위주로 줄입니다.",
    needsPrompt: false, needsContent: true },
  { value: "EXPAND", label: "내용 확장", icon: "expand",
    hint: "짧게 쓴 내용을 더 충실한 공지로 넓힙니다.",
    needsPrompt: false, needsContent: true },
  { value: "PROOFREAD", label: "맞춤법 검사", icon: "spellcheck",
    hint: "맞춤법·띄어쓰기·문법만 고치고 표현은 그대로 둡니다.",
    needsPrompt: false, needsContent: true },
  { value: "TONE", label: "문체 변경", icon: "translate",
    hint: "내용은 유지한 채 선택한 문체로 다시 씁니다.",
    needsPrompt: false, needsContent: true },
];

const TONES = [
  { value: "FORMAL", label: "공식적" },
  { value: "FRIENDLY", label: "친근한" },
  { value: "CONCISE", label: "간결한" },
  { value: "GUIDE", label: "안내문" },
];

const toErrorMessage = (err) => {
  if (err instanceof ApiError) {
    // 서버가 사용자에게 보여줄 문구를 이미 담아 보낸다 (AI_503_001 등)
    return err.message || "AI 작성에 실패했습니다. 잠시 후 다시 시도해주세요.";
  }
  return "AI 작성에 실패했습니다. 잠시 후 다시 시도해주세요.";
};

/**
 * @param {Function} onGenerate - (payload, signal) => Promise. 호출할 API를 화면이 주입한다.
 *   사이트 공지와 행사 공지·자료가 엔드포인트만 다르고 나머지는 같아서 이 모달을 공유한다.
 */
export default function ContentAiAssistant({
  open,
  onClose,
  onApply,
  onGenerate,
  currentTitle = "",
  currentContent = "",
}) {
  const [action, setAction] = useState("GENERATE");
  const [tone, setTone] = useState("FORMAL");
  const [prompt, setPrompt] = useState("");

  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");
  const [result, setResult] = useState(null);
  // 기존 본문을 덮어쓰기 직전 확인 단계.
  // 포커스 트랩이 window에 리스너를 달아 모달을 겹치면 Escape·Tab이 충돌하므로,
  // 별도 모달을 띄우지 않고 이 모달 안에서 단계로 처리한다.
  const [confirming, setConfirming] = useState(false);

  // 응답이 늦을 때 서버 타임아웃(45초)까지 기다리지 않고 직접 끊을 수 있게 한다
  const abortRef = useRef(null);

  const selected = ACTIONS.find((item) => item.value === action) || ACTIONS[0];

  // 본문이 비어 있으면 기존 내용을 요구하는 기능은 쓸 수 없다
  const hasContent = Boolean(currentContent && currentContent.trim());
  const blockedByEmptyContent = selected.needsContent && !hasContent;
  const blockedByEmptyPrompt = selected.needsPrompt && !prompt.trim();

  // 모달을 닫으면 진행 중인 요청도 함께 끊는다 — 응답이 늦게 와서 닫힌 화면에 반영되지 않게 한다
  const handleClose = useCallback(() => {
    abortRef.current?.abort();
    onClose();
  }, [onClose]);

  const { panelRef, initialFocusRef } = useModalFocusTrap(open, handleClose);

  // 모달을 열 때마다 초기화한다 — 이전 결과가 남아 잘못 적용되는 것을 막는다
  useEffect(() => {
    if (!open) return;
    setAction("GENERATE");
    setTone("FORMAL");
    setPrompt("");
    setResult(null);
    setError("");
    setLoading(false);
    setConfirming(false);
  }, [open]);

  // 본문을 바꾸는 결과인데 이미 작성한 내용이 있으면 확인을 먼저 받는다.
  // 제목만 바꾸는 제목 추천은 본문을 건드리지 않으므로 바로 적용한다.
  const requestApply = (payload) => {
    if (payload.content && hasContent) {
      setConfirming(true);
      return;
    }
    onApply(payload);
  };

  const cancelGenerate = () => {
    abortRef.current?.abort();
  };

  const runGenerate = async () => {
    if (loading || blockedByEmptyContent || blockedByEmptyPrompt) return;

    const controller = new AbortController();
    abortRef.current = controller;
    setLoading(true);
    setError("");
    try {
      const response = await onGenerate({
        action,
        tone,
        prompt: prompt.trim() || null,
        title: currentTitle || null,
        content: currentContent || null,
      }, controller.signal);
      setResult(response?.data || null);
    } catch (err) {
      // 사용자가 직접 끊은 경우는 오류가 아니므로 안내를 띄우지 않는다
      if (err?.name !== "AbortError") {
        // 실패해도 result를 지우지 않는다 — 직전 결과를 보고 있었다면 그대로 남긴다
        setError(toErrorMessage(err));
      }
    } finally {
      if (abortRef.current === controller) abortRef.current = null;
      setLoading(false);
    }
  };

  if (!open) return null;

  const titleSuggestions = result?.titleSuggestions || [];
  const hasResult = Boolean(result && (result.content || titleSuggestions.length > 0));

  return (
    <div className="fixed inset-0 z-50 flex items-center justify-center bg-black/40 p-lg">
      <div
        ref={panelRef}
        tabIndex={-1}
        role="dialog"
        aria-modal="true"
        aria-labelledby="ai-assistant-heading"
        className="w-full max-w-[720px] max-h-[88vh] overflow-y-auto rounded-2xl bg-white p-lg shadow-xl"
      >
        <div className="flex items-start justify-between gap-md">
          <div>
            <h2 id="ai-assistant-heading" className="flex items-center gap-xs font-body-strong text-[17px]">
              <Icon name="auto_awesome" className="text-[18px] text-primary" />
              AI로 작성하기
            </h2>
            <p className="mt-xxs text-caption text-ink-muted">
              결과를 확인한 뒤 직접 적용합니다. 자동으로 저장되지 않습니다.
            </p>
          </div>
          <button type="button" onClick={handleClose} aria-label="닫기"
            className="text-ink-muted hover:text-on-surface">
            <Icon name="close" />
          </button>
        </div>

        {/* 기능 선택 */}
        <div className="mt-lg">
          <span className="mb-xs block text-caption text-ink-muted">무엇을 도와드릴까요?</span>
          <div className="flex flex-wrap gap-xs">
            {ACTIONS.map((item, index) => {
              const active = item.value === action;
              const disabled = item.needsContent && !hasContent;
              return (
                <button
                  key={item.value}
                  type="button"
                  ref={index === 0 ? initialFocusRef : undefined}
                  onClick={() => { setAction(item.value); setResult(null); setError(""); }}
                  disabled={loading || disabled}
                  title={disabled ? "본문을 먼저 작성해주세요." : item.hint}
                  className={`inline-flex items-center gap-xxs rounded-full border px-md py-xs text-caption transition-colors disabled:opacity-40 ${
                    active
                      ? "border-primary bg-primary/10 text-primary font-body-strong"
                      : "border-hairline text-ink-muted hover:bg-surface-container"
                  }`}
                >
                  <Icon name={item.icon} className="text-[15px]" />
                  {item.label}
                </button>
              );
            })}
          </div>
          <p className="mt-xs text-caption text-ink-muted">{selected.hint}</p>
          {blockedByEmptyContent && (
            <p className="mt-xs text-caption text-error">본문을 먼저 작성한 뒤 사용할 수 있습니다.</p>
          )}
        </div>

        {/* 입력 — 새 글 작성일 때만 */}
        {selected.needsPrompt && (
          <div className="mt-md">
            <label htmlFor="ai-prompt" className="mb-xs block text-caption text-ink-muted">
              어떤 공지사항을 작성할까요?
            </label>
            <textarea
              id="ai-prompt"
              value={prompt}
              maxLength={2000}
              disabled={loading}
              onChange={(event) => setPrompt(event.target.value)}
              placeholder={"예) 8월 25일 오후 2시부터 4시까지 서버 점검\n점검 중 서비스 이용 불가"}
              className="min-h-[96px] w-full resize-y rounded-lg border border-hairline bg-white px-md py-sm text-caption focus:border-primary focus:outline-none focus:ring-1 focus:ring-primary disabled:opacity-60"
            />
          </div>
        )}

        {/* 문체 — 제목 추천·맞춤법에는 영향이 없어 숨긴다 */}
        {action !== "TITLE_SUGGEST" && action !== "PROOFREAD" && (
          <fieldset className="mt-md">
            <legend className="mb-xs text-caption text-ink-muted">문체</legend>
            <div className="flex flex-wrap gap-md">
              {TONES.map((item) => (
                <label key={item.value} className="flex cursor-pointer items-center gap-xxs text-caption">
                  <input
                    type="radio"
                    name="ai-tone"
                    value={item.value}
                    checked={tone === item.value}
                    disabled={loading}
                    onChange={(event) => setTone(event.target.value)}
                    className="accent-primary"
                  />
                  {item.label}
                </label>
              ))}
            </div>
          </fieldset>
        )}

        {error && (
          <p className="mt-md rounded-xl border border-error/20 bg-error/10 px-md py-sm text-caption text-error">
            {error}
          </p>
        )}

        {loading && (
          <div className="mt-md flex items-center justify-between gap-sm rounded-xl bg-surface-container px-md py-sm">
            <p className="flex items-center gap-xs text-caption text-ink-muted">
              <Icon name="auto_awesome" className="text-[16px] animate-pulse" />
              AI가 내용을 작성하고 있습니다...
            </p>
            <button type="button" onClick={cancelGenerate}
              className="flex-shrink-0 text-caption text-ink-muted underline hover:text-on-surface">
              중단
            </button>
          </div>
        )}

        {/* 결과 미리보기 */}
        {!loading && hasResult && (
          <div className="mt-lg rounded-xl border border-hairline bg-surface-pearl p-md">
            <p className="mb-sm font-body-strong text-caption">AI 작성 결과</p>

            {titleSuggestions.length > 0 && (
              <ul className="space-y-xs">
                {titleSuggestions.map((suggestion) => (
                  <li key={suggestion}>
                    <button
                      type="button"
                      onClick={() => requestApply({ title: suggestion })}
                      className="w-full rounded-lg border border-hairline bg-white px-md py-sm text-left text-caption hover:border-primary hover:bg-primary/5 transition-colors"
                    >
                      {suggestion}
                    </button>
                  </li>
                ))}
              </ul>
            )}

            {result.title && (
              <div className="mb-sm">
                <span className="text-caption text-ink-muted">제목</span>
                <p className="font-body-strong">{result.title}</p>
              </div>
            )}

            {result.content && (
              <div className="rounded-lg border border-hairline bg-white p-md">
                <RichTextViewer html={result.content} />
              </div>
            )}
          </div>
        )}

        {/* 덮어쓰기 확인 단계 */}
        {confirming ? (
          <div className="mt-lg rounded-xl border border-error/30 bg-error/5 p-md">
            <p className="text-caption">
              현재 작성 중인 내용을 AI 결과로 교체할까요? 기존 내용은 되돌릴 수 없습니다.
            </p>
            <div className="mt-md flex justify-end gap-sm">
              <button type="button" onClick={() => setConfirming(false)}
                className="rounded-full border border-hairline px-lg py-sm text-caption">
                취소
              </button>
              <button
                type="button"
                onClick={() => onApply({ title: result.title, content: result.content })}
                className="rounded-full bg-primary px-lg py-sm font-body-strong text-caption text-white"
              >
                교체
              </button>
            </div>
          </div>
        ) : (
          <div className="mt-lg flex flex-wrap justify-end gap-sm">
            <button type="button" onClick={handleClose}
              className="rounded-full border border-hairline px-lg py-sm text-caption">
              취소
            </button>
            {hasResult && (
              <button type="button" onClick={runGenerate} disabled={loading}
                className="rounded-full border border-hairline px-lg py-sm text-caption disabled:opacity-40">
                다시 생성
              </button>
            )}
            {hasResult && result.content ? (
              <button
                type="button"
                onClick={() => requestApply({ title: result.title, content: result.content })}
                className="rounded-full bg-primary px-lg py-sm font-body-strong text-caption text-white"
              >
                결과 적용
              </button>
            ) : (
              <button
                type="button"
                onClick={runGenerate}
                disabled={loading || blockedByEmptyContent || blockedByEmptyPrompt}
                className="inline-flex items-center gap-xxs rounded-full bg-primary px-lg py-sm font-body-strong text-caption text-white disabled:opacity-40"
              >
                <Icon name="auto_awesome" className="text-[16px]" />
                {loading ? "작성 중..." : "AI로 작성하기"}
              </button>
            )}
          </div>
        )}

        {titleSuggestions.length > 0 && (
          <p className="mt-xs text-right text-caption text-ink-muted">제목을 클릭하면 적용됩니다.</p>
        )}
      </div>
    </div>
  );
}
