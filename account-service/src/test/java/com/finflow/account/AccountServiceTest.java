package com.finflow.account;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import com.finflow.account.dto.AccountRequestDto;
import com.finflow.account.dto.AccountResponseDto;
import com.finflow.account.exception.AccountNotFoundException;
import com.finflow.account.exception.DuplicateAccountException;
import com.finflow.account.kafka.AccountEventPublisher;
import com.finflow.account.keycloak.KeycloakAdminClient;
import com.finflow.account.mapper.AccountMapper;
import com.finflow.account.model.Account;
import com.finflow.account.model.AccountStatus;
import com.finflow.account.repository.AccountRepository;
import com.finflow.account.service.AccountService;
import com.finflow.account.service.KycService;
import java.time.LocalDateTime;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AccountServiceTest {

    @Mock private AccountRepository accountRepository;
    @Mock private AccountMapper accountMapper;
    @Mock private KeycloakAdminClient keycloakAdminClient;
    @Mock private KycService kycService;
    @Mock private AccountEventPublisher accountEventPublisher;

    @InjectMocks private AccountService accountService;

    private final AccountRequestDto request =
            new AccountRequestDto(
                    "user@test.com", "First", "Last", "+10000000000", "tenant-1", null);

    @Test
    void createAccount_Success() {
        when(accountRepository.existsByEmail(request.email())).thenReturn(false);

        Account entity =
                Account.builder()
                        .email(request.email())
                        .firstName(request.firstName())
                        .lastName(request.lastName())
                        .phoneNumber(request.phoneNumber())
                        .tenantId(request.tenantId())
                        .build();
        when(accountMapper.toEntity(request)).thenReturn(entity);

        when(accountRepository.save(any(Account.class)))
                .thenAnswer(
                        inv -> {
                            Account a = inv.getArgument(0);
                            if (a.getId() == null) {
                                a.setId("acc-123");
                            }
                            return a;
                        });

        when(kycService.performKycCheck(any(Account.class)))
                .thenReturn(new KycService.KycCheckResult(true, "passed", 0));
        when(keycloakAdminClient.createUser(request)).thenReturn("keycloak-user-123");

        AccountResponseDto responseDto =
                new AccountResponseDto(
                        "acc-123",
                        request.email(),
                        request.firstName(),
                        request.lastName(),
                        request.phoneNumber(),
                        "ACTIVE",
                        request.tenantId(),
                        true,
                        LocalDateTime.now(),
                        LocalDateTime.now());
        when(accountMapper.toDto(any(Account.class))).thenReturn(responseDto);

        AccountResponseDto result = accountService.createAccount(request, "corr-123");

        verify(accountRepository, times(2)).save(any(Account.class));
        verify(keycloakAdminClient, times(1)).createUser(request);
        verify(accountEventPublisher, times(1))
                .publishAccountCreated(any(Account.class), eq("corr-123"));
        assertThat(result).isNotNull();
    }

    @Test
    void createAccount_ThrowsDuplicateException_WhenEmailExists() {
        when(accountRepository.existsByEmail(request.email())).thenReturn(true);

        assertThrows(
                DuplicateAccountException.class,
                () -> accountService.createAccount(request, "corr-123"));
    }

    @Test
    void getAccountById_ThrowsNotFoundException_WhenNotFound() {
        when(accountRepository.findById("non-existent-id")).thenReturn(java.util.Optional.empty());

        assertThrows(
                AccountNotFoundException.class, () -> accountService.getAccountById("non-existent-id"));
    }

    @Test
    void createAccount_SetsSuspendedStatus_WhenKycFails() {
        when(accountRepository.existsByEmail(request.email())).thenReturn(false);

        Account entity =
                Account.builder()
                        .email(request.email())
                        .firstName(request.firstName())
                        .lastName(request.lastName())
                        .phoneNumber(request.phoneNumber())
                        .tenantId(request.tenantId())
                        .build();
        when(accountMapper.toEntity(request)).thenReturn(entity);

        when(accountRepository.save(any(Account.class)))
                .thenAnswer(
                        inv -> {
                            Account a = inv.getArgument(0);
                            if (a.getId() == null) {
                                a.setId("acc-kyc-fail");
                            }
                            return a;
                        });

        when(kycService.performKycCheck(any(Account.class)))
                .thenReturn(new KycService.KycCheckResult(false, "failed", 90));

        AccountResponseDto responseDto =
                new AccountResponseDto(
                        "acc-kyc-fail",
                        request.email(),
                        request.firstName(),
                        request.lastName(),
                        request.phoneNumber(),
                        "SUSPENDED",
                        request.tenantId(),
                        false,
                        LocalDateTime.now(),
                        LocalDateTime.now());
        when(accountMapper.toDto(any(Account.class))).thenReturn(responseDto);

        accountService.createAccount(request, "corr-456");

        ArgumentCaptor<Account> captor = ArgumentCaptor.forClass(Account.class);
        verify(accountRepository, times(2)).save(captor.capture());
        List<Account> saved = captor.getAllValues();
        assertThat(saved.get(1).getStatus()).isEqualTo(AccountStatus.SUSPENDED);
        verify(keycloakAdminClient, times(0)).createUser(any());
    }
}
