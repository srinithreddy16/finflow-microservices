package com.finflow.graphql.resolver;

import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import com.finflow.graphql.cache.GraphqlRedisCache;
import com.finflow.graphql.client.AccountServiceClient;
import com.finflow.graphql.client.dto.AccountDto;
import com.finflow.graphql.client.dto.TenantDto;
import java.time.Duration;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.graphql.data.method.annotation.Argument;
import org.springframework.graphql.data.method.annotation.QueryMapping;
import org.springframework.graphql.data.method.annotation.SchemaMapping;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Controller;

@Controller
@Slf4j
@RequiredArgsConstructor
public class AccountQueryResolver {

    private static final String TENANT_CACHE_KEY = "graphql:tenant:";

    private final AccountServiceClient accountServiceClient;
    private final GraphqlRedisCache graphqlRedisCache;

    @QueryMapping
    public Object account(@Argument String id, @AuthenticationPrincipal Jwt jwt) {
        Object cached = graphqlRedisCache.getAccount(id).orElse(null);
        if (cached != null) {
            return cached;
        }

        try {
            AccountDto account = accountServiceClient.getAccount(id);
            graphqlRedisCache.cacheAccount(id, account);
            log.debug("Account fetched: {}", id);
            return account;
        } catch (FinFlowException ex) {
            if (ex.getErrorCode() == ErrorCode.ACCOUNT_NOT_FOUND) {
                return null;
            }
            throw ex;
        }
    }

    @QueryMapping
    public Object accountByEmail(@Argument String email) {
        try {
            return accountServiceClient.getAccountByEmail(email);
        } catch (FinFlowException ex) {
            if (ex.getErrorCode() == ErrorCode.ACCOUNT_NOT_FOUND) {
                return null;
            }
            throw ex;
        }
    }

    @SchemaMapping(typeName = "Account", field = "tenant")
    public Object resolveTenant(AccountDto account) {
        if (account.tenantId() == null) {
            return null;
        }

        String cacheKey = TENANT_CACHE_KEY + account.tenantId();
        Object cached = graphqlRedisCache.get(cacheKey, Object.class).orElse(null);
        if (cached != null) {
            return cached;
        }

        try {
            TenantDto tenant = accountServiceClient.getTenant(account.tenantId());
            graphqlRedisCache.set(cacheKey, tenant, Duration.ofMinutes(5));
            return tenant;
        } catch (FinFlowException ex) {
            if (ex.getErrorCode() == ErrorCode.TENANT_NOT_FOUND) {
                return null;
            }
            throw ex;
        }
    }
}
