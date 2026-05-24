package com.tripplanning.seed;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;

@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(
        properties = {
            "tripplanning.seed.enabled=true",
            "tripplanning.seed.wipe-before-seed=false",
            "tripplanning.seed.manifest-output-path=target/test-perf_seed_manifest.json",
            "spring.cloud.gcp.firestore.enabled=false"
        })
class SeedJobSmokeTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void seedsUsersTripsAndManifest() throws Exception {
        Long users = jdbc.queryForObject("SELECT COUNT(*) FROM users", Long.class);
        Long trips = jdbc.queryForObject("SELECT COUNT(*) FROM trips", Long.class);
        assertThat(users).isEqualTo(20);
        assertThat(trips).isEqualTo(50);

        String sampleTitle =
                jdbc.queryForObject("SELECT title FROM trips ORDER BY id LIMIT 1", String.class);
        assertThat(sampleTitle).contains("#").doesNotContain("Perf trip");

        String sampleStop =
                jdbc.queryForObject(
                        "SELECT description FROM trip_locations ORDER BY id LIMIT 1", String.class);
        assertThat(sampleStop).doesNotContain("Stop 1 for trip");
    }
}
