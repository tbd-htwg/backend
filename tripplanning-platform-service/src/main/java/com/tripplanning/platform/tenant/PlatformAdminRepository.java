package com.tripplanning.platform.tenant;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformAdminRepository extends JpaRepository<PlatformAdminEntity, Long> {

  Optional<PlatformAdminEntity> findByEmailIgnoreCase(String email);

  boolean existsByEmailIgnoreCase(String email);
}
