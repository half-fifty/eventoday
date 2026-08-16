package com.min.edu.admin.repository;

import com.min.edu.admin.domain.PlatformNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface PlatformNoticeRepository extends JpaRepository<PlatformNotice, Long> {

    /**
     * 공지 목록 페이지 조회 (검색어가 없으면 전체).
     *
     * 본문은 리치 텍스트 HTML이라 그대로 검색하면 화면에 보이지 않는 태그·속성이 걸린다.
     * (예: "table"을 검색하면 표가 들어간 공지가 전부 나오고, "p"는 모든 공지와 일치한다)
     * 그래서 태그를 제거한 텍스트를 대상으로 검색한다.
     *
     * 별도 검색용 컬럼을 두지 않고 조회 시점에 태그를 걷어내는 이유:
     * 부분 일치 검색(LIKE '%키워드%')은 어차피 인덱스를 타지 못해 전체 행을 훑는다.
     * 컬럼을 추가해도 스캔 범위는 같고, 마이그레이션과 기존 데이터 백필만 늘어난다.
     * 사이트 공지는 행 수가 많지 않아 이 방식으로 충분하다.
     * 공지가 크게 늘어 검색이 느려지면 검색용 컬럼 + GIN 인덱스로 옮기는 것을 고려한다.
     *
     * 정렬(상단 고정 우선, 최신순)을 쿼리에 직접 적었으므로 Pageable에는 Sort를 담지 않는다.
     * Sort를 담으면 Spring Data가 ORDER BY를 한 번 더 붙여 문법 오류가 난다.
     * keyword가 null일 때 PostgreSQL이 파라미터 타입을 추론하지 못하므로 cast로 명시한다.
     */
    @Query(value = """
            select * from platform_notices n
            where cast(:keyword as text) is null
               or n.title ilike concat('%', cast(:keyword as text), '%')
               or regexp_replace(coalesce(n.content, ''), '<[^>]*>', '', 'g')
                      ilike concat('%', cast(:keyword as text), '%')
            order by n.pinned desc, n.published_at desc, n.id desc
            """,
            countQuery = """
            select count(*) from platform_notices n
            where cast(:keyword as text) is null
               or n.title ilike concat('%', cast(:keyword as text), '%')
               or regexp_replace(coalesce(n.content, ''), '<[^>]*>', '', 'g')
                      ilike concat('%', cast(:keyword as text), '%')
            """,
            nativeQuery = true)
    Page<PlatformNotice> searchNotices(@Param("keyword") String keyword, Pageable pageable);
}
