package com.finflow.payment.repository;

import com.finflow.payment.model.EntryType;
import com.finflow.payment.model.LedgerEntry;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface LedgerRepository extends JpaRepository<LedgerEntry, String> {

    List<LedgerEntry> findByPaymentIdOrderByCreatedAtAsc(String paymentId);

    List<LedgerEntry> findByAccountIdOrderByCreatedAtDesc(String accountId);

    Page<LedgerEntry> findByAccountId(String accountId, Pageable pageable);

    List<LedgerEntry> findByAccountIdAndEntryTypeOrderByCreatedAtDesc(
            String accountId, EntryType entryType);

    @Query(
            "SELECT SUM(CASE WHEN l.entryType = com.finflow.payment.model.EntryType.CREDIT "
                    + "THEN l.amount ELSE -l.amount END) "
                    + "FROM LedgerEntry l "
                    + "WHERE l.accountId = :accountId")
    Optional<BigDecimal> calculateBalance(@Param("accountId") String accountId);

    Optional<LedgerEntry> findFirstByAccountIdOrderByCreatedAtDesc(String accountId);
}
