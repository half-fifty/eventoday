ALTER TABLE events ADD COLUMN region_code VARCHAR(30);

UPDATE events SET region_code = CASE
    WHEN address LIKE '서울%' THEN 'SEOUL' WHEN address LIKE '부산%' THEN 'BUSAN'
    WHEN address LIKE '대구%' THEN 'DAEGU' WHEN address LIKE '인천%' THEN 'INCHEON'
    WHEN address LIKE '광주%' THEN 'GWANGJU' WHEN address LIKE '대전%' THEN 'DAEJEON'
    WHEN address LIKE '울산%' THEN 'ULSAN' WHEN address LIKE '세종%' THEN 'SEJONG'
    WHEN address LIKE '경기%' THEN 'GYEONGGI' WHEN address LIKE '강원%' THEN 'GANGWON'
    WHEN address LIKE '충청북도%' OR address LIKE '충북%' THEN 'CHUNGBUK'
    WHEN address LIKE '충청남도%' OR address LIKE '충남%' THEN 'CHUNGNAM'
    WHEN address LIKE '전북%' OR address LIKE '전라북도%' THEN 'JEONBUK'
    WHEN address LIKE '전남%' OR address LIKE '전라남도%' THEN 'JEONNAM'
    WHEN address LIKE '경상북도%' OR address LIKE '경북%' THEN 'GYEONGBUK'
    WHEN address LIKE '경상남도%' OR address LIKE '경남%' THEN 'GYEONGNAM'
    WHEN address LIKE '제주%' THEN 'JEJU' END;

CREATE INDEX idx_events_region_status_start ON events (region_code, status, start_at);

CREATE TABLE exhibit_categories (
    id BIGSERIAL PRIMARY KEY, code VARCHAR(50) NOT NULL UNIQUE, name VARCHAR(100) NOT NULL,
    display_order INTEGER NOT NULL, active BOOLEAN NOT NULL DEFAULT TRUE
);
CREATE TABLE event_exhibit_categories (
    event_id BIGINT NOT NULL REFERENCES events(id) ON DELETE CASCADE,
    category_id BIGINT NOT NULL REFERENCES exhibit_categories(id),
    PRIMARY KEY (event_id, category_id)
);
CREATE INDEX idx_event_exhibit_categories_category ON event_exhibit_categories (category_id, event_id);

INSERT INTO exhibit_categories(code,name,display_order) VALUES
('AGRI_FOOD','농축산/식음료',1),('ENERGY_ENVIRONMENT','에너지/환경',2),('TEXTILE_FASHION_JEWELRY','섬유/의류/쥬얼리',3),
('METAL_MACHINERY_EQUIPMENT','금속/기계/장비',4),('ELECTRIC_ELECTRONICS_ICT_BROADCAST','전기/전자/정보통신/방송',5),
('HEALTH_MEDICAL_OPTICS_PRECISION','보건/의료/광학/정밀',6),('CONSTRUCTION_ARCHITECTURE_INTERIOR','건설/건축/인테리어',7),
('TRANSPORT_SERVICE','운송장비/서비스',8),('HOUSEHOLD_GIFTS','가정용품/선물용품',9),('BEAUTY_COSMETICS','뷰티/화장품',10),
('FINANCE_REAL_ESTATE_PROFESSIONAL','금융/부동산/전문서비스',11),('PUBLIC_DEFENSE','공공/국방',12),('EDUCATION','교육',13),
('PREGNANCY_BIRTH_CHILDCARE','임신/출산/육아',14),('WEDDING','웨딩',15),('CULTURE_ART','문화/예술',16),('LEISURE_TOURISM_SPORTS','레저/관광/스포츠',17);
