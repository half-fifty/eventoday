package com.min.edu.advertisement.repository;

import com.min.edu.advertisement.domain.Advertisement;
import com.min.edu.advertisement.domain.AdvertisementStatus;
import java.time.OffsetDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import jakarta.persistence.LockModeType;

public interface AdvertisementRepository extends JpaRepository<Advertisement, Long>,
        JpaSpecificationExecutor<Advertisement> {
    Optional<Advertisement> findByPaymentOrderId(Long paymentOrderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select a from Advertisement a where a.id = :id")
    Optional<Advertisement> findByIdForUpdate(@Param("id") Long id);
    List<Advertisement> findAllByStatusInAndStartAtLessThanEqualAndEndAtGreaterThan(
            Collection<AdvertisementStatus> statuses, OffsetDateTime startAt, OffsetDateTime endAt);
    List<Advertisement> findAllByEventIdAndStatusInAndStartAtLessThanEqualAndEndAtGreaterThan(
            Long eventId, Collection<AdvertisementStatus> statuses,
            OffsetDateTime startAt, OffsetDateTime endAt);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Advertisement a set a.status = :active, a.updatedAt = :now "
            + "where a.status = :scheduled and a.startAt <= :now and a.endAt > :now")
    int activateScheduled(@Param("scheduled") AdvertisementStatus scheduled,
            @Param("active") AdvertisementStatus active, @Param("now") OffsetDateTime now);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("update Advertisement a set a.status = :ended, a.updatedAt = :now "
            + "where a.status in :statuses and a.endAt <= :now")
    int endExpired(@Param("statuses") Collection<AdvertisementStatus> statuses,
            @Param("ended") AdvertisementStatus ended, @Param("now") OffsetDateTime now);
}
