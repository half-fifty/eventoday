package com.min.edu.booth.repository;

import com.min.edu.booth.domain.Booth;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface BoothRepository extends JpaRepository<Booth, Long> {

    /**
     * 동시 신청 충돌 방지를 위한 비관적 락 조회
     * TICKET_SOLD_OUT 처리 패턴과 동일 (비관적 락)
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT b FROM Booth b WHERE b.id = :id")
    Optional<Booth> findByIdWithLock(@Param("id") Long id);
}