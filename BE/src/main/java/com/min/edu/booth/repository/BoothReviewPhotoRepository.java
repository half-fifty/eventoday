package com.min.edu.booth.repository;

import com.min.edu.booth.domain.BoothReviewPhoto;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface BoothReviewPhotoRepository extends JpaRepository<BoothReviewPhoto, Long> {

    List<BoothReviewPhoto> findByBoothReviewIdOrderBySortOrderAsc(Long boothReviewId);

    List<BoothReviewPhoto> findByBoothReviewIdInOrderBySortOrderAsc(Collection<Long> boothReviewIds);

    void deleteByBoothReviewId(Long boothReviewId);
}
