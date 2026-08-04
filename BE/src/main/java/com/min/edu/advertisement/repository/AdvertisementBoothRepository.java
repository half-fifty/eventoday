package com.min.edu.advertisement.repository;

import com.min.edu.booth.domain.Booth;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AdvertisementBoothRepository extends JpaRepository<Booth, Long> {
    List<Booth> findAllByEventIdAndAssignedOrganizationIdIsNotNull(Long eventId);
}
