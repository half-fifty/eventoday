package com.min.edu.event.repository;

import com.min.edu.event.domain.ExhibitCategory;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ExhibitCategoryRepository extends JpaRepository<ExhibitCategory, Long> {
    List<ExhibitCategory> findAllByActiveTrueOrderByDisplayOrderAsc();
    List<ExhibitCategory> findAllByCodeInAndActiveTrue(Collection<String> codes);
}
