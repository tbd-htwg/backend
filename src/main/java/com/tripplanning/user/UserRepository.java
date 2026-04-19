package com.tripplanning.user;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.rest.core.annotation.RepositoryRestResource;

import java.util.Optional;

@RepositoryRestResource(path = "users", collectionResourceRel = "users")
public interface UserRepository extends JpaRepository<UserEntity, Long> {
  Optional<UserEntity> findByEmail(String email);

  /**
   * Indexed (unique) lookup used by the login flow so the frontend does not
   * have to load every user and filter client-side.
   */
  Optional<UserEntity> findByName(String name);
}

