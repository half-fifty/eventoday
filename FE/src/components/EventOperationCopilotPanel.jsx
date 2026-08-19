import { useState } from "react";
import { aiApi } from "../api/aiApi.js";
import Icon from "./Icon.jsx";

export default function EventOperationCopilotPanel({ eventId }) {
  const [question, setQuestion] = useState("");
  const [answer, setAnswer] = useState(null);
  const [error, setError] = useState("");
  const [submitting, setSubmitting] = useState(false);

  const submit = async (event) => {
    event.preventDefault();
    if (submitting) return;
    const trimmedQuestion = question.trim();
    if (!trimmedQuestion) {
      setError("질문을 입력해 주세요.");
      return;
    }

    setSubmitting(true);
    setError("");
    setAnswer(null);
    try {
      const response = await aiApi.askEventCopilot(eventId, {
        question: trimmedQuestion,
      });
      setAnswer(response?.data || null);
    } catch (requestError) {
      setError(requestError.message || "AI Copilot 응답을 불러오지 못했습니다.");
    } finally {
      setSubmitting(false);
    }
  };

  return (
    <div className="rounded-xl border border-hairline bg-white p-lg space-y-md">
      <div className="flex items-start gap-sm">
        <span className="grid h-9 w-9 flex-shrink-0 place-items-center rounded-lg bg-primary/10 text-primary">
          <Icon name="auto_awesome" className="text-[18px]" />
        </span>
        <div>
          <h2 className="font-body-strong">AI 운영 Copilot</h2>
          <p className="mt-xs text-caption text-ink-muted">
            주문, 입장권, 교환 코드, 행사 운영 정보를 질문해 보세요.
          </p>
        </div>
      </div>

      <form onSubmit={submit} className="space-y-sm">
        <textarea
          value={question}
          onChange={(event) => setQuestion(event.target.value)}
          maxLength={2000}
          rows={3}
          placeholder="예: 현재 행사 운영 정보를 알려줘"
          className="w-full resize-none rounded-lg border border-hairline px-md py-sm text-caption outline-none focus:border-primary-focus"
        />
        <div className="flex items-center justify-between gap-sm">
          <span className="text-[11px] text-ink-muted">{question.length}/2000</span>
          <button
            type="submit"
            disabled={submitting}
            className="rounded-full bg-primary px-lg py-sm text-caption font-body-strong text-white disabled:opacity-50"
          >
            {submitting ? "질문 중..." : "질문"}
          </button>
        </div>
      </form>

      {error && (
        <p className="rounded-lg bg-error/10 p-sm text-caption text-error">
          {error}
        </p>
      )}

      {answer && (
        <div className="rounded-xl bg-surface-container p-md text-caption">
          <p className="font-body-strong text-on-surface">AI 답변</p>
          <p className="mt-xs whitespace-pre-wrap text-on-surface-variant">
            {answer.answer || "답변을 확인할 수 없습니다."}
          </p>
          {answer.needsHumanSupport && (
            <p className="mt-sm rounded-lg bg-primary/10 p-sm text-primary">
              추가 확인이 필요할 수 있습니다. 행사 운영 정보를 직접 확인해 주세요.
            </p>
          )}
        </div>
      )}
    </div>
  );
}
