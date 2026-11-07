package com.umis.dashboard.repository;

import com.umis.dashboard.model.DailyIngestionMetric;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for DailyIngestionMetric entities.
 */
@Repository
public interface DailyIngestionMetricRepository extends JpaRepository<DailyIngestionMetric, UUID> {

    List<DailyIngestionMetric> findByMetricDateBetweenOrderByMetricDateAsc(LocalDate startDate, LocalDate endDate);

    List<DailyIngestionMetric> findByTenantIdAndMetricDateBetweenOrderByMetricDateAsc(
            UUID tenantId, LocalDate startDate, LocalDate endDate);

    Optional<DailyIngestionMetric> findByMetricDateAndTenantIdAndMailboxIdAndProvider(
            LocalDate metricDate, UUID tenantId, UUID mailboxId, String provider);
}
