import Icon from "./Icon.jsx";

const boothLabel = (boothsMap, boothId) => {
  const booth = boothsMap?.[boothId];
  return booth?.displayName || booth?.boothCode || `부스 ${boothId}`;
};

// 혼잡도 기반 한산한 부스 추천 배너. EventDetail 평면도 섹션 상단에 표시된다.
export default function BoothRecommendationMessage({ recommendation, boothsMap }) {
  const recommendedBooths = recommendation?.recommendedBooths ?? [];
  if (recommendedBooths.length === 0) return null;

  const congestedBooths = recommendation?.congestedBooths ?? [];

  return (
    <div className="bg-gradient-to-br from-amber-50 to-orange-50 border border-amber-200 rounded-2xl p-lg">
      <div className="flex items-start gap-sm">
        <span className="flex-shrink-0 w-9 h-9 rounded-full bg-gradient-to-r from-amber-400 to-orange-400 flex items-center justify-center">
          <Icon name="auto_awesome" className="text-white text-[18px]" />
        </span>
        <div className="flex-1 min-w-0">
          <p className="font-body-strong text-[15px] text-amber-900 mb-xs">실시간 혼잡도 기반 추천 부스</p>
          {recommendation.recommendation && (
            <p className="text-caption text-amber-800 mb-sm">{recommendation.recommendation}</p>
          )}
          <div className="flex flex-wrap gap-xs">
            {recommendedBooths.map((booth) => (
              <span
                key={booth.boothId}
                className="inline-flex items-center gap-1 px-sm py-xs bg-white/70 border border-amber-200 rounded-full text-[12px] font-body-strong text-amber-900"
              >
                <Icon name="storefront" className="text-[14px]" />
                {boothLabel(boothsMap, booth.boothId)}
              </span>
            ))}
          </div>
          {congestedBooths.length > 0 && (
            <p className="text-[11px] text-amber-700/80 mt-sm">
              지금 혼잡한 부스: {congestedBooths.map((booth) => boothLabel(boothsMap, booth.boothId)).join(", ")}
            </p>
          )}
        </div>
      </div>
    </div>
  );
}
