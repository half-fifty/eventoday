package com.min.edu.file.repository;

import com.min.edu.file.domain.FileAsset;
import org.springframework.data.jpa.repository.JpaRepository;

public interface FileAssetRepository extends JpaRepository<FileAsset, Long> {
}