import FunnelStepChart from "./FunnelStepChart.jsx";

// 행사 하나의 퍼널 요약(차트 + 보조 지표)을 보여준다. 개최자센터/관리자센터 공용.
const percentage = (count, total) => (total > 0 ? `${((count / total) * 100).toFixed(1)}%` : "-");

export default function FunnelSummaryStats({ summary }) {
  return (
    <div className="space-y-lg">
      <div className="flex flex-wrap items-baseline gap-sm text-caption text-ink-muted">
        <span>
          총 방문 <span className="font-body-strong text-on-surface">{summary.totalSessions.toLocaleString()}명</span>
        </span>
        <span>·</span>
        <span>
          결제 전환율{" "}
          <span className="font-body-strong text-on-surface">
            {percentage(summary.completePaymentCount, summary.totalSessions)}
          </span>
        </span>
      </div>

      <FunnelStepChart summary={summary} />

      <div className="overflow-hidden rounded-xl border border-hairline bg-white">
        <div className="border-b border-hairline px-lg py-md">
          <h3 className="font-body-strong">보조 지표</h3>
        </div>
        <div className="divide-y divide-divider-soft">
          <div className="flex items-center justify-between p-lg text-caption">
            <span className="text-ink-muted">이탈 (결제완료 미도달)</span>
            <span className="font-body-strong">
              {summary.droppedCount.toLocaleString()}명 ({percentage(summary.droppedCount, summary.totalSessions)})
            </span>
          </div>
          <div className="flex items-center justify-between p-lg text-caption">
            <span className="text-ink-muted">부스탐색 경험</span>
            <span className="font-body-strong">
              {summary.boothExploredCount.toLocaleString()}명 (
              {percentage(summary.boothExploredCount, summary.totalSessions)})
            </span>
          </div>
          <div className="flex items-center justify-between p-lg text-caption">
            <span className="text-ink-muted">재방문자</span>
            <span className="font-body-strong">
              {summary.returningVisitorCount.toLocaleString()}명 (
              {percentage(summary.returningVisitorCount, summary.totalSessions)})
            </span>
          </div>
        </div>
      </div>
    </div>
  );
}
