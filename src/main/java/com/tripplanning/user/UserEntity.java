package com.tripplanning.user;

import java.util.ArrayList;
import java.util.List;

import com.tripplanning.trip.TripEntity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;


@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor

public class UserEntity {

  public UserEntity(String email, String name, String imagePath, String description) {
    this.email = email;
    this.name = name;
    this.imagePath = imagePath;
    this.description = description;
  }

 
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 320)
  private String email;

  @Column(nullable = false, unique = true, length = 255)
  @FullTextField(analyzer = "english")
  private String name;

  @Column(nullable = true, length = 500)
  private String imagePath;

  @Column(nullable = true, columnDefinition = "TEXT")
  private String description;

  /** Google OIDC subject; nullable for legacy users created before Google login. */
  @Column(name = "google_sub", nullable = true, unique = true, length = 255)
  private String googleSub;

  @Builder.Default
  @ManyToMany
  @JoinTable(
  name = "userLikesTrips", 
  joinColumns = @JoinColumn(name = "userId"),
  inverseJoinColumns = @JoinColumn(name = "tripId")
)
private List<TripEntity> likedTrips = new ArrayList<>();

  /** Inverse of {@link TripEntity#user}; required for Hibernate Search reindexing. */
  @Builder.Default
  @OneToMany(mappedBy = "user")
  private List<TripEntity> trips = new ArrayList<>();

  }

