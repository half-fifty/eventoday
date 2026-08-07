BEGIN;

-- 기존 공개 테스트 데이터는 결제/티켓 참조 보존을 위해 삭제하지 않고 노출만 중단한다.
UPDATE events
SET status = 'SUSPENDED', updated_at = NOW()
WHERE status = 'PUBLISHED'
  AND name NOT IN (
    '2026 코리아빌드위크',
    '2026 한가위 명절선물전&소금박람회',
    '2026 대한민국 국제 병원 및 헬스테크 박람회',
    '제84회 프랜차이즈 창업박람회 2026',
    '2026 한국국제가구 및 인테리어산업대전 코펀',
    '2026 대한민국 안전산업박람회'
  );

INSERT INTO file_assets (uploaded_by, storage_key, original_name, mime_type, file_size, access_level, created_at)
VALUES
  (17, 'events/official-2026/korea-build.png', '2026-korea-build.png', 'image/png', 1182897, 'PUBLIC', NOW()),
  (17, 'events/official-2026/gift-fair.jpg', '2026-gift-fair.jpg', 'image/jpeg', 742609, 'PUBLIC', NOW()),
  (17, 'events/official-2026/khf.jpg', '2026-khf.jpg', 'image/jpeg', 1384780, 'PUBLIC', NOW()),
  (17, 'events/official-2026/franchise.jpg', '2026-franchise.jpg', 'image/jpeg', 128543, 'PUBLIC', NOW()),
  (17, 'events/official-2026/kofurn.png', '2026-kofurn.png', 'image/png', 19498, 'PUBLIC', NOW()),
  (17, 'events/official-2026/safety.jpg', '2026-safety.jpg', 'image/jpeg', 46835, 'PUBLIC', NOW())
ON CONFLICT (storage_key) DO NOTHING;

INSERT INTO events (
  organizer_organization_id, name, event_type, short_description, description,
  venue_name, address, start_at, end_at, ticket_sales_start_at, ticket_sales_end_at,
  ticket_price, ticket_total_quantity, ticket_sold_quantity, ticket_purchase_limit,
  representative_file_id, status, booth_recruitment_enabled, venue_map_enabled,
  booth_reservation_enabled, no_show_grace_minutes, published_at, created_at, updated_at,
  postal_code, address_detail, latitude, longitude, contact_email, contact_phone, region_code
)
SELECT 6, seed.name, seed.event_type, seed.short_description, seed.description,
  seed.venue_name, seed.address, seed.start_at, seed.end_at,
  '2026-08-01 00:00:00+09', seed.start_at, seed.ticket_price, 5000, 0, 4,
  fa.id, 'PUBLISHED', false, false, false, 10, NOW(), NOW(), NOW(),
  seed.postal_code, seed.address_detail, seed.latitude, seed.longitude,
  seed.contact_email, seed.contact_phone, seed.region_code
