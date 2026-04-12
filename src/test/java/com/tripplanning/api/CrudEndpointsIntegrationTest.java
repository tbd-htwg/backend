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
    UserEntity user = UserEntity.builder()
        .email("old@example.com")
        .name("Old Name")
        .build();
    userRepository.save(user);

    String body = objectMapper.writeValueAsString(Map.of(
        "email", "new@example.com",
        "name", "New Name"
    ));

    mockMvc.perform(put("/v1/users/{id}", user.getUser_id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("new@example.com"))
        .andExpect(jsonPath("$.name").value("New Name"));
  }

  @Test
  void patchUserWithDuplicateEmailReturnsConflict() throws Exception {
    UserEntity existing = UserEntity.builder()
        .email("taken@example.com")
        .name("Taken")
        .build();
    userRepository.save(existing);

    UserEntity toUpdate = UserEntity.builder()
        .email("free@example.com")
        .name("Free")
        .build();
    userRepository.save(toUpdate);

    String body = objectMapper.writeValueAsString(Map.of("email", existing.getEmail()));

    mockMvc.perform(patch("/v1/users/{id}", toUpdate.getUser_id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isConflict())
        .andExpect(jsonPath("$.error").value("EmailAlreadyExists"));
  }

  @Test
  void patchUserWithoutFieldsReturnsBadRequest() throws Exception {
    UserEntity user = UserEntity.builder()
        .email("user@example.com")
        .name("User")
        .build();
    userRepository.save(user);

    String body = objectMapper.writeValueAsString(Map.of());

    mockMvc.perform(patch("/v1/users/{id}", user.getUser_id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("InvalidInput"));
  }

  @Test
  void deleteUserRemovesUserAndTrips() throws Exception {
    UserEntity user = UserEntity.builder()
        .email("delete-user@example.com")
        .name("Delete User")
        .build();
    userRepository.save(user);

    TripEntity trip = TripEntity.builder()
        .user(user)
        .title("Trip 1")
        .destination("Berlin")
        .startDate(LocalDate.parse("2026-05-01"))
        .shortDescription("Short")
        .longDescription("Long")
        .build();
    tripRepository.save(trip);
        
    mockMvc.perform(delete("/v1/users/{id}", user.getUser_id()))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(userRepository.findById(user.getUser_id())).isEmpty();
    org.assertj.core.api.Assertions.assertThat(tripRepository.findByUserId(user.getUser_id())).isEmpty();
  }

  @Test
  void putTripUpdatesAllFieldsIncludingUser() throws Exception {
    UserEntity ownerA = UserEntity.builder()
        .email("owner-a@example.com")
        .name("Owner A")
        .build();
    userRepository.save(ownerA);

     UserEntity ownerB = UserEntity.builder()
        .email("owner-b@example.com")
        .name("Owner B")
        .build();
    userRepository.save(ownerB);

    TripEntity trip = TripEntity.builder()
        .user(ownerA)
        .title("Old Title")
        .destination("Paris")
        .startDate(LocalDate.parse("2026-06-01"))
        .shortDescription("Old short")
        .longDescription("Old long")
        .build();
    tripRepository.save(trip);

    String body = objectMapper.writeValueAsString(Map.of(
        "userId", ownerB.getUser_id(),
        "title", "New Title",
        "destination", "Rome",
        "startDate", "2026-07-15",
        "shortDescription", "New short",
        "longDescription", "New long"
    ));

    mockMvc.perform(put("/v1/trips/{id}", trip.getTrip_id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.title").value("New Title"))
        .andExpect(jsonPath("$.destination").value("Rome"));
  }

  @Test
  void patchTripWithoutFieldsReturnsBadRequest() throws Exception {
    UserEntity user = UserEntity.builder()
        .email("trip-owner@example.com")
        .name("Trip Owner")
        .build();
    userRepository.save(user);

    TripEntity trip = TripEntity.builder()
        .user(user)
        .title("Trip")
        .destination("Madrid")
        .startDate(LocalDate.parse("2026-08-01"))
        .shortDescription("Short")
        .longDescription("Long")
        .build();
    tripRepository.save(trip);

    String body = objectMapper.writeValueAsString(Map.of());

    mockMvc.perform(patch("/v1/trips/{id}", trip.getTrip_id())
            .contentType(MediaType.APPLICATION_JSON)
            .content(body))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.error").value("InvalidInput"));
  }

  @Test
  void deleteTripReturnsNoContentAndRemovesTrip() throws Exception {
    UserEntity user = UserEntity.builder()
        .email("delete-trip@example.com")
        .name("Trip User")
        .build();
    userRepository.save(user);

    TripEntity trip = TripEntity.builder()
        .user(user)
        .title("Trip To Delete")
        .destination("Vienna")
        .startDate(LocalDate.parse("2026-09-01"))
        .shortDescription("Short")
        .longDescription("Long")
        .build();
    tripRepository.save(trip);

    mockMvc.perform(delete("/v1/trips/{id}", trip.getTrip_id()))
        .andExpect(status().isNoContent());

    org.assertj.core.api.Assertions.assertThat(tripRepository.findById(trip.getTrip_id())).isEmpty();
  }

  @Test
  void deleteTripWithUnknownIdReturnsNotFound() throws Exception {
    mockMvc.perform(delete("/v1/trips/{id}", 999999L))
        .andExpect(status().isNotFound())
        .andExpect(jsonPath("$.error").value("NotFound"));
  }
}
