import { apiRequest } from "./apiClient.js";

const json = (method, body) => ({
  method,
  headers: { "Content-Type": "application/json" },
  body: body === undefined ? undefined : JSON.stringify(body),
});

// 부스의 예약 시간대 전체 목록 (OPEN/CLOSED 모두, 공개).
const listReservationSlots = async (boothId) => {
  const response = await apiRequest(`/booths/${boothId}/reservation-slots`);
  return response ?? [];
};

// 운영자: 예약 시간대 생성.
const createReservationSlot = async (boothId, payload) =>
  apiRequest(`/booths/${boothId}/reservation-slots`, json("POST", payload));

// 운영자: 예약 시간대 수정 (시작/종료 시간, 정원).
const updateReservationSlot = async (boothId, slotId, payload) =>
  apiRequest(`/booths/${boothId}/reservation-slots/${slotId}`, json("PATCH", payload));

// 운영자: 예약 시간대 끄기(비활성화).
const closeReservationSlot = async (boothId, slotId) =>
  apiRequest(`/booths/${boothId}/reservation-slots/${slotId}/close`, json("PATCH"));

// 운영자: 예약 시간대 켜기(다시 활성화).
const reopenReservationSlot = async (boothId, slotId) =>
  apiRequest(`/booths/${boothId}/reservation-slots/${slotId}/open`, json("PATCH"));

// 운영자: 예약 시간대 삭제 (예약 이력이 있으면 실패 - 대신 끄기를 사용해야 함).
const deleteReservationSlot = async (boothId, slotId) =>
  apiRequest(`/booths/${boothId}/reservation-slots/${slotId}`, { method: "DELETE" });

// 로그인한 회원의 이 부스 예약 조회 (없으면 null).
const getMyReservation = async (boothId) => apiRequest(`/booths/${boothId}/reservations`);

// 운영자: 부스 예약자 전체 목록 (시간대별 예약 인원, 예약자 정보).
const listReservationsForManager = async (boothId) => {
  const response = await apiRequest(`/booths/${boothId}/reservations/admin`);
  return response ?? [];
};

// 방문객: 시간대 예약.
const createReservation = async (boothId, payload) =>
  apiRequest(`/booths/${boothId}/reservations`, json("POST", payload));

// 방문객: 예약 취소.
const cancelReservation = async (boothId, reservationId) =>
  apiRequest(`/booths/${boothId}/reservations/${reservationId}`, { method: "DELETE" });

// 운영자: 예약자 출석 수동 체크 (attended=true 방문 확인, false 노쇼 처리).
const markReservationAttendance = async (boothId, reservationId, attended) =>
  apiRequest(`/booths/${boothId}/reservations/${reservationId}/attendance`, json("PATCH", { attended }));

// 내 부스 예약 목록: 행사 전체에 걸쳐 내가 예약한 모든 부스 예약 (최신순, 페이징).
// ApiResponse 래핑 없이 Page를 그대로 반환한다.
const listMyReservations = async (params = {}) => {
  const query = new URLSearchParams(
    Object.fromEntries(Object.entries(params).filter(([, value]) => value !== "" && value != null))
  );
  const queryString = query.toString();
  return apiRequest(`/members/me/booth-reservations${queryString ? `?${queryString}` : ""}`);
};

export {
  listReservationSlots,
  createReservationSlot,
  updateReservationSlot,
  closeReservationSlot,
  reopenReservationSlot,
  deleteReservationSlot,
  getMyReservation,
  listReservationsForManager,
  markReservationAttendance,
  createReservation,
  cancelReservation,
  listMyReservations,
};
