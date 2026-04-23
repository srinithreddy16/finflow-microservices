package com.finflow.payment.service;

import com.finflow.payment.exception.InsufficientBalanceException;
import com.finflow.payment.model.EntryType;
import com.finflow.payment.model.LedgerEntry;
import com.finflow.payment.repository.LedgerRepository;
import java.math.BigDecimal;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Double-entry bookkeeping service. Every money movement creates two balanced entries ensuring the
 * accounting equation always holds: Assets = Liabilities + Equity.
 *
 * <p>The calculateBalance query sums all CREDITs minus all DEBITs for an account, giving the
 * current available balance.
 *
 * <p>IMPORTANT: Balance checks and entry creation must happen in the same {@code @Transactional}
 * scope to prevent race conditions.
 */
@Service
@Slf4j
@RequiredArgsConstructor
public class LedgerService {

    private final LedgerRepository ledgerRepository;

    @Transactional
    public List<LedgerEntry> createDoubleEntry(
            String paymentId,
            String senderAccountId,
            String receiverAccountId,
            BigDecimal amount,
            String currency,
            String description) {

        BigDecimal senderCurrentBalance =
                ledgerRepository.calculateBalance(senderAccountId).orElse(BigDecimal.ZERO);
        BigDecimal senderNewBalance = senderCurrentBalance.subtract(amount);

        if (senderNewBalance.compareTo(BigDecimal.ZERO) < 0) {
            throw new InsufficientBalanceException(senderAccountId, amount, senderCurrentBalance);
        }

        LedgerEntry debitEntry =
                LedgerEntry.builder()
                        .paymentId(paymentId)
                        .accountId(senderAccountId)
                        .entryType(EntryType.DEBIT)
                        .amount(amount)
                        .currency(currency)
                        .description("Payment sent: " + description)
                        .runningBalance(senderNewBalance)
                        .build();
        ledgerRepository.save(debitEntry);

        BigDecimal receiverCurrentBalance =
                ledgerRepository.calculateBalance(receiverAccountId).orElse(BigDecimal.ZERO);
        BigDecimal receiverNewBalance = receiverCurrentBalance.add(amount);

        LedgerEntry creditEntry =
                LedgerEntry.builder()
                        .paymentId(paymentId)
                        .accountId(receiverAccountId)
                        .entryType(EntryType.CREDIT)
                        .amount(amount)
                        .currency(currency)
                        .description("Payment received: " + description)
                        .runningBalance(receiverNewBalance)
                        .build();
        ledgerRepository.save(creditEntry);

        log.info(
                "Double entry created for payment {}: DEBIT {} from {}, CREDIT {} to {}",
                paymentId,
                amount,
                senderAccountId,
                amount,
                receiverAccountId);

        return List.of(debitEntry, creditEntry);
    }

    public BigDecimal getAccountBalance(String accountId) {
        BigDecimal balance =
                ledgerRepository.calculateBalance(accountId).orElse(BigDecimal.ZERO);
        log.debug("Balance for account {}: {}", accountId, balance);
        return balance;
    }

    public List<LedgerEntry> getLedgerHistory(String accountId) {
        return ledgerRepository.findByAccountIdOrderByCreatedAtDesc(accountId);
    }

    public void validateSufficientBalance(String accountId, BigDecimal requiredAmount) {
        BigDecimal balance =
                ledgerRepository.calculateBalance(accountId).orElse(BigDecimal.ZERO);
        if (balance.compareTo(requiredAmount) < 0) {
            throw new InsufficientBalanceException(accountId, requiredAmount, balance);
        }
        log.debug(
                "Balance validation passed for account {}: {} >= {}",
                accountId,
                balance,
                requiredAmount);
    }
}
