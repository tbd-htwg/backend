package com.tripplanning.catalog;

import static com.github.tomakehurst.wiremock.client.WireMock.aResponse;
import static com.github.tomakehurst.wiremock.client.WireMock.configureFor;
import static com.github.tomakehurst.wiremock.client.WireMock.equalTo;
import static com.github.tomakehurst.wiremock.client.WireMock.get;
import static com.github.tomakehurst.wiremock.client.WireMock.stubFor;
import static com.github.tomakehurst.wiremock.client.WireMock.urlPathEqualTo;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.time.LocalDate;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import com.github.tomakehurst.wiremock.WireMockServer;
import com.github.tomakehurst.wiremock.core.WireMockConfiguration;
import com.tripplanning.TestClientsConfig;
import com.tripplanning.TripServiceApplication;
import com.tripplanning.accommodation.AccomEntity;
import com.tripplanning.accommodation.AccomRepository;
import com.tripplanning.auth.AppJwtService;
import com.tripplanning.transport.TransportEntity;
import com.tripplanning.transport.TransportRepository;
import com.tripplanning.user.UserEntity;
import com.tripplanning.user.UserRepository;

@SpringBootTest(classes = TripServiceApplication.class)
@Import(TestClientsConfig.class)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TransportAccomUpdateIntegrationTest {

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
    @Autowired private AccomRepository accomRepository;
    @Autowired private TransportRepository transportRepository;
    @Autowired private AppJwtService appJwtService;

    private String token;
    private AccomEntity accom;
    private TransportEntity transport;

    @BeforeEach
    void setUp() {
        wireMockServer.resetAll();
        accomRepository.deleteAll();
        transportRepository.deleteAll();
        userRepository.deleteAll();

        UserEntity user =
                userRepository.save(
                        UserEntity.builder()
                                .email("editor@example.com")
                                .name("Editor")
                                .imagePath("")
                                .description("")
                                .build());
        token = appJwtService.createToken(user.getId(), user.getEmail());

        accom =
                accomRepository.save(
                        AccomEntity.builder()
                                .name("Old Hotel")
                                .type("")
                                .address("Old address")
                                .googlePlaceId("ChIJ_old_hotel")
                                .cityName("Berlin")
                                .checkInDate(LocalDate.of(2026, 7, 1))
                                .checkOutDate(LocalDate.of(2026, 7, 3))
                                .cost(new BigDecimal("100.00"))
                                .currency("EUR")
                                .build());

        transport =
                transportRepository.save(
                        TransportEntity.builder()
                                .startGooglePlaceId("ChIJ_old_start")
                                .endGooglePlaceId("ChIJ_old_end")
                                .startAddress("Old start")
                                .endAddress("Old end")
                                .build());

        stubPlace("ChIJ_new_hotel", "New Hotel", "Berlin", "New hotel street");
        stubPlace("ChIJ_new_start", "New Start", "Berlin", "Start street");
        stubPlace("ChIJ_new_end", "New End", "Berlin", "End street");
    }

    private void stubPlace(String placeId, String placeName, String cityName, String formattedAddress) {
        stubFor(
                get(urlPathEqualTo("/internal/location-pack"))
                        .withQueryParam("placeId", equalTo(placeId))
                        .willReturn(
                                aResponse()
                                        .withHeader("Content-Type", "application/json")
                                        .withBody(
                                                """
                                                {
                                                  "placeName": "%s",
                                                  "cityName": "%s",
                                                  "formattedAddress": "%s",
                                                  "lat": 52.5,
                                                  "lon": 13.4,
                                                  "countryCode": "DE"
                                                }
                                                """
                                                        .formatted(placeName, cityName, formattedAddress))));
    }

    @Test
    void updateAccommodation_returnsEnrichedFields() throws Exception {
        mockMvc.perform(
                        put("/api/v2/accommodations/" + accom.getId())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "googlePlaceId": "ChIJ_new_hotel",
                                          "checkInDate": "2026-07-05",
                                          "checkOutDate": "2026-07-10",
                                          "cost": 250.50,
                                          "currency": "USD"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(accom.getId()))
                .andExpect(jsonPath("$.name").value("New Hotel"))
                .andExpect(jsonPath("$.address").value("New hotel street"))
                .andExpect(jsonPath("$.googlePlaceId").value("ChIJ_new_hotel"))
                .andExpect(jsonPath("$.currency").value("USD"));
    }

    @Test
    void updateTransport_returnsEnrichedFields() throws Exception {
        mockMvc.perform(
                        put("/api/v2/transports/" + transport.getId())
                                .header("Authorization", "Bearer " + token)
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "startGooglePlaceId": "ChIJ_new_start",
                                          "endGooglePlaceId": "ChIJ_new_end"
                                        }
                                        """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(transport.getId()))
                .andExpect(jsonPath("$.startAddress").value("Start street"))
                .andExpect(jsonPath("$.endAddress").value("End street"));
    }

    @Test
    void updateAccommodation_withoutToken_returns401() throws Exception {
        mockMvc.perform(
                        put("/api/v2/accommodations/" + accom.getId())
                                .contentType(MediaType.APPLICATION_JSON)
                                .content(
                                        """
                                        {
                                          "googlePlaceId": "ChIJ_new_hotel",
                                          "checkInDate": "2026-07-05",
                                          "checkOutDate": "2026-07-10",
                                          "cost": 1,
                                          "currency": "EUR"
                                        }
                                        """))
                .andExpect(status().isUnauthorized());
    }
}
