package com.tripplanning.trip.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.TestClientsConfig;
import com.tripplanning.TripServiceApplication;
import com.tripplanning.auth.AppJwtService;
import com.tripplanning.accommodation.AccomEntity;
import com.tripplanning.accommodation.AccomRepository;
import com.tripplanning.common.client.SocialServiceClient;
import com.tripplanning.config.CacheConfig;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

@SpringBootTest(classes = TripServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestClientsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class LikedFeedIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private AccomRepository accomRepository;
    @Autowired private AppJwtService appJwtService;
    @Autowired private CacheManager cacheManager;

    @MockitoBean private SocialServiceClient socialServiceClient;

    @Autowired private TripFeedService tripFeedService;

    private UserEntity viewer;
    private TripEntity likedTrip;
    private TripEntity otherTrip;

    @BeforeEach
    void setUp() {
        for (String name : cacheManager.getCacheNames()) {
            cacheManager.getCache(name).clear();
        }
        tripRepository.deleteAll();
        accomRepository.deleteAll();
        userRepository.deleteAll();

        viewer =
                userRepository.save(
                        UserEntity.builder()
                                .email("viewer@example.com")
                                .name("Viewer")
                                .imagePath("")
                                .description("")
                                .build());
        UserEntity author =
                userRepository.save(
                        UserEntity.builder()
                                .email("author@example.com")
                                .name("Author")
                                .imagePath("")
                                .description("")
                                .build());

        likedTrip = saveTrip(author, "Liked trip", "Rome");
        otherTrip = saveTrip(author, "Other trip", "Paris");
    }

    @Test
    void feedLiked_withoutToken_returns401() throws Exception {
        mockMvc.perform(
                        get("/api/v2/trips/feed")
                                .param("mode", "liked")
                                .param("page", "0")
                                .param("size", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void feedLiked_withToken_returnsOnlyLikedTripsInLikeOrder() throws Exception {
        when(socialServiceClient.getLikedTripIdsForUser(viewer.getId()))
                .thenReturn(List.of(otherTrip.getId(), likedTrip.getId()));

        String token = appJwtService.createToken(viewer.getId(), viewer.getEmail());
        mockMvc.perform(
                        get("/api/v2/trips/feed")
                                .param("mode", "liked")
                                .param("page", "0")
                                .param("size", "10")
                                .header("Authorization", "Bearer " + token))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(2))
                .andExpect(jsonPath("$.items[0].id").value(otherTrip.getId()))
                .andExpect(jsonPath("$.items[1].id").value(likedTrip.getId()));
    }

    @Test
    void feedLikedBy_legacyEndpoint_stillWorks() throws Exception {
        when(socialServiceClient.getLikedTripIdsForUser(viewer.getId()))
                .thenReturn(List.of(likedTrip.getId()));

        mockMvc.perform(
                        get("/api/v2/trips/feed/liked-by")
                                .param("userId", String.valueOf(viewer.getId()))
                                .param("page", "0")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(likedTrip.getId()));
    }

    @Test
    void feedLiked_isCachedPerUserPage() {
        when(socialServiceClient.getLikedTripIdsForUser(viewer.getId()))
                .thenReturn(List.of(likedTrip.getId()));

        tripFeedService.feedLikedBy(viewer.getId(), 0, 10);
        assertThat(cacheManager.getCache(CacheConfig.TRIP_FEED_LIKED_BY).get(List.of(viewer.getId(), 0, 10)))
                .isNotNull();
    }

    private TripEntity saveTrip(UserEntity author, String title, String destination) {
        AccomEntity accom =
                accomRepository.save(
                        AccomEntity.builder()
                                .name("Hotel")
                                .type("hotel")
                                .address("Main St")
                                .googlePlaceId("ChIJ_" + title)
                                .cityName(destination)
                                .checkInDate(LocalDate.of(2026, 6, 1))
                                .checkOutDate(LocalDate.of(2026, 6, 3))
                                .cost(new BigDecimal("100"))
                                .currency("EUR")
                                .build());
        return tripRepository.save(
                TripEntity.builder()
                        .user(author)
                        .title(title)
                        .destination(destination)
                        .destinationGooglePlaceId("ChIJ_" + title)
                        .startDate(LocalDate.of(2026, 6, 1))
                        .shortDescription("Short")
                        .longDescription("Long")
                        .accommodations(List.of(accom))
                        .build());
    }
}
