package com.tripplanning.platform.tenant;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Entity
@Table(name = "platform_admins")
@Getter
@Setter
@NoArgsConstructor
public class PlatformAdminEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true)
  private String email;

  @Column(name = "created_at", nullable = false)
  private Instant createdAt;

  public PlatformAdminEntity(String email, Instant createdAt) {
    this.email = email;
    this.createdAt = createdAt;
  }
}
