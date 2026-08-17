import Icon from "./Icon.jsx";

// 부스 리뷰 코멘트 AI 요약 카드. BoothDetail 방문객 후기 섹션 상단에 표시된다.
export default function BoothReviewSummaryCard({ summary }) {
  if (!summary?.available) return null;

  return (
    <div className="bg-gradient-to-br from-amber-50 to-orange-50 border border-amber-200 rounded-2xl p-lg mb-lg">
      <div className="flex items-start gap-sm">
        <span className="flex-shrink-0 w-9 h-9 rounded-full bg-gradient-to-r from-amber-400 to-orange-400 flex items-center justify-center">
          <Icon name="auto_awesome" className="text-white text-[18px]" />
        </span>
        <div className="flex-1 min-w-0">
          <p className="font-body-strong text-[15px] text-amber-900 mb-xs">AI 리뷰 요약</p>
          <p className="text-caption text-amber-800 whitespace-pre-line">{summary.summary}</p>
          {summary.stale && (
            <p className="text-[11px] text-amber-700/80 mt-sm">최신 리뷰 반영에 실패해 이전 요약을 보여드리고 있어요.</p>
          )}
        </div>
      </div>
    </div>
  );
}
