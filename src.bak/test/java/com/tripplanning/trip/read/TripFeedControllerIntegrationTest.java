package com.tripplanning.trip.read;

import static org.assertj.core.api.Assertions.assertThat;
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
import org.springframework.cache.CacheManager;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.accommodation.AccomEntity;
import com.tripplanning.accommodation.AccomRepository;
import com.tripplanning.config.CacheConfig;
import com.tripplanning.location.LocationEntity;
import com.tripplanning.location.LocationRepository;
import com.tripplanning.transport.TransportEntity;
import com.tripplanning.transport.TransportRepository;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationRepository;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

/**
 * Covers the new {@code /api/v2/trips/feed} and {@code /api/v2/trips/{id}/detail} endpoints plus
 * the {@link TripCacheEvictor}-driven cache invalidation. The point of these tests is correctness
 * of the JPQL/DTO assembly and the eviction wiring; the SQL-statement count win is validated by
 * the manual local sanity check called out in {@code performance/reports/}.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TripFeedControllerIntegrationTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private LocationRepository locationRepository;
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
        tripLocationRepository.deleteAll();
        tripRepository.deleteAll();
        accomRepository.deleteAll();
        transportRepository.deleteAll();
        locationRepository.deleteAll();
        userRepository.deleteAll();

        author =
                userRepository.save(
                        UserEntity.builder()
                                .email("author@example.com")
                                .name("Author")
                                .imagePath("")
                                .description("")
                                .build());
        LocationEntity loc = locationRepository.save(new LocationEntity("Tokyo"));
        AccomEntity accom = accomRepository.save(new AccomEntity("Hotel Sakura", "hotel", "Shibuya"));
        TransportEntity transport = transportRepository.save(new TransportEntity("train"));

        trip =
                TripEntity.builder()
                        .user(author)
                        .title("Spring trip")
                        .destination("Japan")
                        .startDate(LocalDate.of(2026, 5, 1))
                        .shortDescription("Cherry blossoms")
                        .longDescription("Two weeks chasing sakura through Honshu.")
                        .accommodations(List.of(accom))
                        .transports(List.of(transport))
                        .build();
        trip = tripRepository.save(trip);

        stop =
                TripLocationEntity.builder()
                        .trip(trip)
                        .location(loc)
                        .description("First stop")
                        .startDate(LocalDateTime.of(2026, 5, 1, 9, 0))
                        .endDate(LocalDateTime.of(2026, 5, 3, 18, 0))
                        .build();
        stop = tripLocationRepository.save(stop);
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
                .andExpect(jsonPath("$.items[0].locations[0]").value("Tokyo"))
                .andExpect(jsonPath("$.items[0].accommodationNames[0]").value("Hotel Sakura"))
                .andExpect(jsonPath("$.items[0].transportTypes[0]").value("train"));
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
                .andExpect(jsonPath("$.stops[0].locationName").value("Tokyo"))
                .andExpect(jsonPath("$.stops[0].description").value("First stop"))
                .andExpect(jsonPath("$.accommodations[0].name").value("Hotel Sakura"))
                .andExpect(jsonPath("$.transports[0].type").value("train"));
    }

    @Test
    void detail_returns404ForUnknownTrip() throws Exception {
        mockMvc.perform(get("/api/v2/trips/999999/detail")).andExpect(status().isNotFound());
    }

    @Test
    void tripExists_isCachedAfterFirstCall() {
        boolean firstAnswer = tripFeedService.tripExists(trip.getId());
        assertThat(firstAnswer).isTrue();
        assertThat(cacheManager.getCache(CacheConfig.TRIP_EXISTS).get(trip.getId(), Boolean.class))
                .isTrue();
    }

    @Test
    void evictForTripChange_invalidatesDetailAndFeedCaches() {
        tripFeedService.detail(trip.getId());
        tripFeedService.feed(0, 10);
        assertThat(cacheManager.getCache(CacheConfig.TRIP_DETAIL).get(trip.getId())).isNotNull();
        assertThat(cacheManager.getCache(CacheConfig.TRIP_FEED_PAGE).get(List.of(0, 10)))
                .isNotNull();

        tripCacheEvictor.evictForTripChange(trip.getId());

        assertThat(cacheManager.getCache(CacheConfig.TRIP_DETAIL).get(trip.getId())).isNull();
        assertThat(cacheManager.getCache(CacheConfig.TRIP_FEED_PAGE).get(List.of(0, 10))).isNull();
    }

    private void clearAllCaches() {
        for (String name : cacheManager.getCacheNames()) {
            cacheManager.getCache(name).clear();
        }
    }
}
