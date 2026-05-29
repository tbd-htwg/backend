package com.tripplanning.trip.read;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.context.annotation.Import;

import com.tripplanning.TestClientsConfig;
import com.tripplanning.TripServiceApplication;
import com.tripplanning.accommodation.AccomEntity;
import com.tripplanning.accommodation.AccomRepository;
import com.tripplanning.config.CacheConfig;
import com.tripplanning.transport.TransportEntity;
import com.tripplanning.transport.TransportRepository;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationRepository;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

@SpringBootTest(classes = TripServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestClientsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TripFeedControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripLocationRepository tripLocationRepository;
    @Autowired private AccomRepository accomRepository;
    @Autowired private TransportRepository transportRepository;
    @Autowired private TripFeedService tripFeedService;
    @Autowired private TripCacheEvictor tripCacheEvictor;
    @Autowired private CacheManager cacheManager;

    private UserEntity author;
    private TripEntity trip;
    private TripLocationEntity stop;

    @BeforeEach
    void setUp() {
        clearAllCaches();
        tripRepository.deleteAll();
        accomRepository.deleteAll();
        transportRepository.deleteAll();
        userRepository.deleteAll();

        author = userRepository.save(
                UserEntity.builder()
                        .email("author@example.com")
                        .name("Author")
                        .imagePath("")
                        .description("")
                        .build());
        
        AccomEntity accom = accomRepository.save(
                AccomEntity.builder()
                        .name("Hotel Sakura")
                        .type("hotel")
                        .address("Shibuya, Tokyo")
                        .googlePlaceId("ChIJ912345_TokyoPlaceId")
                        .cityName("Tokyo")
                        .checkInDate(LocalDate.of(2026, 5, 1))
                        .checkOutDate(LocalDate.of(2026, 5, 5))
                        .cost(new BigDecimal("450.00"))
                        .currency("EUR")
                        .build());

        TransportEntity transport =
                transportRepository.save(
                        TransportEntity.builder()
                                .startGooglePlaceId("ChIJ_start")
                                .endGooglePlaceId("ChIJ_end")
                                .startAddress("Tokyo Station")
                                .endAddress("Kyoto Station")
                                .build());

        trip = TripEntity.builder()
                        .user(author)
                        .title("Spring trip")
                        .destination("Japan")
                        .destinationGooglePlaceId("ChIJ_TestJapanPlaceId")
                        .startDate(LocalDate.of(2026, 5, 1))
                        .shortDescription("Cherry blossoms")
                        .longDescription("Two weeks chasing sakura through Honshu.")
                        .accommodations(List.of(accom))
                        .transports(List.of(transport))
                        .build();
        trip = tripRepository.save(trip);

        stop = tripLocationRepository.save(
                TripLocationEntity.builder()
                        .trip(trip)
                        .googlePlaceId("ChIJ912345_TokyoPlaceId")
                        .placeName("Tokyo Tower")
                        .cityName("Tokyo")
                        .description("First stop")
                        .startDate(LocalDateTime.of(2026, 5, 1, 9, 0))
                        .endDate(LocalDateTime.of(2026, 5, 3, 18, 0))
                        .build());
    }

    @Test
    void feed_returnsItemsWithMaterialisedNames() throws Exception {
        mockMvc.perform(get("/api/v2/trips/feed").param("page", "0").param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(trip.getId()))
                .andExpect(jsonPath("$.items[0].title").value("Spring trip"))
                .andExpect(jsonPath("$.items[0].destination").value("Japan"))
                .andExpect(jsonPath("$.items[0].author.id").value(author.getId()))
                .andExpect(jsonPath("$.items[0].author.name").value("Author"))
                .andExpect(jsonPath("$.items[0].locations[0]").value("Tokyo Tower"))
                .andExpect(jsonPath("$.items[0].accommodationNames[0]").value("Hotel Sakura"))
                .andExpect(jsonPath("$.items[0].transportRoutes[0]").value("Tokyo Station → Kyoto Station"));
    }

    @Test
    void feedByUser_filtersByOwner() throws Exception {
        mockMvc.perform(
                        get("/api/v2/trips/feed/by-user")
                                .param("userId", String.valueOf(author.getId()))
                                .param("page", "0")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(1))
                .andExpect(jsonPath("$.items[0].id").value(trip.getId()));

        mockMvc.perform(
                        get("/api/v2/trips/feed/by-user")
                                .param("userId", String.valueOf(author.getId() + 999))
                                .param("page", "0")
                                .param("size", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalItems").value(0))
                .andExpect(jsonPath("$.items").isEmpty());
    }

    @Test
    void detail_returnsAllSections() throws Exception {
        mockMvc.perform(get("/api/v2/trips/" + trip.getId() + "/detail"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(trip.getId()))
                .andExpect(jsonPath("$.title").value("Spring trip"))
                .andExpect(jsonPath("$.longDescription").value("Two weeks chasing sakura through Honshu."))
                .andExpect(jsonPath("$.author.id").value(author.getId()))
                .andExpect(jsonPath("$.stops[0].id").value(stop.getId()))
                .andExpect(jsonPath("$.stops[0].placeName").value("Tokyo Tower"))
                .andExpect(jsonPath("$.stops[0].cityName").value("Tokyo"))
                .andExpect(jsonPath("$.stops[0].description").value("First stop"))
                .andExpect(jsonPath("$.accommodations[0].name").value("Hotel Sakura"))
                .andExpect(jsonPath("$.accommodations[0].googlePlaceId").value("ChIJ912345_TokyoPlaceId"))
                .andExpect(jsonPath("$.accommodations[0].cost").value(450.0))
                .andExpect(jsonPath("$.accommodations[0].currency").value("EUR"))
                .andExpect(jsonPath("$.transports[0].startGooglePlaceId").value("ChIJ_start"))
                .andExpect(jsonPath("$.transports[0].endGooglePlaceId").value("ChIJ_end"))
                .andExpect(jsonPath("$.transports[0].startAddress").value("Tokyo Station"))
                .andExpect(jsonPath("$.transports[0].endAddress").value("Kyoto Station"));
    }

    @Test
    void detail_returns404ForUnknownTrip() throws Exception {
        mockMvc.perform(get("/api/v2/trips/999999/detail")).andExpect(status().isNotFound());
    }

    @Test
    void detailRaw_isCachedAfterFirstCall() {
        tripFeedService.detail(trip.getId());
        assertThat(cacheManager.getCache(CacheConfig.TRIP_DETAIL).get(trip.getId())).isNotNull();
    }

    @Test
    void evictForTripChange_invalidatesDetailCache() {
        tripFeedService.detail(trip.getId());
        assertThat(cacheManager.getCache(CacheConfig.TRIP_DETAIL).get(trip.getId())).isNotNull();

        tripCacheEvictor.evictForTripChange(trip.getId());

        assertThat(cacheManager.getCache(CacheConfig.TRIP_DETAIL).get(trip.getId())).isNull();
    }

    private void clearAllCaches() {
        for (String name : cacheManager.getCacheNames()) {
            cacheManager.getCache(name).clear();
        }
    }
}