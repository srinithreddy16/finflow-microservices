package com.finflow.account.controller;

import com.finflow.account.dto.AccountRequestDto;
import com.finflow.account.dto.AccountResponseDto;
import com.finflow.account.dto.AccountStatusUpdateDto;
import com.finflow.account.service.AccountService;
import com.finflow.common.util.IdGenerator;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@Slf4j
@RestController
@RequiredArgsConstructor  //Lombok annotation for constructor injection(AccountService) that is why we did not use this.accountservice=accountservice
@RequestMapping("/api/accounts")
@Tag(name = "Accounts")  // for segregation all Account APIs by OpenAPI UI
public class AccountController {

    private static final String CORRELATION_HEADER = "X-Correlation-Id";

    private final AccountService accountService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public AccountResponseDto createAccount(
            @RequestBody @Valid AccountRequestDto request,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationId) {
        return accountService.createAccount(request, resolveCorrelationId(correlationId));
    }

    @GetMapping("/{id}")
    public AccountResponseDto getAccountById(@PathVariable String id) {
        return accountService.getAccountById(id);
    }

    @GetMapping("/email/{email}")
    public AccountResponseDto getAccountByEmail(@PathVariable String email) {
        return accountService.getAccountByEmail(email);
    }

    @GetMapping("/tenant/{tenantId}")
    public List<AccountResponseDto> getAccountsByTenant(@PathVariable String tenantId) {
        return accountService.getAccountsByTenant(tenantId);
    }

    @PatchMapping("/{id}/status")
    public AccountResponseDto updateAccountStatus(
            @PathVariable String id,
            @RequestBody @Valid AccountStatusUpdateDto request,
            @RequestHeader(value = CORRELATION_HEADER, required = false) String correlationId) {
        return accountService.updateAccountStatus(id, request, resolveCorrelationId(correlationId));
    }

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void deleteAccount(@PathVariable String id) {
        accountService.deleteAccount(id);
    }

    private static String resolveCorrelationId(String correlationId) {
        return StringUtils.hasText(correlationId) ? correlationId : IdGenerator.correlationId();
    }
}
