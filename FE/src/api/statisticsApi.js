import { apiRequest } from "./apiClient.js";

// STAT-API-001: 시간대별 부스 통계 (date 생략 시 BE에서 오늘 날짜 사용)
// 응답: { boothId, statDate, hourlyStats: [{ statHour, reservationCount, noShowCount, qrScanCount }] }
const getHourlyStatistics = async (boothId, date) => {
  const query = date ? `?date=${date}` : "";
  const response = await apiRequest(`/booths/${boothId}/statistics/hourly${query}`);
  return response.data;
};

// STAT-API-002: 전날 부스 통계
// 응답: { boothId, statDate, totalReservationCount, totalNoShowCount, totalQrScanCount, hourlyStats }
const getPreviousDayStatistics = async (boothId) => {
  const response = await apiRequest(`/booths/${boothId}/statistics/previous-day`);
  return response.data;
};

// STAT-API-003: 기간별 인기 부스 통계 (from, to: "YYYY-MM-DD" 필수)
// 응답: { eventId, from, to, booths: [{ rank, boothId, boothCode, totalReservationCount, ... }] }
const getPopularBooths = async (eventId, from, to) => {
  const response = await apiRequest(
    `/events/${eventId}/statistics/popular-booths?from=${from}&to=${to}`
  );
  return response.data;
};

// STAT-API-004: 행사 운영 통계 요약 (from, to: "YYYY-MM-DD" 필수)
// 응답: { eventId, from, to, totalReservationCount, totalNoShowCount, totalQrScanCount, boothSummaries }
const getEventOverview = async (eventId, from, to) => {
  const response = await apiRequest(
    `/events/${eventId}/statistics/overview?from=${from}&to=${to}`
  );
  return response.data;
};

export { getHourlyStatistics, getPreviousDayStatistics, getPopularBooths, getEventOverview };
