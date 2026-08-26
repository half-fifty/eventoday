package com.min.edu.file.repository;

import com.min.edu.file.domain.FileAsset;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import java.util.List;

public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {

    // ID 목록으로 존재하는 파일 수 일괄 조회
    @Query("SELECT COUNT(f) FROM FileAsset f WHERE f.id IN :ids")
    long countByIdIn(@Param("ids") List<Long> ids);

    // 파일이 존재하고 업로더(소유자)와 일치하는지 확인
    boolean existsByIdAndUploadedBy(Long id, Long uploadedBy);

    // ID 목록 중 업로더와 일치하는 파일 수 일괄 조회
    @Query("SELECT COUNT(f) FROM FileAsset f WHERE f.id IN :ids AND f.uploadedBy = :uploadedBy")
    long countByIdInAndUploadedBy(@Param("ids") List<Long> ids, @Param("uploadedBy") Long uploadedBy);

}