package com.tripplanning.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import org.springframework.context.annotation.Import;

import com.tripplanning.TestClientsConfig;
import com.tripplanning.TripServiceApplication;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

@SpringBootTest(classes = TripServiceApplication.class)
@Import(TestClientsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthSecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  private UserEntity alice;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    alice =
        userRepository.save(
            UserEntity.builder()
                .email("alice@example.com")
                .name("Alice")
                .imagePath("")
                .description("")
                .build());
  }

  @Test
  void deleteUser_withoutToken_returns401() throws Exception {
    mockMvc.perform(delete("/api/v2/users/" + alice.getId())).andExpect(status().isUnauthorized());
  }

  @Test
  void getTrips_withoutToken_returns200() throws Exception {
    mockMvc.perform(get("/api/v2/trips")).andExpect(status().isOk());
  }

  @Test
  void getTripFeed_withoutToken_returns200() throws Exception {
    mockMvc
        .perform(get("/api/v2/trips/feed").param("page", "0").param("size", "10"))
        .andExpect(status().isOk());
  }

  @Test
  void getUserByNumericId_withoutToken_returns200() throws Exception {
    mockMvc.perform(get("/api/v2/users/" + alice.getId())).andExpect(status().isOk());
  }

  @Test
  void userSearch_withoutToken_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v2/users/search/findByName").param("name", "Alice"))
        .andExpect(status().isUnauthorized());
  }

}
