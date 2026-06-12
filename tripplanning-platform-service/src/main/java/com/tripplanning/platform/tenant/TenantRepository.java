package com.tripplanning.platform.tenant;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface TenantRepository extends JpaRepository<TenantEntity, String> {

  Optional<TenantEntity> findBySlug(String slug);

  boolean existsBySlug(String slug);

  List<TenantEntity> findByTierOrderByCreatedAtDesc(TenantTier tier);

  List<TenantEntity> findAllByOrderByCreatedAtDesc();
}
