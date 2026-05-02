package com.tripplanning.trip;

import com.tripplanning.accommodation.AccomEntity;
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
import jakarta.persistence.ManyToMany;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
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

import org.hibernate.search.mapper.pojo.mapping.definition.annotation.FullTextField;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.Indexed;
import org.hibernate.search.mapper.pojo.mapping.definition.annotation.IndexedEmbedded;

@Entity
@Table(name = "trips")
@Getter
@Setter
@Indexed
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

  @IndexedEmbedded
  @ManyToOne(optional = false)
  @JoinColumn(name = "userId", nullable = false)
  private UserEntity user;

  @IndexedEmbedded
  @Builder.Default
  @ManyToMany
  @JoinTable(name = "tripTransport", 
    joinColumns = @JoinColumn(name = "tripId"),
    inverseJoinColumns = @JoinColumn(name = "transportId")
  )
  private List<TransportEntity> transports = new ArrayList<>();

  @IndexedEmbedded
  @Builder.Default
  @ManyToMany
  @JoinTable(name = "tripAccommodation",
    joinColumns = @JoinColumn(name = "tripId"),
    inverseJoinColumns = @JoinColumn(name = "accomId")
  )
  private List<AccomEntity> accommodations = new ArrayList<>();

  @Builder.Default 
  @IndexedEmbedded
  @OneToMany(mappedBy = "trip", cascade = CascadeType.ALL, orphanRemoval = true)
  private List<TripLocationEntity> tripLocations = new ArrayList<>();

  @FullTextField(analyzer = "english")
  @Column(nullable = false, length = 255)
  private String title;

  @FullTextField(analyzer = "english")
  @Column(nullable = false, length = 255)
  private String destination;

  @NotNull
  @Column(nullable = false)
  private LocalDate startDate;

  @FullTextField(analyzer = "english")
  @NotBlank
  @Column(nullable = false, length = 80)
  private String shortDescription;

  @Column(nullable = false, columnDefinition = "TEXT")
  private String longDescription;

  public List<String> getLocationNames() {
    return tripLocations.stream()
        .map(tl -> tl.getLocation().getName())
        .toList();
}

}
