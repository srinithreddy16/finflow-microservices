package com.finflow.account.controller;

import com.finflow.account.dto.TenantRequestDto;
import com.finflow.account.dto.TenantResponseDto;
import com.finflow.account.service.TenantService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/tenants")
public class TenantController {

    private final TenantService tenantService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TenantResponseDto createTenant(@RequestBody @Valid TenantRequestDto request) {
        return tenantService.createTenant(request);
    }

    @GetMapping("/{id}")
    public TenantResponseDto getTenantById(@PathVariable String id) {
        return tenantService.getTenantById(id);
    }

    @GetMapping("/slug/{slug}")
    public TenantResponseDto getTenantBySlug(@PathVariable String slug) {
        return tenantService.getTenantBySlug(slug);
    }

    @GetMapping
    public List<TenantResponseDto> getAllTenants() {
        return tenantService.getAllTenants();
    }
}
