package com.tripplanning.trip;

import com.tripplanning.accommodation.AccomEntity;
import com.tripplanning.comment.CommentEntity;
import com.tripplanning.transport.TransportEntity;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.user.UserEntity;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.JoinTable;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "trips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class TripEntity {

  public TripEntity(
      UserEntity user,
      String title,
      String destination,
      LocalDate startDate,
      String shortDescription,
      String longDescription) {
    setUser(user);
    this.title = title;
    this.destination = destination;
    this.startDate = startDate;
    this.shortDescription = shortDescription;
    this.longDescription = longDescription;
  }

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  @Column(name = "trip_id")
  private Long tripId;

  @ManyToOne(optional = false)
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

  @ManyToMany
  @JoinTable(
      name = "trip_transport",
      joinColumns = @JoinColumn(name = "trip_id"),
      inverseJoinColumns = @JoinColumn(name = "transport_id"))
  private List<TransportEntity> transports = new ArrayList<>();

  @ManyToMany
  @JoinTable(
      name = "trip_accommodation",
      joinColumns = @JoinColumn(name = "trip_id"),
      inverseJoinColumns = @JoinColumn(name = "accom_id"))
  private List<AccomEntity> acommodations = new ArrayList<>();

  @ManyToMany(mappedBy = "likedTrips")
  private List<UserEntity> likedByUsers = new ArrayList<>();

  @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<CommentEntity> comments = new ArrayList<>();

  @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TripLocationEntity> tripLocations = new ArrayList<>();

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

  @Lob
  @Column(nullable = false)
  private String longDescription;

  public void setUser(UserEntity user) {
    if (this.user != null) {
      this.user.getTrips().remove(this);
    }
    this.user = user;
    if (user != null) {
      user.getTrips().add(this);
    }
  }

  public void setTitle(String title) {
    this.title = title;
  }

  public void setDestination(String destination) {
    this.destination = destination;
  }

  public void setStartDate(LocalDate startDate) {
    this.startDate = startDate;
  }

  public void setShortDescription(String shortDescription) {
    this.shortDescription = shortDescription;
  }

  public void setLongDescription(String longDescription) {
    this.longDescription = longDescription;
  }
}
