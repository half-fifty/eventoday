-- VenueMapService.create()가 (event_id, map_type, floor_name) 기준 최대 version에 +1을 해서
-- 새 버전을 만드는데, DB에 유니크 제약이 없어 두 요청이 거의 동시에 들어오면 같은 버전 번호로
-- 두 행이 생길 수 있다. 유니크 제약을 추가해 이런 경우 INSERT가 실패하도록 한다.
ALTER TABLE venue_maps
    ADD CONSTRAINT uk_venue_maps_event_type_floor_version
        UNIQUE (event_id, map_type, floor_name, version);
