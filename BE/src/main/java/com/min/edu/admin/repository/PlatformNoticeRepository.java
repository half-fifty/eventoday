package com.min.edu.admin.repository;

import com.min.edu.admin.domain.PlatformNotice;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformNoticeRepository extends JpaRepository<PlatformNotice, Long> {

    /** 상단 고정 우선(pinned DESC), 최신순(publishedAt DESC) 정렬 — V08121040 인덱스와 동일한 순서 */
    List<PlatformNotice> findAllByOrderByPinnedDescPublishedAtDescIdDesc();
}
