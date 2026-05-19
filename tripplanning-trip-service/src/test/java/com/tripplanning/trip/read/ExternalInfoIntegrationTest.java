package com.tripplanning.trip.read;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.LocalDateTime;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.tripplanning.TestClientsConfig;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;
import com.tripplanning.TripServiceApplication;
import com.tripplanning.tripLocation.TripLocationEntity;
import com.tripplanning.tripLocation.TripLocationRepository;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

@SpringBootTest(classes = TripServiceApplication.class, webEnvironment = SpringBootTest.WebEnvironment.MOCK)
@Import(TestClientsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
class ExternalInfoIntegrationTest {

    private static WireMockServer wireMockServer;

    @BeforeAll
    static void startWireMock() {
        wireMockServer = new WireMockServer(WireMockConfiguration.wireMockConfig().dynamicPort());
        wireMockServer.start();
        configureFor("localhost", wireMockServer.port());
    }

    @AfterAll
    static void stopWireMock() {
        if (wireMockServer != null) {
            wireMockServer.stop();
        }
    }

    @DynamicPropertySource
    static void externalInfoUrl(DynamicPropertyRegistry registry) {
        registry.add(
                "tripplanning.services.external-info-base-url",
                () -> "http://localhost:" + wireMockServer.port());
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private UserRepository userRepository;
    @Autowired private TripRepository tripRepository;
    @Autowired private TripLocationRepository tripLocationRepository;

    private TripLocationEntity stop;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        tripLocationRepository.deleteAll();
        tripRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity user = userRepository.save(
                UserEntity.builder()
                        .email("test@example.com")
                        .name("Tester")
                        .imagePath("")
                        .description("")
                        .build());

        TripEntity trip = tripRepository.save(
                new TripEntity(
                        user,
                        "Paris trip",
                        "Paris",
                        LocalDate.of(2026, 6, 1),
                        "Short",
                        "Long"));

        stop = tripLocationRepository.save(
                TripLocationEntity.builder()
                        .trip(trip)
                        .googlePlaceId("ChIJD7fiw9u55kcRLm1vYGoUby0") // Beispiel-ID für Paris
                        .cityName("Paris")
                        .description("Eiffel Tower")
                        .startDate(LocalDateTime.of(2026, 6, 2, 10, 0))
                        .endDate(LocalDateTime.of(2026, 6, 2, 18, 0))
                        .build());

        stubFor(com.github.tomakehurst.wiremock.client.WireMock.get(urlPathEqualTo("/api/v2/external/details"))
                .withQueryParam("placeId", equalTo("ChIJD7fiw9u55kcRLm1vYGoUby0"))
                .willReturn(aResponse()
                        .withHeader("Content-Type", "application/json")
                        .withBody(
                                """
                                {
                                  "warning": {"country":"FR","status":"Safety Info","message":"All clear"},
                                  "weather": {"currentTemp":20.0,"currentWeatherCode":0,"currentDescription":"Clear sky","dailyForecasts":[]},
                                  "tours": [{"id":"t1","title":"City tour","price":"25 €","url":"#"}]
                                }
                                """)));
    }

    @Test
    void getStopDetails_returnsExternalInfoFromService() throws Exception {
        mockMvc.perform(get("/api/v2/trip-locations/" + stop.getId() + "/details"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.warning.country").value("FR"))
                .andExpect(jsonPath("$.weather.currentDescription").value("Clear sky"))
                .andExpect(jsonPath("$.tours[0].title").value("City tour"));
    }
}