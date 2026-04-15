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
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "users")
@Getter
@Setter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor

public class UserEntity {

  public UserEntity(String email, String name, String imageUrl, String description) {
    this.email = email;
    this.name = name;
    this.imageUrl = imageUrl;
    this.description = description;
  }

 
  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false, unique = true, length = 320)
  private String email;

  @Column(nullable = false, unique = true, length = 255)
  private String name;

  @Column(nullable = true, length = 500)
  private String imageUrl;

  @Column(nullable = true, columnDefinition = "TEXT")
  private String description;

  @Builder.Default
  @ManyToMany
  @JoinTable(
  name = "userLikesTrips", 
  joinColumns = @JoinColumn(name = "userId"),
  inverseJoinColumns = @JoinColumn(name = "tripId")
)
private List<TripEntity> likedTrips = new ArrayList<>();

  }

