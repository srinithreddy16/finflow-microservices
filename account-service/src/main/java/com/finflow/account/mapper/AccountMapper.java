package com.finflow.account.mapper;

import com.finflow.account.dto.AccountRequestDto;
import com.finflow.account.dto.AccountResponseDto;
import com.finflow.account.dto.TenantRequestDto;
import com.finflow.account.dto.TenantResponseDto;
import com.finflow.account.model.Account;
import com.finflow.account.model.Tenant;
import java.util.List;
import org.mapstruct.BeanMapping;
import org.mapstruct.Mapper;
import org.mapstruct.Mapping;

@Mapper(componentModel = "spring")
public interface AccountMapper {

    @Mapping(target = "status", expression = "java(account.getStatus() == null ? null : account.getStatus().name())")
    AccountResponseDto toDto(Account account);

    @BeanMapping(ignoreUnmappedSourceProperties = "accountId")
    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "PENDING")
    @Mapping(target = "kycVerified", constant = "false")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    @Mapping(target = "keycloakUserId", ignore = true)
    Account toEntity(AccountRequestDto dto);

    @Mapping(target = "status", expression = "java(tenant.getStatus() == null ? null : tenant.getStatus().name())")
    TenantResponseDto toDto(Tenant tenant);

    @Mapping(target = "id", ignore = true)
    @Mapping(target = "status", constant = "ACTIVE")
    @Mapping(target = "createdAt", ignore = true)
    @Mapping(target = "updatedAt", ignore = true)
    Tenant toEntity(TenantRequestDto dto);

    List<AccountResponseDto> toDtoList(List<Account> accounts);
}
