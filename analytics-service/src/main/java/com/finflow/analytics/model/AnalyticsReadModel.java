package com.finflow.analytics.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.UpdateTimestamp;

/**
 * CQRS read model for analytics, aggregated per account per day per currency. Built by consuming
 * Kafka events -- never written to directly from the command side. One row represents all activity
 * for one account on one day in one currency.
 */
@Entity
@Table(
        name = "analytics_read_model",
        uniqueConstraints = @UniqueConstraint(columnNames = {"account_id", "date", "currency"}))
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalyticsReadModel {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private String id;

    @Column(name = "account_id", nullable = false)
    private String accountId;

    @Column(nullable = false)
    private LocalDate date;

    @Column(nullable = false, length = 3)
    private String currency;

    @Column(name = "total_transactions", nullable = false)
    private long totalTransactions;

    @Column(name = "completed_transactions", nullable = false)
    private long completedTransactions;

    @Column(name = "failed_transactions", nullable = false)
    private long failedTransactions;

    @Column(name = "reversed_transactions", nullable = false)
    private long reversedTransactions;

    @Column(name = "total_volume", precision = 19, scale = 4)
    private BigDecimal totalVolume;

    @Column(name = "completed_volume", precision = 19, scale = 4)
    private BigDecimal completedVolume;

    @Column(name = "fraud_flags", nullable = false)
    private long fraudFlags;

    @Column(name = "avg_transaction_amount", precision = 19, scale = 4)
    private BigDecimal avgTransactionAmount;

    @UpdateTimestamp
    @Column(name = "last_updated")
    private Instant lastUpdated;

    public void incrementTransaction(BigDecimal amount, String status) {
        BigDecimal safeAmount = amount != null ? amount : BigDecimal.ZERO;
        BigDecimal currentTotalVolume = totalVolume != null ? totalVolume : BigDecimal.ZERO;
        BigDecimal currentCompletedVolume = completedVolume != null ? completedVolume : BigDecimal.ZERO;

        totalTransactions++;
        switch (status) {
            case "COMPLETED" -> {
                completedTransactions++;
                completedVolume = currentCompletedVolume.add(safeAmount);
            }
            case "FAILED" -> failedTransactions++;
            case "REVERSED" -> reversedTransactions++;
            default -> {
                // Ignore unknown statuses for status-specific counters.
            }
        }

        totalVolume = currentTotalVolume.add(safeAmount);
        avgTransactionAmount =
                totalVolume.divide(BigDecimal.valueOf(totalTransactions), 4, RoundingMode.HALF_UP);
    }
}
