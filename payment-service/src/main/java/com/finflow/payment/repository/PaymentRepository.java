package com.finflow.payment.repository;

import com.finflow.payment.model.Payment;
import com.finflow.payment.model.PaymentStatus;
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
public interface PaymentRepository extends JpaRepository<Payment, String> {

    Optional<Payment> findByTransactionId(String transactionId);

    boolean existsByTransactionId(String transactionId);

    List<Payment> findBySenderAccountIdOrderByCreatedAtDesc(String accountId);

    List<Payment> findBySenderAccountIdAndStatusOrderByCreatedAtDesc(
            String accountId, PaymentStatus status);

    Page<Payment> findBySenderAccountId(String accountId, Pageable pageable);

    @Query(
            "SELECT SUM(p.amount) FROM Payment p "
                    + "WHERE p.senderAccountId = :accountId "
                    + "AND p.status = com.finflow.payment.model.PaymentStatus.COMPLETED")
    Optional<BigDecimal> sumCompletedPaymentsByAccount(@Param("accountId") String accountId);

    long countBySenderAccountId(String accountId);

    long countBySenderAccountIdAndStatus(String accountId, PaymentStatus status);
}
