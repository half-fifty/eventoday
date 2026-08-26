import assert from "node:assert/strict";
import test from "node:test";
import { distanceInKilometers, formatEventDistance, hasEventCoordinates, rankNearbyEvents } from "./eventRecommendations.js";

test("행사 후보를 현재 행사에서 가까운 순서로 정렬한다", () => {
  const origin = { id: 1, latitude: 37.5117, longitude: 127.0592 };
  const result = rankNearbyEvents(origin, [
    { id: 1, latitude: 37.5117, longitude: 127.0592, startAt: "2026-08-01" },
    { id: 3, latitude: 37.5665, longitude: 126.978, startAt: "2026-08-01" },
    { id: 2, latitude: 37.513, longitude: 127.058, startAt: "2026-09-01" },
  ]);
  assert.deepEqual(result.map((event) => event.id), [2, 3]);
});

test("좌표가 없는 행사는 날짜순으로 보조 정렬한다", () => {
  const result = rankNearbyEvents({ id: 1 }, [
    { id: 2, startAt: "2026-09-01" },
    { id: 3, startAt: "2026-08-01" },
  ]);
  assert.deepEqual(result.map((event) => event.id), [3, 2]);
  assert.equal(formatEventDistance(result[0].distance), "거리 정보 없음");
  assert.equal(hasEventCoordinates({ latitude: null, longitude: null }), false);
});

test("거리 표시 형식을 m와 km로 구분한다", () => {
  assert.equal(formatEventDistance(0.42), "약 420m");
  assert.equal(formatEventDistance(3.1415), "약 3.1km");
  assert.ok(distanceInKilometers({ latitude: 37.5117, longitude: 127.0592 }, { latitude: 37.513, longitude: 127.058 }) < 1);
});
