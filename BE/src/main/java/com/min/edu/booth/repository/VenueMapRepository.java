package com.min.edu.booth.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.booth.domain.VenueMap;
import com.min.edu.booth.domain.VenueMapStatus;
import com.min.edu.booth.domain.VenueMapType;

public interface VenueMapRepository extends JpaRepository<VenueMap, Long> {

    List<VenueMap> findByEventIdOrderByFloorNameAscVersionDesc(Long eventId);

    Optional<VenueMap> findByIdAndEventId(Long id, Long eventId);

    Optional<VenueMap> findFirstByEventIdAndMapTypeAndFloorNameOrderByVersionDesc(
        Long eventId, VenueMapType mapType, String floorName
    );

    Optional<VenueMap> findFirstByEventIdAndMapTypeAndStatusOrderByVersionDesc(
        Long eventId, VenueMapType mapType, VenueMapStatus status
    );

    Optional<VenueMap> findFirstByEventIdAndMapTypeAndFloorNameAndStatus(
        Long eventId, VenueMapType mapType, String floorName, VenueMapStatus status
    );
}
