package com.finflow.payment;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.payment.exception.InsufficientBalanceException;
import com.finflow.payment.model.EntryType;
import com.finflow.payment.model.LedgerEntry;
import com.finflow.payment.repository.LedgerRepository;
import com.finflow.payment.service.LedgerService;
import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LedgerServiceTest {

    @Mock private LedgerRepository ledgerRepository;

    @InjectMocks private LedgerService ledgerService;

    @Test
    void createDoubleEntry_CreatesTwoEntries_WhenBalanceSufficient() {
        when(ledgerRepository.calculateBalance(eq("sender-001")))
                .thenReturn(Optional.of(BigDecimal.valueOf(1000)));
        when(ledgerRepository.calculateBalance(eq("receiver-001")))
                .thenReturn(Optional.of(BigDecimal.ZERO));

        List<LedgerEntry> result =
                ledgerService.createDoubleEntry(
                        "pay-001",
                        "sender-001",
                        "receiver-001",
                        BigDecimal.valueOf(500),
                        "USD",
                        "Test payment");

        ArgumentCaptor<LedgerEntry> captor = ArgumentCaptor.forClass(LedgerEntry.class);
        verify(ledgerRepository, times(2)).save(captor.capture());
        List<LedgerEntry> saved = captor.getAllValues();

        assertThat(saved).hasSize(2);
        LedgerEntry debit = saved.get(0);
        LedgerEntry credit = saved.get(1);

        assertThat(debit.getEntryType()).isEqualTo(EntryType.DEBIT);
        assertThat(debit.getAccountId()).isEqualTo("sender-001");
        assertThat(debit.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(debit.getRunningBalance()).isEqualByComparingTo(BigDecimal.valueOf(500));

        assertThat(credit.getEntryType()).isEqualTo(EntryType.CREDIT);
        assertThat(credit.getAccountId()).isEqualTo("receiver-001");
        assertThat(credit.getAmount()).isEqualByComparingTo(BigDecimal.valueOf(500));
        assertThat(credit.getRunningBalance()).isEqualByComparingTo(BigDecimal.valueOf(500));

        assertThat(result).containsExactly(debit, credit);
    }

    @Test
    void createDoubleEntry_ThrowsInsufficientBalance_WhenBalanceTooLow() {
        when(ledgerRepository.calculateBalance(eq("sender-001")))
                .thenReturn(Optional.of(BigDecimal.valueOf(100)));

        assertThatThrownBy(
                        () ->
                                ledgerService.createDoubleEntry(
                                        "pay-001",
                                        "sender-001",
                                        "receiver-001",
                                        BigDecimal.valueOf(500),
                                        "USD",
                                        "Test payment"))
                .isInstanceOf(InsufficientBalanceException.class);

        verify(ledgerRepository, never()).save(any());
    }

    @Test
    void getAccountBalance_ReturnsZero_WhenNoEntries() {
        when(ledgerRepository.calculateBalance("acc-001")).thenReturn(Optional.empty());

        assertThat(ledgerService.getAccountBalance("acc-001")).isEqualByComparingTo(BigDecimal.ZERO);
    }

    @Test
    void validateSufficientBalance_Passes_WhenBalanceIsEnough() {
        when(ledgerRepository.calculateBalance("acc-001"))
                .thenReturn(Optional.of(BigDecimal.valueOf(500)));

        ledgerService.validateSufficientBalance("acc-001", BigDecimal.valueOf(300));
    }
}
