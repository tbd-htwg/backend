package com.tripplanning.trip;

import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doAnswer;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.SpyBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.TestClientsConfig;
import com.tripplanning.TripServiceApplication;
import com.tripplanning.auth.AppJwtService;
import com.tripplanning.images.ImageService;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationImageEntity;
import com.tripplanning.tripLocation.TripLocationImageRepository;
import com.tripplanning.tripLocation.TripLocationRepository;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

@SpringBootTest(classes = TripServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestClientsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TripFeedImagesControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripLocationRepository tripLocationRepository;
    @Autowired private TripLocationImageRepository tripLocationImageRepository;
    @Autowired private AppJwtService appJwtService;

    @SpyBean private ImageService imageService;

    private TripEntity trip;
    private String bearerToken;

    @BeforeEach
    void setUp() {
        tripRepository.deleteAll();
        userRepository.deleteAll();

        doAnswer(
                        inv -> {
                            @SuppressWarnings("unchecked")
                            List<String> paths = inv.getArgument(0);
                            return paths.stream()
                                    .map(p -> "https://signed.example/" + p)
                                    .toList();
                        })
                .when(imageService)
                .createSignedReadUrlsIfAuthenticated(anyList());

        UserEntity author =
                userRepository.save(
                        UserEntity.builder()
                                .email("feed-images@example.com")
                                .name("Feed Images")
                                .imagePath("")
                                .description("")
                                .build());
        bearerToken = appJwtService.createToken(author.getId(), author.getEmail());

        trip =
                tripRepository.save(
                        TripEntity.builder()
                                .user(author)
                                .title("Carousel trip")
                                .destination("Japan")
                                .destinationGooglePlaceId("ChIJ_TestJapanPlaceId")
                                .startDate(LocalDate.of(2026, 5, 1))
                                .shortDescription("Short")
                                .longDescription("Long")
                                .build());

        TripLocationEntity stop =
                tripLocationRepository.save(
                        TripLocationEntity.builder()
                                .trip(trip)
                                .googlePlaceId("ChIJ_stop")
                                .placeName("Tokyo")
                                .cityName("Tokyo")
                                .description("Stop")
                                .startDate(LocalDateTime.of(2026, 5, 1, 9, 0))
                                .endDate(LocalDateTime.of(2026, 5, 2, 18, 0))
                                .build());

        tripLocationImageRepository.save(
                TripLocationImageEntity.builder()
                        .tripLocation(stop)
                        .imagePath("trip-locations/1/a.jpg")
                        .build());
        tripLocationImageRepository.save(
                TripLocationImageEntity.builder()
                        .tripLocation(stop)
                        .imagePath("trip-locations/1/b.jpg")
                        .build());
    }

    @Test
    void feedLocationImages_withoutToken_returnsEmptyLists() throws Exception {
        mockMvc.perform(
                        get("/api/v2/trips/feed-location-images")
                                .param("tripId", String.valueOf(trip.getId())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + trip.getId() + "']").isArray())
                .andExpect(jsonPath("$['" + trip.getId() + "']").isEmpty());
    }

    @Test
    void feedLocationImages_withJwt_returnsAllSignedUrls() throws Exception {
        mockMvc.perform(
                        get("/api/v2/trips/feed-location-images")
                                .param("tripId", String.valueOf(trip.getId()))
                                .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + trip.getId() + "'].length()").value(2))
                .andExpect(
                        jsonPath("$['" + trip.getId() + "'][0]")
                                .value("https://signed.example/trip-locations/1/a.jpg"))
                .andExpect(
                        jsonPath("$['" + trip.getId() + "'][1]")
                                .value("https://signed.example/trip-locations/1/b.jpg"));
    }

    @Test
    void feedLocationImages_perTripLimit_returnsFirstOnly() throws Exception {
        mockMvc.perform(
                        get("/api/v2/trips/feed-location-images")
                                .param("tripId", String.valueOf(trip.getId()))
                                .param("perTripLimit", "1")
                                .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + trip.getId() + "'].length()").value(1))
                .andExpect(
                        jsonPath("$['" + trip.getId() + "'][0]")
                                .value("https://signed.example/trip-locations/1/a.jpg"));
    }

    @Test
    void feedLocationImages_startIndex_skipsEarlierImages() throws Exception {
        mockMvc.perform(
                        get("/api/v2/trips/feed-location-images")
                                .param("tripId", String.valueOf(trip.getId()))
                                .param("startIndex", "1")
                                .param("perTripLimit", "1")
                                .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['" + trip.getId() + "'].length()").value(1))
                .andExpect(
                        jsonPath("$['" + trip.getId() + "'][0]")
                                .value("https://signed.example/trip-locations/1/b.jpg"));
    }

    @Test
    void feedLocationImages_invalidPerTripLimit_returns400() throws Exception {
        mockMvc.perform(
                        get("/api/v2/trips/feed-location-images")
                                .param("tripId", String.valueOf(trip.getId()))
                                .param("perTripLimit", "0")
                                .header("Authorization", "Bearer " + bearerToken))
                .andExpect(status().isBadRequest());
    }
}
