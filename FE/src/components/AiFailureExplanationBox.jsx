import { useState } from "react";

const DEFAULT_QUESTION = "왜 이용할 수 없나요?";

export default function AiFailureExplanationBox({
  buttonLabel,
  question = DEFAULT_QUESTION,
  onRequest,
  className = "",
}) {
  const [loading, setLoading] = useState(false);
  const [result, setResult] = useState(null);
  const [error, setError] = useState("");

  const requestExplanation = async () => {
    if (loading || typeof onRequest !== "function") return;
    setLoading(true);
    setError("");
    try {
      const response = await onRequest({ question });
      setResult(response?.data || null);
    } catch (requestError) {
      setResult(null);
      setError(requestError.message || "AI 설명을 불러오지 못했습니다. 잠시 후 다시 시도해 주세요.");
    } finally {
      setLoading(false);
    }
  };

  return (
    <div className={`rounded-xl border border-hairline bg-surface-container p-md ${className}`}>
      <button
        type="button"
        onClick={requestExplanation}
        disabled={loading}
        className="rounded-full border border-primary px-md py-1.5 text-caption font-body-strong text-primary disabled:opacity-50"
      >
        {loading ? "AI 설명 확인 중..." : buttonLabel}
      </button>

      {error && (
        <p className="mt-sm rounded-lg bg-error/10 p-sm text-caption text-error">
          {error}
        </p>
      )}

      {result && (
        <div className="mt-md space-y-sm text-caption">
          <div>
            <p className="font-body-strong text-on-surface">AI 설명</p>
            <p className="mt-xs whitespace-pre-wrap text-on-surface-variant">
              {result.explanation || "설명을 확인할 수 없습니다."}
            </p>
          </div>
          {result.recommendedAction && (
            <div>
              <p className="font-body-strong text-on-surface">권장 조치</p>
              <p className="mt-xs whitespace-pre-wrap text-on-surface-variant">
                {result.recommendedAction}
              </p>
            </div>
          )}
          {result.needsHumanSupport && (
            <p className="rounded-lg bg-primary/10 p-sm text-primary">
              추가 확인이 필요할 수 있습니다. 현장 스태프 또는 행사 운영자에게 문의해 주세요.
            </p>
          )}
          {result.aiGenerated === false && (
            <p className="text-[11px] text-ink-muted">
              현재는 기본 안내 문구로 표시됩니다.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
