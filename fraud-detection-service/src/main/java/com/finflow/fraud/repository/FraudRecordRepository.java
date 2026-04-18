package com.finflow.fraud.repository;

import com.finflow.fraud.model.FraudRecord;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Repository for {@link FraudRecord} entities.
 *
 * <p>Used by:
 *
 * <ul>
 *   <li>VelocityRule: to count recent transactions per account
 *   <li>GeolocationRule: to get the last known country for an account
 *   <li>FraudCheckGrpcService: to persist every check result
 *   <li>FraudEventConsumer: to update records from async Kafka events
 * </ul>
 */
@Repository
public interface FraudRecordRepository extends JpaRepository<FraudRecord, String> {

    Optional<FraudRecord> findByTransactionId(String transactionId);

    List<FraudRecord> findByAccountIdOrderByEvaluatedAtDesc(String accountId);

    long countByAccountIdAndEvaluatedAtAfter(String accountId, Instant after);

    Optional<FraudRecord> findTopByAccountIdOrderByEvaluatedAtDesc(String accountId);

    Page<FraudRecord> findByFlaggedTrueOrderByEvaluatedAtDesc(Pageable pageable);

    List<FraudRecord> findByAccountIdAndFlaggedTrue(String accountId);

    @Query(
            "SELECT COUNT(f) FROM FraudRecord f WHERE f.accountId = :accountId "
                    + "AND f.flagged = true AND f.evaluatedAt >= :since")
    long countFlaggedByAccountIdSince(
            @Param("accountId") String accountId, @Param("since") Instant since);
}
