package com.min.edu.funnel.repository;

import java.time.LocalDate;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.min.edu.funnel.domain.FunnelDiagnosisReport;

public interface FunnelDiagnosisReportRepository extends JpaRepository<FunnelDiagnosisReport, Long> {

    Optional<FunnelDiagnosisReport> findByEventIdAndReportDate(Long eventId, LocalDate reportDate);
}
