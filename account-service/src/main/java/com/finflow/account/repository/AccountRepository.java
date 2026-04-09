package com.finflow.account.repository;

import com.finflow.account.model.Account;
import java.util.List;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AccountRepository extends JpaRepository<Account, String> {

    Optional<Account> findByEmail(String email);

    boolean existsByEmail(String email);

    List<Account> findAllByTenantId(String tenantId);

    Optional<Account> findByKeycloakUserId(String keycloakUserId);
}
