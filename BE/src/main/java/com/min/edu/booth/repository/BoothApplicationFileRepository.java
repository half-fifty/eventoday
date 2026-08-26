package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothApplicationFile;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.List;

public interface BoothApplicationFileRepository extends JpaRepository<BoothApplicationFile, Long> {
    // 신청 ID로 첨부파일 목록 조회
    List<BoothApplicationFile> findAllByBoothApplicationId(Long boothApplicationId);
}