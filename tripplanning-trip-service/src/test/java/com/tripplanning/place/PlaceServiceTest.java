package com.tripplanning.place;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.tripplanning.external.ExternalInfoClient;
import com.tripplanning.external.ExternalInfoDtos.PlaceDetailsResult;

@ExtendWith(MockitoExtension.class)
class PlaceServiceTest {

    private static final String PLACE_ID = "ChIJGaK-SZcLkEcRA9wf5_GNbuY";

    @Mock private GooglePlaceRepository googlePlaceRepository;
    @Mock private ExternalInfoClient externalInfoClient;

    private PlaceService placeService;

    @BeforeEach
    void setUp() {
        placeService = new PlaceService(googlePlaceRepository, externalInfoClient);
    }

    @Test
    void resolvePlaceForWrite_whenRowExists_skipsExternalInfo() {
        GooglePlaceEntity cached =
                GooglePlaceEntity.builder()
                        .googlePlaceId(PLACE_ID)
                        .placeName("Zürich")
                        .cityName("Zürich")
                        .formattedAddress("Zürich, Switzerland")
                        .latitude(47.37)
                        .longitude(8.54)
                        .countryCode("CH")
                        .updatedAt(Instant.parse("2026-01-01T00:00:00Z"))
                        .build();
        when(googlePlaceRepository.findById(PLACE_ID)).thenReturn(Optional.of(cached));

        GooglePlaceEntity result = placeService.resolvePlaceForWrite(PLACE_ID);

        assertThat(result).isSameAs(cached);
        verify(externalInfoClient, never()).fetchPlaceDetails(eq(PLACE_ID), anyBoolean());
        verify(googlePlaceRepository, never()).save(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void resolvePlaceForWrite_whenRowMissing_callsExternalInfoAndUpserts() {
        when(googlePlaceRepository.findById(PLACE_ID)).thenReturn(Optional.empty());
        PlaceDetailsResult details =
                new PlaceDetailsResult("Zürich", "Zürich", "Zürich, Switzerland", 47.37, 8.54, "CH");
        when(externalInfoClient.fetchPlaceDetails(PLACE_ID, true))
                .thenReturn(reactor.core.publisher.Mono.just(details));
        when(googlePlaceRepository.save(org.mockito.ArgumentMatchers.any(GooglePlaceEntity.class)))
                .thenAnswer(inv -> inv.getArgument(0));

        GooglePlaceEntity result = placeService.resolvePlaceForWrite(PLACE_ID);

        assertThat(result.getGooglePlaceId()).isEqualTo(PLACE_ID);
        assertThat(result.getPlaceName()).isEqualTo("Zürich");
        verify(externalInfoClient).fetchPlaceDetails(PLACE_ID, true);
    }
}