FROM (VALUES
  ('2026 코리아빌드위크', 'EXPO',
   '건설·건축·인테리어 산업의 최신 기술과 브랜드를 한자리에서 만나는 전문 전시회',
   '국내외 건설·건축·인테리어 기업이 참가해 최신 자재와 기술, 공간 솔루션을 소개하는 대규모 산업 전시회입니다. 전시와 함께 업계 관계자를 위한 네트워킹과 다양한 전문 프로그램이 운영됩니다.',
   '코엑스', '서울특별시 강남구 영동대로 513', '2026-08-05 10:00:00+09'::timestamptz, '2026-08-08 18:00:00+09'::timestamptz,
   20000::numeric, '06164', '전관', 37.511683::numeric, 127.059151::numeric, 'info@koreabuild.co.kr', '1600-5340', 'SEOUL', 'events/official-2026/korea-build.png'),
  ('2026 한가위 명절선물전&소금박람회', 'EXHIBITION',
   '추석 선물과 지역 특산품, 소금 산업 상품을 비교하고 만나는 명절선물 전문 전시회',
   '추석을 앞두고 식음료, 주류, 농수축산물, 전통상품, 건강상품과 생활용품을 소개합니다. 기업과 단체의 선물 구매 상담부터 일반 관람객의 합리적인 상품 탐색까지 가능한 전시회입니다.',
   '코엑스 D홀', '서울특별시 강남구 영동대로 513', '2026-08-14 11:00:00+09'::timestamptz, '2026-08-16 17:00:00+09'::timestamptz,
   10000::numeric, '06164', 'D홀', 37.511683::numeric, 127.059151::numeric, 'ex2@fsnews.co.kr', '02-515-4855', 'SEOUL', 'events/official-2026/gift-fair.jpg'),
  ('2026 대한민국 국제 병원 및 헬스테크 박람회', 'EXPO',
   '병원·의료산업의 디지털 전환과 최신 헬스테크를 연결하는 전문 비즈니스 전시회',
   '병원과 의료산업의 경쟁력 강화와 글로벌 진출을 위해 의료기술과 헬스테크 솔루션을 선보입니다. 특별전, 실무 중심 세미나와 컨퍼런스를 통해 최신 산업 정보를 공유합니다.',
   '코엑스 C·D홀', '서울특별시 강남구 영동대로 513', '2026-08-19 10:00:00+09'::timestamptz, '2026-08-21 17:00:00+09'::timestamptz,
   0::numeric, '06164', 'C·D홀', 37.511683::numeric, 127.059151::numeric, 'khf@esgroup.net', '02-6121-6363', 'SEOUL', 'events/official-2026/khf.jpg'),
  ('제84회 프랜차이즈 창업박람회 2026', 'EXPO',
   '다양한 프랜차이즈 브랜드와 예비 창업자가 직접 상담하는 국내 대표 창업 박람회',
   '외식, 카페, 판매와 서비스 분야의 프랜차이즈 본사가 참가해 창업 비용과 가맹 조건, 운영 정보를 소개합니다. 예비 창업자는 여러 브랜드를 비교하고 담당자와 상담할 수 있습니다.',
   '코엑스 D홀', '서울특별시 강남구 영동대로 513', '2026-08-25 10:00:00+09'::timestamptz, '2026-08-27 16:30:00+09'::timestamptz,
   15000::numeric, '06164', 'D홀', 37.511683::numeric, 127.059151::numeric, 'fran@world-expo.co.kr', '02-557-0648', 'SEOUL', 'events/official-2026/franchise.jpg'),
  ('2026 한국국제가구 및 인테리어산업대전 코펀', 'EXHIBITION',
   '가구·인테리어와 목공 산업의 최신 제품 및 디자인을 소개하는 국제 전문 전시회',
   '국내외 가구와 인테리어 기업이 참여해 생활가구, 사무용 가구, 인테리어 소재와 목공기계를 선보입니다. 제조 산업과 디자인, 유통 관계자가 함께하는 비즈니스 전시회입니다.',
   '킨텍스 제2전시장', '경기도 고양시 일산서구 킨텍스로 217-59', '2026-08-27 10:00:00+09'::timestamptz, '2026-08-30 18:00:00+09'::timestamptz,
   10000::numeric, '10390', '7·8홀', 37.669176::numeric, 126.745929::numeric, 'kofurn@kffic.kr', '02-2215-8838', 'GYEONGGI', 'events/official-2026/kofurn.png'),
  ('2026 대한민국 안전산업박람회', 'EXPO',
   '재난·산업·생활 안전 분야의 우수 기술과 기업을 연결하는 국내 대표 안전산업 전시회',
   '안전산업 기업의 기술과 제품을 소개하고 공공기관 및 국내외 바이어와의 비즈니스 기회를 제공합니다. 전문 세미나, 컨퍼런스와 체험 프로그램을 통해 안전산업의 미래를 조망합니다.',
   '벡스코 제1전시장', '부산광역시 해운대구 APEC로 55', '2026-09-02 10:00:00+09'::timestamptz, '2026-09-04 17:00:00+09'::timestamptz,
   0::numeric, '48060', '제1전시장', 35.168917::numeric, 129.136792::numeric, 'ksafetyexpo@bexco.co.kr', '051-740-7485', 'BUSAN', 'events/official-2026/safety.jpg')
) AS seed(name, event_type, short_description, description, venue_name, address, start_at, end_at,
          ticket_price, postal_code, address_detail, latitude, longitude, contact_email, contact_phone,
          region_code, storage_key)
JOIN file_assets fa ON fa.storage_key = seed.storage_key
WHERE NOT EXISTS (SELECT 1 FROM events existing WHERE existing.name = seed.name);

INSERT INTO event_exhibit_categories (event_id, category_id)
SELECT e.id, c.id
FROM events e
JOIN (VALUES
  ('2026 코리아빌드위크', 'CONSTRUCTION_ARCHITECTURE_INTERIOR'),
  ('2026 한가위 명절선물전&소금박람회', 'AGRI_FOOD'),
  ('2026 한가위 명절선물전&소금박람회', 'HOUSEHOLD_GIFTS'),
  ('2026 대한민국 국제 병원 및 헬스테크 박람회', 'HEALTH_MEDICAL_OPTICS_PRECISION'),
  ('제84회 프랜차이즈 창업박람회 2026', 'FINANCE_REAL_ESTATE_PROFESSIONAL'),
  ('2026 한국국제가구 및 인테리어산업대전 코펀', 'CONSTRUCTION_ARCHITECTURE_INTERIOR'),
  ('2026 한국국제가구 및 인테리어산업대전 코펀', 'HOUSEHOLD_GIFTS'),
  ('2026 대한민국 안전산업박람회', 'PUBLIC_DEFENSE')
) AS mapping(event_name, category_code) ON mapping.event_name = e.name
JOIN exhibit_categories c ON c.code = mapping.category_code
ON CONFLICT DO NOTHING;

COMMIT;
