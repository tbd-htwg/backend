package com.tripplanning.seed;

import java.math.BigDecimal;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.tripplanning.seed.assets.DatasetSpec;
import com.tripplanning.seed.assets.PrefetchedPlace;
import com.tripplanning.seed.assets.SampleImageRow;
import com.tripplanning.seed.assets.SeedAssetLoader;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class TripSqlSeeder {

    /** Radii tried in order until at least two places are found near the anchor. */
    private static final double[] TRANSPORT_RADIUS_KM = {80, 150, 300};

    private final JdbcTemplate jdbc;
    private final SeedAssetLoader assetLoader;

    @Transactional(transactionManager = "transactionManager")
    public SeedContext seed() throws Exception {
        DatasetSpec spec = assetLoader.loadDatasetSpec();
        List<PrefetchedPlace> places = assetLoader.loadPlaces();
        List<SampleImageRow> images = assetLoader.loadSampleImages();
        if (places.isEmpty()) {
            throw new IllegalStateException("google-places.json is empty");
        }
        if (images.isEmpty()) {
            throw new IllegalStateException("sample-images.csv is empty");
        }

        Random rng = new Random(42);
        insertGooglePlaces(places);
        insertUsers(spec.totalUsers());
        resetSequence("users", spec.totalUsers());

        SeedContext ctx = new SeedContext();
        List<Integer> tripsPerUser =
                TripDistribution.tripsPerUser(
                        spec.totalUsers(),
                        spec.totalTrips(),
                        spec.tripsPerUserMin(),
                        spec.tripsPerUserMax(),
                        rng);

        Map<String, List<String>> imagesByCategory = groupImagesByCategory(images);
        List<String> categories = new ArrayList<>(imagesByCategory.keySet());
        int tripCounter = 0;

        for (int userIdx = 0; userIdx < spec.totalUsers(); userIdx++) {
            long userId = userIdx + 1L;
            int numTrips = tripsPerUser.get(userIdx);
            for (int t = 0; t < numTrips; t++) {
                tripCounter++;
                PrefetchedPlace dest = places.get(rng.nextInt(places.size()));
                PerfSeedText.TripTopic topic = PerfSeedText.pickTripTopic(rng);
                String title = PerfSeedText.tripTitle(topic, tripCounter);
                long tripId =
                        insertTrip(
                                userId,
                                title,
                                topic.shortDescription(),
                                PerfSeedText.tripLongDescription(rng),
                                dest,
                                LocalDate.now().plusDays(7 + (tripCounter % 90)));
                ctx.addTrip(userId, tripId);

                List<String> stopLines =
                        PerfSeedText.stopDescriptions(rng, spec.tripLocationsPerTrip());
                for (int s = 0; s < spec.tripLocationsPerTrip(); s++) {
                    PrefetchedPlace stop = places.get(rng.nextInt(places.size()));
                    long stopId =
                            insertTripLocation(
                                    tripId,
                                    stop,
                                    stopLines.get(s),
                                    LocalDateTime.now().plusDays(s).plusHours(10),
                                    LocalDateTime.now().plusDays(s + 1).plusHours(18));
                    attachImages(
                            stopId,
                            imagesByCategory,
                            categories,
                            tripCounter,
                            s,
                            spec,
                            rng);
                }

                for (int a = 0; a < spec.accommodationsPerTrip(); a++) {
                    PrefetchedPlace hotel = places.get(rng.nextInt(places.size()));
                    long accomId = insertAccommodation(hotel, tripCounter + a);
                    linkTripAccommodation(tripId, accomId);
                }

                for (int tr = 0; tr < spec.transportsPerTrip(); tr++) {
                    PrefetchedPlace[] endpoints = pickTransportEndpoints(places, dest, rng);
                    long transportId = insertTransport(endpoints[0], endpoints[1]);
                    linkTripTransport(tripId, transportId);
                }
            }
        }

        resetSequence("trips", ctx.tripIdMax());
        resetSequence("trip_locations", maxId("trip_locations"));
        resetSequence("accommodation", maxId("accommodation"));
        resetSequence("transport", maxId("transport"));
        resetSequence("trip_location_images", maxId("trip_location_images"));

        log.info(
                "SQL seed complete: users={}, trips={}",
                spec.totalUsers(),
                ctx.allTripIds().size());
        return ctx;
    }

    private void insertGooglePlaces(List<PrefetchedPlace> places) {
        Instant now = Instant.now();
        boolean postgres = isPostgres();
        String sql =
                postgres
                        ? """
                        INSERT INTO google_places (
                          google_place_id, place_name, city_name, formatted_address,
                          latitude, longitude, country_code, updated_at
                        ) VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        ON CONFLICT (google_place_id) DO UPDATE SET
                          place_name = EXCLUDED.place_name,
                          city_name = EXCLUDED.city_name,
                          formatted_address = EXCLUDED.formatted_address,
                          latitude = EXCLUDED.latitude,
                          longitude = EXCLUDED.longitude,
                          country_code = EXCLUDED.country_code,
                          updated_at = EXCLUDED.updated_at
                        """
                        : """
                        MERGE INTO google_places (
                          google_place_id, place_name, city_name, formatted_address,
                          latitude, longitude, country_code, updated_at
                        ) KEY (google_place_id)
                        VALUES (?, ?, ?, ?, ?, ?, ?, ?)
                        """;
        jdbc.batchUpdate(
                sql,
                places,
                200,
                (ps, p) -> {
                    ps.setString(1, p.googlePlaceId());
                    ps.setString(2, p.placeName());
                    ps.setString(3, p.cityName());
                    ps.setString(4, p.formattedAddress());
                    ps.setDouble(5, p.latitude());
                    ps.setDouble(6, p.longitude());
                    ps.setString(7, p.countryCode());
                    ps.setTimestamp(8, Timestamp.from(now));
                });
    }

    private void insertUsers(int totalUsers) {
        mergeUser(
                1L,
                "test@example.com",
                "test",
                "https://picsum.photos/seed/test-user/300/300",
                "Smoke-test account for perf seed.");
        Random userRng = new Random(41);
        for (int i = 2; i <= totalUsers; i++) {
            String name = String.format("SeededUser%05d", i - 1);
            mergeUser(
                    i,
                    "seed-user-" + i + "@perf.example.com",
                    name,
                    "https://picsum.photos/seed/user-" + i + "/300/300",
                    PerfSeedText.userDescription(userRng, i));
        }
    }

    private void mergeUser(long id, String email, String name, String imagePath, String description) {
        if (isPostgres()) {
            jdbc.update(
                    """
                    INSERT INTO users (id, email, name, image_path, description)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT (id) DO NOTHING
                    """,
                    id,
                    email,
                    name,
                    imagePath,
                    description);
        } else {
            jdbc.update(
                    """
                    MERGE INTO users (id, email, name, image_path, description)
                    KEY (id) VALUES (?, ?, ?, ?, ?)
                    """,
                    id,
                    email,
                    name,
                    imagePath,
                    description);
        }
    }

    private boolean isPostgres() {
        String version = jdbc.queryForObject("SELECT LOWER(version())", String.class);
        return version != null && version.contains("postgresql") && !version.contains("h2");
    }

    private long insertTrip(
            long userId,
            String title,
            String shortDescription,
            String longDescription,
            PrefetchedPlace dest,
            LocalDate startDate) {
        jdbc.update(
                """
                INSERT INTO trips (
                  user_id, title, destination, destination_google_place_id,
                  start_date, short_description, long_description
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                userId,
                title,
                dest.placeName(),
                dest.googlePlaceId(),
                startDate,
                shortDescription.substring(0, Math.min(80, shortDescription.length())),
                longDescription);
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM trips", Long.class);
        return id != null ? id : 0L;
    }

    private long insertTripLocation(
            long tripId,
            PrefetchedPlace place,
            String description,
            LocalDateTime start,
            LocalDateTime end) {
        jdbc.update(
                """
                INSERT INTO trip_locations (
                  trip_id, google_place_id, place_name, city_name,
                  description, start_date, end_date
                ) VALUES (?, ?, ?, ?, ?, ?, ?)
                """,
                tripId,
                place.googlePlaceId(),
                place.placeName(),
                place.cityName(),
                description,
                Timestamp.valueOf(start),
                Timestamp.valueOf(end));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM trip_locations WHERE trip_id = ?", Long.class, tripId);
        return id != null ? id : 0L;
    }

    private void attachImages(
            long stopId,
            Map<String, List<String>> imagesByCategory,
            List<String> categories,
            int tripIdx,
            int stopOffset,
            DatasetSpec spec,
            Random rng) {
        if (categories.isEmpty()) {
            return;
        }
        String category = categories.get((tripIdx * 31 + stopOffset * 7) % categories.size());
        List<String> pool = imagesByCategory.get(category);
        if (pool == null || pool.isEmpty()) {
            return;
        }
        int count =
                spec.imagePathsPerStopMin()
                        + rng.nextInt(
                                Math.max(
                                        1,
                                        spec.imagePathsPerStopMax() - spec.imagePathsPerStopMin() + 1));
        for (int i = 0; i < count; i++) {
            String path = pool.get(rng.nextInt(pool.size()));
            jdbc.update(
                    "INSERT INTO trip_location_images (trip_location_id, image_path) VALUES (?, ?)",
                    stopId,
                    path);
        }
    }

    private long insertAccommodation(PrefetchedPlace place, int seed) {
        LocalDate in = LocalDate.now().plusDays(10 + (seed % 30));
        jdbc.update(
                """
                INSERT INTO accommodation (
                  type, name, google_place_id, city_name, address,
                  check_in_date, check_out_date, cost, currency
                ) VALUES ('hotel', ?, ?, ?, ?, ?, ?, ?, 'EUR')
                """,
                place.placeName() + " Hotel",
                place.googlePlaceId(),
                place.cityName(),
                place.formattedAddress(),
                in,
                in.plusDays(2),
                BigDecimal.valueOf(80 + (seed % 200)));
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM accommodation", Long.class);
        return id != null ? id : 0L;
    }

    private void linkTripAccommodation(long tripId, long accomId) {
        jdbc.update("INSERT INTO trip_accommodation (trip_id, accom_id) VALUES (?, ?)", tripId, accomId);
    }

    private long insertTransport(PrefetchedPlace start, PrefetchedPlace end) {
        jdbc.update(
                """
                INSERT INTO transport (
                  start_google_place_id, end_google_place_id, start_address, end_address
                ) VALUES (?, ?, ?, ?)
                """,
                start.googlePlaceId(),
                end.googlePlaceId(),
                start.formattedAddress(),
                end.formattedAddress());
        Long id = jdbc.queryForObject("SELECT MAX(id) FROM transport", Long.class);
        return id != null ? id : 0L;
    }

    private void linkTripTransport(long tripId, long transportId) {
        jdbc.update("INSERT INTO trip_transport (trip_id, transport_id) VALUES (?, ?)", tripId, transportId);
    }

    /** Picks two distinct places close enough for Google Routes to return a real route. */
    private PrefetchedPlace[] pickTransportEndpoints(
            List<PrefetchedPlace> places, PrefetchedPlace anchor, Random rng) {
        for (double radiusKm : TRANSPORT_RADIUS_KM) {
            List<PrefetchedPlace> nearby = placesWithinRadius(places, anchor, radiusKm);
            if (nearby.size() >= 2) {
                return pickTwoDistinct(nearby, rng);
            }
        }
        List<PrefetchedPlace> sameCountry = new ArrayList<>();
        for (PrefetchedPlace place : places) {
            if (anchor.countryCode().equals(place.countryCode())) {
                sameCountry.add(place);
            }
        }
        if (sameCountry.size() >= 2) {
            return pickTwoDistinct(sameCountry, rng);
        }
        return pickTwoDistinct(places, rng);
    }

    private List<PrefetchedPlace> placesWithinRadius(
            List<PrefetchedPlace> places, PrefetchedPlace anchor, double radiusKm) {
        List<PrefetchedPlace> nearby = new ArrayList<>();
        for (PrefetchedPlace place : places) {
            if (distanceKm(anchor, place) <= radiusKm) {
                nearby.add(place);
            }
        }
        return nearby;
    }

    private PrefetchedPlace[] pickTwoDistinct(List<PrefetchedPlace> pool, Random rng) {
        PrefetchedPlace start = pool.get(rng.nextInt(pool.size()));
        PrefetchedPlace end = start;
        for (int attempt = 0;
                attempt < 8 && end.googlePlaceId().equals(start.googlePlaceId());
                attempt++) {
            end = pool.get(rng.nextInt(pool.size()));
        }
        if (end.googlePlaceId().equals(start.googlePlaceId())) {
            for (PrefetchedPlace candidate : pool) {
                if (!candidate.googlePlaceId().equals(start.googlePlaceId())) {
                    end = candidate;
                    break;
                }
            }
        }
        return new PrefetchedPlace[] {start, end};
    }

    private static double distanceKm(PrefetchedPlace a, PrefetchedPlace b) {
        double lat1 = Math.toRadians(a.latitude());
        double lat2 = Math.toRadians(b.latitude());
        double dLat = lat2 - lat1;
        double dLon = Math.toRadians(b.longitude() - a.longitude());
        double sinLat = Math.sin(dLat / 2);
        double sinLon = Math.sin(dLon / 2);
        double h = sinLat * sinLat + Math.cos(lat1) * Math.cos(lat2) * sinLon * sinLon;
        return 6371.0 * 2 * Math.asin(Math.min(1.0, Math.sqrt(h)));
    }

    private Map<String, List<String>> groupImagesByCategory(List<SampleImageRow> images) {
        Map<String, List<String>> out = new HashMap<>();
        for (SampleImageRow row : images) {
            out.computeIfAbsent(row.category(), k -> new ArrayList<>()).add(row.imagePath());
        }
        return out;
    }

    private long maxId(String table) {
        Long v = jdbc.queryForObject("SELECT COALESCE(MAX(id), 0) FROM " + table, Long.class);
        return v != null ? v : 0L;
    }

    private void resetSequence(String table, long maxId) {
        if (!isPostgres()) {
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN id RESTART WITH " + (maxId + 1));
            return;
        }
        try {
            jdbc.queryForObject(
                    "SELECT setval(pg_get_serial_sequence(?, 'id'), ?)",
                    Long.class,
                    table,
                    maxId);
        } catch (Exception e) {
            log.debug("Sequence reset skipped for {}: {}", table, e.getMessage());
        }
    }
}
