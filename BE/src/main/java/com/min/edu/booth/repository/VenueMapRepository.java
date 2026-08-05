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

    // 같은 mapType이라도 층(floorName)별로 각각 게시될 수 있어, 공개 조회는
    // 게시된 모든 층을 반환해야 한다 (버전 최고값 한 건만 리턴하면 다른 층이 가려짐).
    List<VenueMap> findByEventIdAndMapTypeAndStatusOrderByFloorNameAsc(
        Long eventId, VenueMapType mapType, VenueMapStatus status
    );

    Optional<VenueMap> findFirstByEventIdAndMapTypeAndFloorNameAndStatus(
        Long eventId, VenueMapType mapType, String floorName, VenueMapStatus status
    );
}
