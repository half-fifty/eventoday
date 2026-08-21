package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReviewReply;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothReviewReplyRepository extends JpaRepository<BoothReviewReply, Long> {

    Optional<BoothReviewReply> findByBoothReviewId(Long boothReviewId);

    List<BoothReviewReply> findByBoothReviewIdIn(Collection<Long> boothReviewIds);

    void deleteByBoothReviewId(Long boothReviewId);
}
