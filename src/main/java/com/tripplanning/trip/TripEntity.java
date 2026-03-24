package com.tripplanning.trip;

import com.tripplanning.user.UserEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

@Entity
@Table(name = "trips")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
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
  @JoinColumn(name = "user_id", nullable = false)
  private UserEntity user;

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
    this.user = user;
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
