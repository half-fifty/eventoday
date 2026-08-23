import { useEffect, useState } from "react";
import { eventApi } from "../api/eventApi.js";
import { funnelSessionApi } from "../api/funnelSessionApi.js";
import { toLocalDateString } from "../utils/datetime.js";
import FunnelEventRankingChart from "./FunnelEventRankingChart.jsx";
import FunnelSummaryStats from "./FunnelSummaryStats.jsx";

// 플랫폼 관리자센터 - 행사별 퍼널 전환 현황 패널
// FunnelSession 원본을 그 자리에서 집계한 수치만 보여준다. 이상탐지/AI 요약은 아직 없다
// (P0.5/P1에서 FunnelDiagnosisReport로 대체될 예정 — docs/funnel-ai-diagnosis 참고).
// PlatformAdmin.jsx가 이미 길어 다른 관리 패널(PlatformNoticePanel 등)처럼 별도 컴포넌트로 분리한다.

const yesterday = () => {
  const date = new Date();
  date.setDate(date.getDate() - 1);
  return toLocalDateString(date);
};

export default function FunnelDashboardPanel() {
  const [events, setEvents] = useState([]);
  const [eventId, setEventId] = useState("");
  const [date, setDate] = useState(yesterday());
  const [summary, setSummary] = useState(null);
  const [loading, setLoading] = useState(false);
  const [reconstructing, setReconstructing] = useState(false);
  const [error, setError] = useState("");

  const [rankingDate, setRankingDate] = useState(yesterday());
  const [ranking, setRanking] = useState(null);
  const [rankingLoading, setRankingLoading] = useState(false);
  const [rankingError, setRankingError] = useState("");

  useEffect(() => {
    eventApi.adminList({ size: 200, sort: "name,asc" })
      .then((response) => setEvents(response?.data?.content || []))
      .catch(() => setEvents([]));
  }, []);

  useEffect(() => {
    setRankingLoading(true);
    setRankingError("");
    funnelSessionApi.ranking(rankingDate)
      .then((response) => setRanking(response?.data || null))
      .catch((requestError) => {
        setRankingError(requestError.message || "전체 행사 현황을 불러오지 못했습니다.");
        setRanking(null);
      })
      .finally(() => setRankingLoading(false));
  }, [rankingDate]);

  const loadSummary = async () => {
    if (!eventId) {
      setError("조회할 행사를 선택해주세요.");
      return;
    }
    setLoading(true);
    setError("");
    try {
      const response = await funnelSessionApi.summary(eventId, date);
      setSummary(response?.data || null);
    } catch (requestError) {
      setError(requestError.message || "퍼널 데이터를 불러오지 못했습니다.");
      setSummary(null);
    } finally {
      setLoading(false);
    }
  };

  // 새벽 3시 배치를 기다리지 않고 선택한 날짜치를 즉시 재구성한다 (개발/확인용 수동 트리거).
  const handleReconstruct = async () => {
    if (!eventId) {
      setError("조회할 행사를 선택해주세요.");
      return;
    }
    setReconstructing(true);
    setError("");
    try {
      await funnelSessionApi.reconstruct(eventId, date);
      await loadSummary();
    } catch (requestError) {
      setError(requestError.message || "퍼널 세션 재구성에 실패했습니다.");
    } finally {
      setReconstructing(false);
    }
  };

  return (
    <section className="space-y-xl">
      <div>
        <h1 className="font-display-lg text-[26px]">행사 퍼널 전환 현황</h1>
        <p className="mt-xs text-caption text-ink-muted">
          행사별 티켓 구매 퍼널의 원시 집계 수치입니다. 이상탐지·AI 요약은 아직 제공되지 않습니다.
        </p>
      </div>

      <div className="flex flex-wrap items-end gap-md rounded-xl border border-hairline bg-white p-lg">
        <label className="flex flex-col gap-1 text-caption text-ink-muted">
          행사
          <select
            value={eventId}
            onChange={(event) => setEventId(event.target.value)}
            className="min-w-[220px] rounded-lg border border-hairline px-sm py-xs"
          >
            <option value="">행사를 선택하세요</option>
            {events.map((item) => (
              <option key={item.id} value={item.id}>{item.name}</option>
            ))}
          </select>
        </label>
        <label className="flex flex-col gap-1 text-caption text-ink-muted">
          날짜
          <input
            type="date"
            value={date}
            onChange={(event) => setDate(event.target.value)}
            className="rounded-lg border border-hairline px-sm py-xs"
          />
        </label>
        <button
          type="button"
          onClick={loadSummary}
          disabled={loading || reconstructing}
          className="rounded-full bg-primary px-lg py-xs text-caption font-body-strong text-white disabled:opacity-50"
        >
          조회
        </button>
        <button
          type="button"
          onClick={handleReconstruct}
          disabled={loading || reconstructing}
          title="새벽 3시 배치를 기다리지 않고 선택한 날짜치를 즉시 재구성합니다 (개발/확인용)"
          className="rounded-full border border-hairline px-lg py-xs text-caption font-body-strong text-ink disabled:opacity-50"
        >
          {reconstructing ? "재구성 중..." : "재구성"}
        </button>
      </div>

      {loading && (
        <div className="rounded-xl border border-hairline bg-white p-lg text-caption text-ink-muted">
          불러오는 중입니다.
        </div>
      )}
      {error && (
        <div className="rounded-xl border border-error/20 bg-error/10 p-lg text-caption text-error">{error}</div>
      )}

      {summary && !loading && <FunnelSummaryStats summary={summary} />}

      {!summary && !loading && !error && (
        <div className="rounded-xl border border-hairline bg-white p-xl text-center text-caption text-ink-muted">
          행사를 선택하고 조회해주세요.
        </div>
      )}

      <div className="space-y-md border-t border-hairline pt-xl">
        <div className="flex flex-wrap items-end justify-between gap-md">
          <div>
            <h2 className="font-display-md text-[22px]">전체 행사 현황</h2>
            <p className="mt-xs text-caption text-ink-muted">선택한 날짜, 전체 행사의 방문수·결제완료수 랭킹입니다.</p>
          </div>
          <label className="flex flex-col gap-1 text-caption text-ink-muted">
            날짜
            <input
              type="date"
              value={rankingDate}
              onChange={(event) => setRankingDate(event.target.value)}
              className="rounded-lg border border-hairline px-sm py-xs"
            />
          </label>
        </div>

        {rankingLoading && (
          <div className="rounded-xl border border-hairline bg-white p-lg text-caption text-ink-muted">
            불러오는 중입니다.
          </div>
        )}
        {rankingError && (
          <div className="rounded-xl border border-error/20 bg-error/10 p-lg text-caption text-error">
            {rankingError}
          </div>
        )}
        {!rankingLoading && !rankingError && ranking && ranking.events.length === 0 && (
          <div className="rounded-xl border border-hairline bg-white p-xl text-center text-caption text-ink-muted">
            선택한 날짜에 방문 기록이 있는 행사가 없습니다.
          </div>
        )}
        {!rankingLoading && ranking && ranking.events.length > 0 && (
          <div className="rounded-xl border border-hairline bg-white p-lg">
            <FunnelEventRankingChart events={ranking.events} />
          </div>
        )}
      </div>
    </section>
  );
}
