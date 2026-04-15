package com.tripplanning.trip;

import com.tripplanning.accommodation.AccomEntity;
import com.tripplanning.transport.TransportEntity;
import com.tripplanning.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.Setter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "trips")
@Getter
@Setter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
@Builder
public class TripEntity {
  
  public TripEntity(
      UserEntity user,
      String title,
      String destination,
      LocalDate startDate,
      String shortDescription,
      String longDescription) {
    this.user = user;
    this.title = title;
    this.destination = destination;
    this.startDate = startDate;
    this.shortDescription = shortDescription;
    this.longDescription = longDescription;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @ManyToOne(optional = false)
  @JoinColumn(name = "userId", nullable = false)
  private UserEntity user;

  @Builder.Default
  @ManyToMany
  @JoinTable(name = "tripTransport", 
    joinColumns = @JoinColumn(name = "tripId"),
    inverseJoinColumns = @JoinColumn(name = "transportId")
  )
  private List<TransportEntity> transports = new ArrayList<>();

  @Builder.Default
  @ManyToMany
  @JoinTable(name = "tripAccommodation",
    joinColumns = @JoinColumn(name = "tripId"),
    inverseJoinColumns = @JoinColumn(name = "accomId")
  )
  private List<AccomEntity> accommodations = new ArrayList<>();

  @Builder.Default
  @ManyToMany(mappedBy = "likedTrips")
  private List<UserEntity> likedByUsers = new ArrayList<>();

  @Column(nullable = false, length = 255)
  private String title;

  @Column(nullable = false, length = 255)
  private String destination;

  @NotNull
  @Column(nullable = false)
  private LocalDate startDate;

  @NotBlank
  @Column(nullable = false, length = 80)
  private String shortDescription;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String longDescription;

}
