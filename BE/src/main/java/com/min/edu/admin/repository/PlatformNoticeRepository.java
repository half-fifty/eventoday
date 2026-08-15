package com.min.edu.admin.repository;

import com.min.edu.admin.domain.PlatformNotice;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformNoticeRepository extends JpaRepository<PlatformNotice, Long> {

    /**
     * 공지 목록 페이지 조회 (검색어 없음).
     * 정렬은 호출부가 Pageable에 담아 전달한다 — 검색 조회와 정렬 기준을 한 곳에서 관리하기 위함.
     */
    Page<PlatformNotice> findAllBy(Pageable pageable);

    /**
     * 제목 또는 본문에 검색어가 포함된 공지 페이지 조회.
     *
     * 파라미터를 하나로 합치면 검색어가 null일 때 PostgreSQL이 타입을 추론하지 못하는 문제가 있어,
     * 검색어 유무에 따라 위 findAllBy와 메서드를 나눠 사용한다.
     * 본문은 정제된 HTML이므로 태그명이 검색에 걸릴 수 있다. 실사용에 지장이 없는 수준이라 그대로 둔다.
     */
    Page<PlatformNotice> findByTitleContainingIgnoreCaseOrContentContainingIgnoreCase(
            String titleKeyword, String contentKeyword, Pageable pageable);
}
