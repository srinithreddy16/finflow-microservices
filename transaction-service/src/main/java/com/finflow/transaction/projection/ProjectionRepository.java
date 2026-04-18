package com.finflow.transaction.projection;

import com.finflow.transaction.model.Transaction;
import com.finflow.transaction.model.TransactionStatus;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

/**
 * Read-side repository for the {@link Transaction} CQRS projection ( {@code transaction_projection}
 * table).
 * <p>
 * This reads ONLY from the projection table populated by replaying events from the EventStore.
 * Do not use it for inserts, updates, or deletes from command handlers — writes belong in
 * {@code TransactionProjectionUpdater} (or equivalent) driven by domain events.
 */
@Repository
public interface ProjectionRepository extends JpaRepository<Transaction, String> {

    List<Transaction> findByAccountIdOrderByCreatedAtDesc(String accountId);

    List<Transaction> findByAccountIdAndStatusOrderByCreatedAtDesc(
            String accountId, TransactionStatus status);

    Page<Transaction> findByAccountId(String accountId, Pageable pageable);

    Page<Transaction> findByAccountIdAndStatus(
            String accountId, TransactionStatus status, Pageable pageable);

    List<Transaction> findByStatusOrderByCreatedAtDesc(TransactionStatus status);

    long countByAccountIdAndStatus(String accountId, TransactionStatus status);

    long countByAccountId(String accountId);

    /**
     * Returns the most recently created projection row for the account, if any (used to resolve
     * display currency for summaries when all rows share the same account currency).
     */
    Optional<Transaction> findFirstByAccountIdOrderByCreatedAtDesc(String accountId);

    @Query(
            "SELECT SUM(t.amount) FROM Transaction t "
                    + "WHERE t.accountId = :accountId "
                    + "AND t.status = com.finflow.transaction.model.TransactionStatus.COMPLETED")
    Optional<BigDecimal> sumCompletedAmountByAccountId(@Param("accountId") String accountId);
}
