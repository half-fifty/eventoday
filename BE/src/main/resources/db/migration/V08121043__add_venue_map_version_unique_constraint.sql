-- VenueMapService.create()가 (event_id, map_type, floor_name) 기준 최대 version에 +1을 해서
-- 새 버전을 만드는데, DB에 유니크 제약이 없어 두 요청이 거의 동시에 들어오면 같은 버전 번호로
-- 두 행이 생길 수 있다. 유니크 제약을 추가해 이런 경우 INSERT가 실패하도록 한다.
--
-- 제약을 걸기 전에 이미 같은 (event_id, map_type, floor_name, version) 조합의 중복 행이
-- 있으면 ADD CONSTRAINT 자체가 실패하므로, 각 조합에서 가장 먼저 만들어진 행(created_at 기준,
-- 동률이면 id 기준)만 남기고 정리한다. 삭제 대상 평면도를 참조하는 booth_map_positions가
-- 있으면 FK 위반이 나므로 좌표도 함께 지운다 (같은 조합의 중복 평면도는 좌표까지 정확히
-- 같다고 보장할 수 없어, 남기는 행 기준으로 좌표를 다시 등록해야 한다).
DELETE FROM booth_map_positions
    WHERE venue_map_id IN (
        SELECT dup.id
        FROM venue_maps dup
        JOIN venue_maps keep
            ON dup.event_id = keep.event_id
           AND dup.map_type = keep.map_type
           AND dup.floor_name = keep.floor_name
           AND dup.version = keep.version
        WHERE (dup.created_at, dup.id) > (keep.created_at, keep.id)
    );

DELETE FROM venue_maps dup
    USING venue_maps keep
    WHERE dup.event_id = keep.event_id
      AND dup.map_type = keep.map_type
      AND dup.floor_name = keep.floor_name
      AND dup.version = keep.version
      AND (dup.created_at, dup.id) > (keep.created_at, keep.id);

ALTER TABLE venue_maps
    ADD CONSTRAINT uk_venue_maps_event_type_floor_version
        UNIQUE (event_id, map_type, floor_name, version);
