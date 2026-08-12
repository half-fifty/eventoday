-- VenueMapService.create()가 (event_id, map_type, floor_name) 기준 최대 version에 +1을 해서
-- 새 버전을 만드는데, DB에 유니크 제약이 없어 두 요청이 거의 동시에 들어오면 같은 버전 번호로
-- 두 행이 생길 수 있다. 유니크 제약을 추가해 이런 경우 INSERT가 실패하도록 한다.
--
-- 제약을 걸기 전에 이미 같은 (event_id, map_type, floor_name, version) 조합의 중복 행이
-- 있으면 ADD CONSTRAINT 자체가 실패하므로 정리가 필요하다. 이때 "먼저 만들어진 행"이 아니라
-- "PUBLISHED 상태인 행"을 우선 보존해야 한다 - VenueMapService.publish()는 생성 순서와
-- 무관하게 나중에 만들어진 행을 게시할 수 있어서, 단순히 created_at 기준으로만 남기면 이미
-- 공개된 평면도와 그 좌표(booth_map_positions)가 삭제될 수 있다. PUBLISHED를 최우선으로,
-- 그다음 created_at, id 순으로 각 조합에서 한 행만 남긴다.
DELETE FROM booth_map_positions
    WHERE venue_map_id IN (
        SELECT id
        FROM (
            SELECT
                id,
                ROW_NUMBER() OVER (
                    PARTITION BY event_id, map_type, floor_name, version
                    ORDER BY
                        CASE WHEN status = 'PUBLISHED' THEN 0 ELSE 1 END,
                        created_at,
                        id
                ) AS rn
            FROM venue_maps
        ) ranked
        WHERE rn > 1
    );

DELETE FROM venue_maps
    WHERE id IN (
        SELECT id
        FROM (
            SELECT
                id,
                ROW_NUMBER() OVER (
                    PARTITION BY event_id, map_type, floor_name, version
                    ORDER BY
                        CASE WHEN status = 'PUBLISHED' THEN 0 ELSE 1 END,
                        created_at,
                        id
                ) AS rn
            FROM venue_maps
        ) ranked
        WHERE rn > 1
    );

ALTER TABLE venue_maps
    ADD CONSTRAINT uk_venue_maps_event_type_floor_version
        UNIQUE (event_id, map_type, floor_name, version);
