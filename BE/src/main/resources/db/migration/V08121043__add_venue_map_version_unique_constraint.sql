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
--
-- 두 DELETE와 ADD CONSTRAINT는 같은 Flyway 트랜잭션 안에서 실행되지만, 트랜잭션만으로는
-- 그 사이에 다른 트랜잭션이 커밋한 새 쓰기를 막지 못한다. 첫 DELETE 이후 두 번째 DELETE
-- 전에 새 booth_map_positions가 들어오면 venue_maps 삭제가 FK 위반으로 실패할 수 있고,
-- 정리 이후 제약 추가 전에 새 중복 venue_maps 행이 들어와도 마찬가지다. 두 테이블에
-- SHARE ROW EXCLUSIVE 잠금을 걸어 정리·제약 추가가 끝날 때까지 쓰기를 막는다.
LOCK TABLE venue_maps, booth_map_positions IN SHARE ROW EXCLUSIVE MODE;

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
