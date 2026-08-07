# 운영 시드 실행 전 확인사항

`seed_official_events_2026.sql`과 `seed_official_event_ads_2026.sql`은 파일 메타데이터만 DB에 등록합니다.
SQL 실행만으로 이미지가 S3에 업로드되지는 않습니다.

운영·공유 DB에 시드를 적용하기 전에 인프라 담당자가 다음 객체를
`expo-platform-files` 버킷에 같은 키로 업로드해야 합니다.

- `.event-seed-assets/*` → `events/official-2026/*`
- `FE/public/images/ads/*-hero.png` → `ads/2026/*`

업로드 후 다음 경로를 확인한 뒤 SQL을 실행합니다.

```powershell
aws s3 ls s3://expo-platform-files/events/official-2026/
aws s3 ls s3://expo-platform-files/ads/2026/
```

버킷은 공개할 필요가 없습니다. 백엔드가 다운로드용 Presigned URL을 발급합니다.
DB 시드와 S3 업로드 권한이 분리된 환경에서는 DB 담당자와 인프라 담당자가
적용 순서를 함께 확인해야 합니다.
