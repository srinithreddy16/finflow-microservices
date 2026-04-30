package com.finflow.analytics.repository;

import com.finflow.analytics.model.AnalyticsReadModel;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Read-side CQRS repository for analytics projections. This model is populated by event consumers
 * and queried by API handlers; it is never written to directly from command handlers.
 */
@Repository
public interface AnalyticsReadModelRepository extends JpaRepository<AnalyticsReadModel, String> {

    Optional<AnalyticsReadModel> findByAccountIdAndDateAndCurrency(
            String accountId, LocalDate date, String currency);

    List<AnalyticsReadModel> findByAccountIdOrderByDateDesc(String accountId);

    List<AnalyticsReadModel> findByAccountIdAndDateBetweenOrderByDateAsc(
            String accountId, LocalDate startDate, LocalDate endDate);

    List<AnalyticsReadModel> findByDateOrderByTotalVolumeDesc(LocalDate date);

    @Query(
            "SELECT SUM(a.completedVolume) FROM AnalyticsReadModel a "
                    + "WHERE a.accountId = :accountId "
                    + "AND a.date BETWEEN :startDate AND :endDate")
    Optional<BigDecimal> sumCompletedVolume(
            @Param("accountId") String accountId,
            @Param("startDate") LocalDate startDate,
            @Param("endDate") LocalDate endDate);

    @Query("SELECT SUM(a.fraudFlags) FROM AnalyticsReadModel a WHERE a.date = :date")
    long sumFraudFlagsByDate(@Param("date") LocalDate date);

    @Query(
            "SELECT a FROM AnalyticsReadModel a "
                    + "WHERE a.date >= :since "
                    + "ORDER BY a.totalVolume DESC")
    List<AnalyticsReadModel> findTopAccountsByVolumeSince(
            @Param("since") LocalDate since, Pageable pageable);
}
