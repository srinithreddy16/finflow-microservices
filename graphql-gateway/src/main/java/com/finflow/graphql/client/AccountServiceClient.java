package com.finflow.graphql.client;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.graphql.client.dto.AccountDto;
import com.finflow.graphql.client.dto.TenantDto;
import java.util.List;
import java.util.Objects;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;
import reactor.core.publisher.Mono;

@Service
@Slf4j
public class AccountServiceClient {

    private final WebClient webClient;

    public AccountServiceClient(
            @Value("${service.account.url:http://localhost:8082}") String accountUrl,
            WebClient.Builder webClientBuilder) {
        this.webClient = WebClient.create(accountUrl);
    }

    public AccountDto getAccount(String accountId) {
        try {
            return webClient
                    .get()
                    .uri("/api/accounts/{accountId}", accountId)
                    .retrieve()
                    .onStatus(
                            s -> s.is4xxClientError(),
                            response ->
                                    Mono.error(
                                            FinFlowException.notFound(
                                                    ErrorCode.ACCOUNT_NOT_FOUND,
                                                    "Account not found: " + accountId)))
                    .bodyToMono(AccountDto.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Failed fetching account: {}", accountId, ex);
            throw FinFlowException.internalError(
                    ErrorCode.SERVICE_UNAVAILABLE, "Failed to fetch account", ex);
        }
    }

    public AccountDto getAccountByEmail(String email) {
        try {
            return webClient
                    .get()
                    .uri("/api/accounts/email/{email}", email)
                    .retrieve()
                    .onStatus(
                            s -> s.is4xxClientError(),
                            response ->
                                    Mono.error(
                                            FinFlowException.notFound(
                                                    ErrorCode.ACCOUNT_NOT_FOUND,
                                                    "Account not found for email: " + email)))
                    .bodyToMono(AccountDto.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Failed fetching account by email: {}", email, ex);
            throw FinFlowException.internalError(
                    ErrorCode.SERVICE_UNAVAILABLE, "Failed to fetch account by email", ex);
        }
    }

    public TenantDto getTenant(String tenantId) {
        try {
            return webClient
                    .get()
                    .uri("/api/tenants/{tenantId}", tenantId)
                    .retrieve()
                    .onStatus(
                            s -> s.is4xxClientError(),
                            response ->
                                    Mono.error(
                                            FinFlowException.notFound(
                                                    ErrorCode.TENANT_NOT_FOUND,
                                                    "Tenant not found: " + tenantId)))
                    .bodyToMono(TenantDto.class)
                    .block();
        } catch (WebClientResponseException ex) {
            log.error("Failed fetching tenant: {}", tenantId, ex);
            throw FinFlowException.internalError(
                    ErrorCode.SERVICE_UNAVAILABLE, "Failed to fetch tenant", ex);
        }
    }

    public List<AccountDto> getAccountsByIds(List<String> accountIds) {
        return accountIds.parallelStream()
                .map(
                        id -> {
                            try {
                                return getAccount(id);
                            } catch (Exception e) {
                                log.warn("Failed to fetch account in batch: {}", id, e);
                                return null;
                            }
                        })
                .filter(Objects::nonNull)
                .toList();
    }
}
