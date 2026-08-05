package com.min.edu.booth.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.booth.domain.BoothMapPosition;

public interface BoothMapPositionRepository extends JpaRepository<BoothMapPosition, Long> {

    List<BoothMapPosition> findByVenueMapId(Long venueMapId);

    void deleteByVenueMapId(Long venueMapId);
}
