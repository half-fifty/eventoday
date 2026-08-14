// 백엔드 혼잡도 기준과 동일하게 맞춘 상수 (VenueMapCongestionController 참고).
// 최근 10분 QR 스캔 수 기준: HIGH 20명 이상, MEDIUM 10~19명, LOW 10명 미만.
const CONGESTION_LEVEL_META = {
  HIGH: { label: "혼잡", colorClass: "status-visited" },
  MEDIUM: { label: "보통", colorClass: "status-pending" },
  LOW: { label: "여유", colorClass: "status-available" },
};

// 마커 API처럼 congestionLevel 문자열을 이미 받은 경우 사용.
const congestionLevelMeta = (level) => CONGESTION_LEVEL_META[level] ?? null;

// 추천/인기 부스 API처럼 congestionCount(숫자)만 받은 경우, 백엔드와 동일한 기준으로 레벨을 계산한다.
const congestionLevelFromCount = (count) => {
  const value = Number(count) || 0;
  if (value >= 20) return "HIGH";
  if (value >= 10) return "MEDIUM";
  return "LOW";
};

export { CONGESTION_LEVEL_META, congestionLevelMeta, congestionLevelFromCount };
