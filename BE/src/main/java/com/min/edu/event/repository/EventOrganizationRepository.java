package com.min.edu.event.repository;

import com.min.edu.organization.domain.Organization;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface EventOrganizationRepository extends JpaRepository<Organization, Long> {
    List<Organization> findAllByIdInOrderByNameAsc(Collection<Long> ids);
}
