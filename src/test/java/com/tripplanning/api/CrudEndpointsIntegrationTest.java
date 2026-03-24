package com.tripplanning.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.Map;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class CrudEndpointsIntegrationTest {

  @Autowired
  private MockMvc mockMvc;

  @Autowired
  private ObjectMapper objectMapper;

  @Autowired
  private UserRepository userRepository;

  @Autowired
  private TripRepository tripRepository;

  @Test
  void putUserUpdatesAllFields() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("old@example.com", "Old Name"));
    String body = objectMapper.writeValueAsString(Map.of(
        "email", "new@example.com",
        "name", "New Name"
    ));

    mockMvc.perform(put("/v1/users/{id}", user.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("new@example.com"))
        .andExpect(jsonPath("$.name").value("New Name"));
  }

  @Test
  void patchUserWithDuplicateEmailReturnsConflict() throws Exception {
    UserEntity existing = userRepository.save(new UserEntity("taken@example.com", "Taken"));
    UserEntity toUpdate = userRepository.save(new UserEntity("free@example.com", "Free"));
    String body = objectMapper.writeValueAsString(Map.of("email", existing.getEmail()));

    mockMvc.perform(patch("/v1/users/{id}", toUpdate.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("EmailAlreadyExists"));
  }

  @Test
  void patchUserWithoutFieldsReturnsBadRequest() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("user@example.com", "User"));
    String body = objectMapper.writeValueAsString(Map.of());

    mockMvc.perform(patch("/v1/users/{id}", user.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("InvalidInput"));
  }

  @Test
  void deleteUserRemovesUserAndTrips() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("delete-user@example.com", "Delete User"));
    tripRepository.save(new TripEntity(
        user,
        "Trip 1",
        "Berlin",
        LocalDate.parse("2026-05-01"),
        "Short",
        "Long description"
    ));

    mockMvc.perform(delete("/v1/users/{id}", user.getId()))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(userRepository.findById(user.getId())).isEmpty();
    org.assertj.core.api.Assertions.assertThat(tripRepository.findByUserId(user.getId())).isEmpty();
  }

  @Test
  void putTripUpdatesAllFieldsIncludingUser() throws Exception {
    UserEntity ownerA = userRepository.save(new UserEntity("owner-a@example.com", "Owner A"));
    UserEntity ownerB = userRepository.save(new UserEntity("owner-b@example.com", "Owner B"));
    TripEntity trip = tripRepository.save(new TripEntity(
        ownerA,
        "Old Title",
        "Paris",
        LocalDate.parse("2026-06-01"),
        "Old short",
        "Old long"
    ));

    String body = objectMapper.writeValueAsString(Map.of(
        "userId", ownerB.getId(),
        "title", "New Title",
        "destination", "Rome",
        "startDate", "2026-07-15",
        "shortDescription", "New short",
        "longDescription", "New long"
    ));

    mockMvc.perform(put("/v1/trips/{id}", trip.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("New Title"))
        .andExpect(jsonPath("$.destination").value("Rome"));
  }

  @Test
  void patchTripWithoutFieldsReturnsBadRequest() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("trip-owner@example.com", "Trip Owner"));
    TripEntity trip = tripRepository.save(new TripEntity(
        user,
        "Trip",
        "Madrid",
        LocalDate.parse("2026-08-01"),
        "Short",
        "Long"
    ));
    String body = objectMapper.writeValueAsString(Map.of());

    mockMvc.perform(patch("/v1/trips/{id}", trip.getId())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("InvalidInput"));
  }

  @Test
  void deleteTripReturnsNoContentAndRemovesTrip() throws Exception {
    UserEntity user = userRepository.save(new UserEntity("delete-trip@example.com", "Trip User"));
    TripEntity trip = tripRepository.save(new TripEntity(
        user,
        "Trip To Delete",
        "Vienna",
        LocalDate.parse("2026-09-01"),
        "Short",
        "Long"
    ));

    mockMvc.perform(delete("/v1/trips/{id}", trip.getId()))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(tripRepository.findById(trip.getId())).isEmpty();
  }

  @Test
  void deleteTripWithUnknownIdReturnsNotFound() throws Exception {
    mockMvc.perform(delete("/v1/trips/{id}", 999999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NotFound"));
  }
}
