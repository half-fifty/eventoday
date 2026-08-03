package com.min.edu.interest.repository;

import com.min.edu.booth.domain.BoothInterest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Optional;

@Repository
public interface BoothInterestRepository extends JpaRepository<BoothInterest, Long> {
    boolean existsByMemberIdAndBoothId(Long memberId, Long boothId);

    Optional<BoothInterest> findByMemberIdAndBoothId(Long memberId, Long boothId);

    List<BoothInterest> findAllByMemberId(Long memberId);
}