BEGIN;

INSERT INTO file_assets (uploaded_by, storage_key, original_name, mime_type, file_size, access_level, created_at)
VALUES
  (17, 'ads/2026/korea-build-hero.png', 'korea-build-hero.png', 'image/png', 2010185, 'PUBLIC', NOW()),
  (17, 'ads/2026/khf-hero.png', 'khf-hero.png', 'image/png', 1693333, 'PUBLIC', NOW()),
  (17, 'ads/2026/kofurn-hero.png', 'kofurn-hero.png', 'image/png', 1917980, 'PUBLIC', NOW()),
  (17, 'ads/2026/safety-hero.png', 'safety-hero.png', 'image/png', 1662167, 'PUBLIC', NOW())
ON CONFLICT (storage_key) DO NOTHING;

INSERT INTO advertisements (
  event_id, booth_id, applicant_organization_id, payment_order_id, banner_file_id,
  ad_text, start_at, end_at, status, reviewed_by, rejection_reason,
  approved_at, created_at, updated_at
)
SELECT e.id, NULL, 6, NULL, f.id, campaign.ad_text,
       '2026-08-07 00:00:00+09'::timestamptz, e.end_at,
       'ACTIVE', 17, NULL, NOW(), NOW(), NOW()
FROM (VALUES
  ('2026 코리아빌드위크', '건축의 다음 장면을 만나다', 'ads/2026/korea-build-hero.png'),
  ('2026 대한민국 국제 병원 및 헬스테크 박람회', 'AI와 로보틱스가 만드는 지능형 병원', 'ads/2026/khf-hero.png'),
  ('2026 한국국제가구 및 인테리어산업대전 코펀', '가구와 공간 디자인의 새로운 기준', 'ads/2026/kofurn-hero.png'),
  ('2026 대한민국 안전산업박람회', '안전 기술이 만드는 더 나은 내일', 'ads/2026/safety-hero.png')
) AS campaign(event_name, ad_text, storage_key)
JOIN events e ON e.name = campaign.event_name
JOIN file_assets f ON f.storage_key = campaign.storage_key
WHERE NOT EXISTS (
  SELECT 1 FROM advertisements existing
  WHERE existing.event_id = e.id AND existing.banner_file_id = f.id
);

COMMIT;
