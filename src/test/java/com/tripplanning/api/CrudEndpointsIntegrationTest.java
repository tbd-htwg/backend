package com.tripplanning.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanning.location.LocationEntity;
import com.tripplanning.location.LocationRepository;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationRepository;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
class CrudEndpointsIntegrationTest {

  private static final String TEST_IMAGE_URL = "https://example.com/avatar.png";
  private static final String TEST_DESCRIPTION = "Integration test user";

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TripRepository tripRepository;

  @Autowired
  private LocationRepository locationRepository;

  @Autowired
  private TripLocationRepository tripLocationRepository;

  private UserEntity testUser(String email, String name) {
    return new UserEntity(email, name, TEST_IMAGE_URL, TEST_DESCRIPTION);
  }

  @Test
  void putUserUpdatesAllFields() throws Exception {
    UserEntity user = userRepository.save(testUser("old@example.com", "Old Name"));
    String body = objectMapper.writeValueAsString(Map.of(
        "email", "new@example.com",
        "name", "New Name",
        "imageUrl", "https://example.com/new-avatar.png",
        "description", "Updated description for PUT"));

    mockMvc.perform(put("/v1/users/{id}", user.getUserId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("new@example.com"))
        .andExpect(jsonPath("$.name").value("New Name"))
        .andExpect(jsonPath("$.imageUrl").value("https://example.com/new-avatar.png"))
        .andExpect(jsonPath("$.description").value("Updated description for PUT"));
  }

  @Test
  void patchUserWithDuplicateEmailReturnsConflict() throws Exception {
    UserEntity existing = userRepository.save(testUser("taken@example.com", "Taken"));
    UserEntity toUpdate = userRepository.save(testUser("free@example.com", "Free"));
    String body = objectMapper.writeValueAsString(Map.of("email", existing.getEmail()));

    mockMvc.perform(patch("/v1/users/{id}", toUpdate.getUserId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("EmailAlreadyExists"));
  }

  @Test
  void patchUserWithoutFieldsReturnsBadRequest() throws Exception {
    UserEntity user = userRepository.save(testUser("user@example.com", "User"));
    String body = objectMapper.writeValueAsString(Map.of());

    mockMvc.perform(patch("/v1/users/{id}", user.getUserId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("InvalidInput"));
  }

  @Test
  void deleteUserRemovesUserAndTrips() throws Exception {
    UserEntity user = userRepository.save(testUser("delete-user@example.com", "Delete User"));
    tripRepository.save(new TripEntity(
        user,
        "Trip 1",
        "Berlin",
        LocalDate.parse("2026-05-01"),
        "Short",
        "Long description"
    ));

    mockMvc.perform(delete("/v1/users/{id}", user.getUserId()))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(userRepository.findById(user.getUserId())).isEmpty();
    org.assertj.core.api.Assertions.assertThat(tripRepository.findByUser_UserId(user.getUserId())).isEmpty();
  }

  @Test
  void putTripUpdatesAllFieldsIncludingUser() throws Exception {
    UserEntity ownerA = userRepository.save(testUser("owner-a@example.com", "Owner A"));
    UserEntity ownerB = userRepository.save(testUser("owner-b@example.com", "Owner B"));
    TripEntity trip = tripRepository.save(new TripEntity(
        ownerA,
        "Old Title",
        "Paris",
        LocalDate.parse("2026-06-01"),
        "Old short",
        "Old long"
    ));

    String body = objectMapper.writeValueAsString(Map.of(
        "userId", ownerB.getUserId(),
        "title", "New Title",
        "destination", "Rome",
        "startDate", "2026-07-15",
        "shortDescription", "New short",
        "longDescription", "New long"
    ));

    mockMvc.perform(put("/v1/trips/{id}", trip.getTripId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("New Title"))
        .andExpect(jsonPath("$.destination").value("Rome"));
  }

  @Test
  void patchTripWithoutFieldsReturnsBadRequest() throws Exception {
    UserEntity user = userRepository.save(testUser("trip-owner@example.com", "Trip Owner"));
    TripEntity trip = tripRepository.save(new TripEntity(
        user,
        "Trip",
        "Madrid",
        LocalDate.parse("2026-08-01"),
        "Short",
        "Long"
    ));
    String body = objectMapper.writeValueAsString(Map.of());

    mockMvc.perform(patch("/v1/trips/{id}", trip.getTripId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("InvalidInput"));
  }

  @Test
  void deleteTripReturnsNoContentAndRemovesTrip() throws Exception {
    UserEntity user = userRepository.save(testUser("delete-trip@example.com", "Trip User"));
    TripEntity trip = tripRepository.save(new TripEntity(
        user,
        "Trip To Delete",
        "Vienna",
        LocalDate.parse("2026-09-01"),
        "Short",
        "Long"
    ));

    mockMvc.perform(delete("/v1/trips/{id}", trip.getTripId()))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(tripRepository.findById(trip.getTripId())).isEmpty();
  }

  @Test
  void deleteTripRemovesStopsButKeepsSharedLocation() throws Exception {
    UserEntity user = userRepository.save(testUser("delete-trip-loc@example.com", "Trip User"));
    LocationEntity location = locationRepository.save(new LocationEntity("loc-catalog-" + UUID.randomUUID()));
    TripEntity trip = tripRepository.save(new TripEntity(
        user,
        "Trip With Stop",
        "Vienna",
        LocalDate.parse("2026-09-01"),
        "Short",
        "Long"
    ));
    tripLocationRepository.save(new TripLocationEntity(trip, location, null, null));
    long locationId = location.getLocation_id();
    Long tripId = trip.getTripId();

    mockMvc.perform(delete("/v1/trips/{id}", tripId))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(tripRepository.findById(tripId)).isEmpty();
    org.assertj.core.api.Assertions.assertThat(tripLocationRepository.findByTrip_TripId(tripId)).isEmpty();
    org.assertj.core.api.Assertions.assertThat(locationRepository.findById(locationId)).isPresent();
  }

  @Test
  void deleteTripWithUnknownIdReturnsNotFound() throws Exception {
    mockMvc.perform(delete("/v1/trips/{id}", 999999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NotFound"));
  }
}
