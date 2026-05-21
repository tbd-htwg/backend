package com.tripplanning.config;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.tripplanning.trip.read.TripFeedCachedReader.TripFeedAuthorRaw;
import com.tripplanning.trip.read.TripFeedCachedReader.TripFeedDetailRaw;
import com.tripplanning.trip.read.TripFeedCachedReader.TripFeedItemRaw;
import com.tripplanning.trip.read.TripFeedCachedReader.TripFeedPageRaw;
import com.tripplanning.trip.read.TripFeedDtos.TripFeedAccommodation;

class CacheConfigRedisSerializerTest {

    @Test
    void tripRedisCacheValueSerializer_roundTripsFeedPageRaw() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var serializer = CacheConfig.tripRedisCacheValueSerializer(mapper);
        TripFeedPageRaw page =
                new TripFeedPageRaw(
                        List.of(
                                new TripFeedItemRaw(
                                        1L,
                                        "Trip",
                                        "Paris",
                                        LocalDate.of(2024, 1, 1),
                                        "desc",
                                        new TripFeedAuthorRaw(2L, "Alice", "avatars/a.jpg"),
                                        List.of("Paris"),
                                        List.of(),
                                        List.of())),
                        0,
                        10,
                        1L,
                        1);

        byte[] bytes = serializer.serialize(page);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(TripFeedPageRaw.class);
        assertThat(((TripFeedPageRaw) restored).items()).hasSize(1);
        assertThat(((TripFeedPageRaw) restored).items().getFirst().title()).isEqualTo("Trip");
    }

    @Test
    void tripRedisCacheValueSerializer_roundTripsDetailRawWithBigDecimal() {
        ObjectMapper mapper = new ObjectMapper().registerModule(new JavaTimeModule());
        var serializer = CacheConfig.tripRedisCacheValueSerializer(mapper);
        TripFeedDetailRaw detail =
                new TripFeedDetailRaw(
                        1L,
                        "Trip",
                        "Paris",
                        "place-id",
                        LocalDate.of(2024, 6, 1),
                        "short",
                        "long",
                        new TripFeedAuthorRaw(2L, "Alice", ""),
                        List.of(),
                        List.of(
                                new TripFeedAccommodation(
                                        10L,
                                        "hotel",
                                        "Hotel",
                                        "addr",
                                        "gp",
                                        "Paris",
                                        48.8,
                                        2.3,
                                        "FR",
                                        LocalDate.of(2024, 6, 1),
                                        LocalDate.of(2024, 6, 3),
                                        new BigDecimal("199.50"),
                                        "EUR")),
                        List.of());

        byte[] bytes = serializer.serialize(detail);
        Object restored = serializer.deserialize(bytes);

        assertThat(restored).isInstanceOf(TripFeedDetailRaw.class);
        assertThat(((TripFeedDetailRaw) restored).accommodations().getFirst().cost())
                .isEqualByComparingTo(new BigDecimal("199.50"));
    }
}
