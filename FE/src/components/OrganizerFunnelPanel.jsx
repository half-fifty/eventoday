import { useEffect, useState } from "react";
import { funnelSessionApi } from "../api/funnelSessionApi.js";
import FunnelSummaryStats from "./FunnelSummaryStats.jsx";

// 개최자센터 - 선택한 행사의 퍼널 전환 현황 패널.
// 조직/행사 선택은 OrganizerAdmin.jsx 상단의 공용 드롭다운을 그대로 쓴다.
const yesterday = () => {
  const date = new Date();
  date.setDate(date.getDate() - 1);
  return date.toISOString().slice(0, 10);
};

export default function OrganizerFunnelPanel({ organizationId, eventId }) {
  const [date, setDate] = useState(yesterday());
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(false);
  const [error, setError] = useState("");

  useEffect(() => {
    if (!organizationId || !eventId) {
      setSummary(null);
      return;
    }
    setLoading(true);
    setError("");
    funnelSessionApi.summaryForOrganizer(organizationId, eventId, date)
      .then((response) => setSummary(response?.data || null))
      .catch((requestError) => {
        setError(requestError.message || "퍼널 데이터를 불러오지 못했습니다.");
        setSummary(null);
      })
      .finally(() => setLoading(false));
  }, [organizationId, eventId, date]);

  if (!eventId) {
    return (
      <div className="rounded-xl border border-hairline bg-white p-xl text-center text-caption text-ink-muted">
        상단에서 조회할 행사를 먼저 선택해주세요.
      </div>
    );
  }

  return (
    <section className="space-y-lg">
      <div>
        <h2 className="font-display-md text-[22px]">퍼널 전환 현황</h2>
        <p className="mt-xs text-caption text-ink-muted">
          방문부터 결제완료까지, 선택한 날짜의 티켓 구매 퍼널 지표입니다. 전날 새벽 배치로 집계되어 오늘자 데이터는
          아직 반영되지 않을 수 있습니다.
        </p>
      </div>

      <label className="flex w-fit flex-col gap-1 text-caption text-ink-muted">
        날짜
        <input
          type="date"
          value={date}
          onChange={(event) => setDate(event.target.value)}
          className="rounded-lg border border-hairline px-sm py-xs"
        />
      </label>

      {loading && (
        <div className="rounded-xl border border-hairline bg-white p-lg text-caption text-ink-muted">
          불러오는 중입니다.
        </div>
      )}
      {error && (
        <div className="rounded-xl border border-error/20 bg-error/10 p-lg text-caption text-error">{error}</div>
      )}
      {summary && !loading && <FunnelSummaryStats summary={summary} />}
    </section>
  );
}
