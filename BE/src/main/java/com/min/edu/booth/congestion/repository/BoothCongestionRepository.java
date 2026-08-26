package com.min.edu.booth.congestion.repository;

import com.min.edu.booth.congestion.domain.BoothCongestion;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

// 조회(현재 혼잡도/인기 부스/한산한 부스)는 이제 QR 스캔 기반 실시간 계산(BoothCongestionService,
// BoothQrScanRepository)을 쓴다 — 이 테이블에 실제로 값을 채워 넣는 쓰기 경로가 없어 예전 조회
// 메서드들은 항상 빈 결과만 반환했다. save()는 향후 운영자 수동 조정 등을 위해 남겨둔다.
@Repository
public interface BoothCongestionRepository extends JpaRepository<BoothCongestion, Long> {
}