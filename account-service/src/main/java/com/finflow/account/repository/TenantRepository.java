package com.finflow.account.repository;

import com.finflow.account.model.Tenant;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<Tenant, String> {

    Optional<Tenant> findBySlug(String slug);

    Optional<Tenant> findByOwnerEmail(String ownerEmail);

    boolean existsByName(String name);

    boolean existsBySlug(String slug);
}
