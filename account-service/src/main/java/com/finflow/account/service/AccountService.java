package com.finflow.account.service;

import com.finflow.account.dto.AccountRequestDto;
import com.finflow.account.dto.AccountResponseDto;
import com.finflow.account.dto.AccountStatusUpdateDto;
import com.finflow.account.exception.AccountNotFoundException;
import com.finflow.account.exception.DuplicateAccountException;
import com.finflow.account.kafka.AccountEventPublisher;
import com.finflow.account.keycloak.KeycloakAdminClient;
import com.finflow.account.mapper.AccountMapper;
import com.finflow.account.model.Account;
import com.finflow.account.model.AccountStatus;
import com.finflow.account.repository.AccountRepository;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Slf4j
@Service
@Transactional // A transaction ensures that a group of database operations are executed together as one unit. If any step fails, everything rolls back.
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper accountMapper;
    private final KeycloakAdminClient keycloakAdminClient;
    private final KycService kycService;
    private final AccountEventPublisher accountEventPublisher;

    public AccountService(
            AccountRepository accountRepository,
            AccountMapper accountMapper,
            KeycloakAdminClient keycloakAdminClient,
            KycService kycService,
            AccountEventPublisher accountEventPublisher) {
        this.accountRepository = accountRepository;
        this.accountMapper = accountMapper;
        this.keycloakAdminClient = keycloakAdminClient;
        this.kycService = kycService;
        this.accountEventPublisher = accountEventPublisher;
    }

    @Transactional
    public AccountResponseDto createAccount(AccountRequestDto request, String correlationId) {
        if (accountRepository.existsByEmail(request.email())) {
            throw new DuplicateAccountException(request.email());
        }

        Account account = accountMapper.toEntity(request);
        account.setStatus(AccountStatus.PENDING);
        account = accountRepository.save(account);

        KycService.KycCheckResult kycResult = kycService.performKycCheck(account);
        if (kycResult.passed()) {
            String keycloakUserId = keycloakAdminClient.createUser(request);
            account.setKeycloakUserId(keycloakUserId);
            account.setKycVerified(true);
            account.setStatus(AccountStatus.ACTIVE);
        } else {
            account.setStatus(AccountStatus.SUSPENDED);
            log.warn("KYC failed for account {}: {}", account.getId(), kycResult.reason());
        }

        account = accountRepository.save(account);
        accountEventPublisher.publishAccountCreated(account, correlationId);
        return accountMapper.toDto(account);
    }

    @Transactional(readOnly = true)
    public AccountResponseDto getAccountById(String accountId) {
        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));
        return accountMapper.toDto(account);
    }

    @Transactional(readOnly = true)
    public KycService.KycCheckResult performKycVerification(String accountId) {
        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));
        return kycService.performKycCheck(account);
    }

    @Transactional(readOnly = true)
    public AccountResponseDto getAccountByEmail(String email) {
        Account account =
                accountRepository.findByEmail(email)
                        .orElseThrow(() -> new AccountNotFoundException("email", email));
        return accountMapper.toDto(account);
    }

    @Transactional(readOnly = true)
    public List<AccountResponseDto> getAccountsByTenant(String tenantId) {
        return accountMapper.toDtoList(accountRepository.findAllByTenantId(tenantId));
    }

    @Transactional
    public AccountResponseDto updateAccountStatus(
            String accountId, AccountStatusUpdateDto request, String correlationId) {
        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));

        account.setStatus(request.status());

        if (StringUtils.hasText(account.getKeycloakUserId())) {
            if (request.status() == AccountStatus.SUSPENDED) {
                keycloakAdminClient.updateUserStatus(account.getKeycloakUserId(), false);
            } else if (request.status() == AccountStatus.ACTIVE) {
                keycloakAdminClient.updateUserStatus(account.getKeycloakUserId(), true);
            }
        }

        account = accountRepository.save(account);
        accountEventPublisher.publishAccountStatusChanged(
                account, request.status().name(), correlationId);
        return accountMapper.toDto(account);
    }

    @Transactional
    public void deleteAccount(String accountId) {
        Account account =
                accountRepository.findById(accountId)
                        .orElseThrow(() -> new AccountNotFoundException(accountId));

        if (StringUtils.hasText(account.getKeycloakUserId())) {
            keycloakAdminClient.deleteUser(account.getKeycloakUserId());
        }

        accountRepository.delete(account);
        log.info("Account deleted: {}", accountId);
    }
}
