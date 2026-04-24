package com.tripplanning.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class AuthSecurityIntegrationTest {

  @Autowired private MockMvc mockMvc;
  @Autowired private UserRepository userRepository;
  @Autowired private AppJwtService appJwtService;

  private UserEntity alice;

  @BeforeEach
  void setUp() {
    userRepository.deleteAll();
    alice =
        userRepository.save(
            UserEntity.builder()
                .email("alice@example.com")
                .name("Alice")
                .imageUrl("")
                .description("")
                .build());
  }

  @Test
  void authMe_withoutToken_returns401() throws Exception {
    mockMvc.perform(get("/api/v2/auth/me")).andExpect(status().isUnauthorized());
  }

  @Test
  void authMe_withValidJwt_returns200() throws Exception {
    String token = appJwtService.createToken(alice.getId(), alice.getEmail());
    mockMvc
        .perform(get("/api/v2/auth/me").header("Authorization", "Bearer " + token))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.email").value("alice@example.com"))
        .andExpect(jsonPath("$.name").value("Alice"));
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
  void getUserByNumericId_withoutToken_returns200() throws Exception {
    mockMvc.perform(get("/api/v2/users/" + alice.getId())).andExpect(status().isOk());
  }

  @Test
  void userSearch_withoutToken_returns401() throws Exception {
    mockMvc
        .perform(get("/api/v2/users/search/findByName").param("name", "Alice"))
        .andExpect(status().isUnauthorized());
  }

  @Test
  void postAuthRegister_withoutToken_returns200() throws Exception {
    mockMvc
        .perform(
            post("/api/v2/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(
                    "{\"email\":\"register@example.com\",\"name\":\"Reg User\",\"imageUrl\":\"\",\"description\":\"\"}"))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.accessToken").isString())
        .andExpect(jsonPath("$.user.email").value("register@example.com"));
  }
}
