package com.finflow.account.service;

import com.finflow.account.dto.TenantRequestDto;
import com.finflow.account.dto.TenantResponseDto;
import com.finflow.account.exception.TenantNotFoundException;
import com.finflow.account.mapper.AccountMapper;
import com.finflow.account.model.Tenant;
import com.finflow.account.model.TenantStatus;
import com.finflow.account.repository.TenantRepository;
import com.finflow.common.exception.ErrorCode;
import com.finflow.common.exception.FinFlowException;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Transactional
public class TenantService {

    private final TenantRepository tenantRepository;
    private final AccountMapper accountMapper;

    public TenantService(TenantRepository tenantRepository, AccountMapper accountMapper) {
        this.tenantRepository = tenantRepository;
        this.accountMapper = accountMapper;
    }

    @Transactional
    public TenantResponseDto createTenant(TenantRequestDto request) {
        if (tenantRepository.existsByName(request.name())) {
            throw new FinFlowException(
                    ErrorCode.VALIDATION_FAILED,
                    "Tenant name already taken",
                    HttpStatus.BAD_REQUEST);
        }
        if (tenantRepository.existsBySlug(request.slug())) { // A slug is the URL-friendly version of the tenant name — for example "acme-corp" or "techstartup-ltd". It must also be unique.
            throw new FinFlowException(
                    ErrorCode.VALIDATION_FAILED,
                    "Tenant slug already taken",
                    HttpStatus.BAD_REQUEST);
        }

        Tenant tenant = accountMapper.toEntity(request);
        tenant.setStatus(TenantStatus.ACTIVE);
        tenant = tenantRepository.save(tenant);
        log.info("Tenant created: {} ({})", tenant.getName(), tenant.getId());
        return accountMapper.toDto(tenant);
    }

    @Transactional(readOnly = true)
    public TenantResponseDto getTenantById(String tenantId) {
        Tenant tenant =
                tenantRepository.findById(tenantId)
                        .orElseThrow(() -> new TenantNotFoundException(tenantId));
        return accountMapper.toDto(tenant);
    }

    @Transactional(readOnly = true)
    public TenantResponseDto getTenantBySlug(String slug) {
        Tenant tenant =
                tenantRepository.findBySlug(slug)
                        .orElseThrow(() -> new TenantNotFoundException("slug: " + slug));
        return accountMapper.toDto(tenant);
    }

    @Transactional(readOnly = true)
    public List<TenantResponseDto> getAllTenants() {
        return tenantRepository.findAll().stream().map(accountMapper::toDto).toList();
    }
}
