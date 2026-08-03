package com.min.edu.advertisement.repository;

import com.min.edu.advertisement.domain.Advertisement;
import com.min.edu.advertisement.domain.AdvertisementStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long>,
        JpaSpecificationExecutor<Advertisement> {
    List<Advertisement> findAllByStatusInAndStartAtLessThanEqualAndEndAtGreaterThanEqual(
            Collection<AdvertisementStatus> statuses, OffsetDateTime startAt, OffsetDateTime endAt);
    List<Advertisement> findAllByStatusAndStartAtLessThanEqual(
            AdvertisementStatus status, OffsetDateTime startAt);
    List<Advertisement> findAllByStatusInAndEndAtLessThanEqual(
            Collection<AdvertisementStatus> statuses, OffsetDateTime endAt);
}
