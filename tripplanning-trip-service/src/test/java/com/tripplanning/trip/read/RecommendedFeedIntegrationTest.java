package com.tripplanning.trip.read;

import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.util.List;

import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.TripServiceApplication;
import com.tripplanning.auth.AppJwtService;
import com.tripplanning.common.client.SocialServiceClient;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

import jakarta.persistence.EntityManager;

@SpringBootTest(classes = TripServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class RecommendedFeedIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private AppJwtService appJwtService;
    @Autowired private EntityManager entityManager;

    @MockBean private SocialServiceClient socialServiceClient;

    private UserEntity viewer;
    private UserEntity otherAuthor;
    private TripEntity anchorTrip;
    private TripEntity similarTrip;
    private String viewerToken;

    @BeforeEach
    void setUp() {
        tripRepository.deleteAll();
        userRepository.deleteAll();

        viewer =
                userRepository.save(
                        UserEntity.builder()
                                .email("viewer@example.com")
                                .name("Viewer")
                                .imagePath("")
                                .description("")
                                .build());
        otherAuthor =
                userRepository.save(
                        UserEntity.builder()
                                .email("other@example.com")
                                .name("Other")
                                .imagePath("")
                                .description("")
                                .build());

        viewerToken = appJwtService.createToken(viewer.getId(), viewer.getEmail());

        anchorTrip =
                tripRepository.save(
                        TripEntity.builder()
                                .user(otherAuthor)
                                .title("Spring trip")
                                .destination("Japan")
                                .destinationGooglePlaceId("ChIJ_anchor")
                                .startDate(LocalDate.of(2026, 5, 1))
                                .shortDescription("Cherry blossoms in Tokyo")
                                .longDescription("Sakura season walk")
                                .build());

        similarTrip =
                tripRepository.save(
                        TripEntity.builder()
                                .user(otherAuthor)
                                .title("Sakura journey")
                                .destination("Japan")
                                .destinationGooglePlaceId("ChIJ_similar")
                                .startDate(LocalDate.of(2026, 4, 10))
                                .shortDescription("Cherry blossoms and temples")
                                .longDescription("Honshu sakura route")
                                .build());

        entityManager.flush();
        SearchSession searchSession = Search.session(entityManager);
        var indexingPlan = searchSession.indexingPlan();
        indexingPlan.addOrUpdate(anchorTrip);
        indexingPlan.addOrUpdate(similarTrip);
        indexingPlan.execute();

        when(socialServiceClient.getLikedTripIdsForUser(anyLong())).thenReturn(List.of());
        when(socialServiceClient.getLikedTripIdsForUser(viewer.getId()))
                .thenReturn(List.of(anchorTrip.getId()));
    }

    @Test
    void recommendedFeed_withoutToken_returns401() throws Exception {
        mockMvc.perform(
                        get("/api/v2/trips/feed")
                                .param("mode", "recommended")
                                .param("page", "0")
                                .param("size", "10"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void recommendedFeed_withLikedAnchor_returnsSimilarTripNotAnchor() throws Exception {
        mockMvc.perform(
                        get("/api/v2/trips/feed")
                                .param("mode", "recommended")
                                .param("page", "0")
                                .param("size", "10")
                                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("recommended"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(similarTrip.getId()))
                .andExpect(jsonPath("$.items[0].title").value("Sakura journey"));
    }

    @Test
    void recommendedFeed_withoutAnchors_fallsBackWithSource() throws Exception {
        when(socialServiceClient.getLikedTripIdsForUser(viewer.getId())).thenReturn(List.of());

        mockMvc.perform(
                        get("/api/v2/trips/feed")
                                .param("mode", "recommended")
                                .param("page", "0")
                                .param("size", "10")
                                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("latest-fallback"))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void latestFeed_includesSourceLatest() throws Exception {
        mockMvc.perform(
                        get("/api/v2/trips/feed")
                                .param("mode", "latest")
                                .param("page", "0")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("latest"))
                .andExpect(jsonPath("$.totalItems").value(2));
    }

    @Test
    void latestFeed_differsFromRecommendedWhenUserHasLikedAnchor() throws Exception {
        mockMvc.perform(
                        get("/api/v2/trips/feed")
                                .param("mode", "latest")
                                .param("page", "0")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("latest"))
                .andExpect(jsonPath("$.totalItems").value(2));

        mockMvc.perform(
                        get("/api/v2/trips/feed")
                                .param("mode", "recommended")
                                .param("page", "0")
                                .param("size", "10")
                                .header("Authorization", "Bearer " + viewerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.source").value("recommended"))
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(similarTrip.getId()))
                .andExpect(jsonPath("$.items[?(@.id == " + anchorTrip.getId() + ")]")
                        .isEmpty());
    }
}
