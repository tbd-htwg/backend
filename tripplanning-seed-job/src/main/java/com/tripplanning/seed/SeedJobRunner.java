package com.tripplanning.seed;

import java.util.List;
import java.util.Map;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Component
@RequiredArgsConstructor
@Slf4j
public class SeedJobRunner implements ApplicationRunner {

    private final SeedProperties seedProperties;
    private final Environment environment;
    private final SeedDataReset seedDataReset;
    private final TripSqlSeeder tripSqlSeeder;
    private final SocialFirestoreSeeder socialFirestoreSeeder;
    private final SeedOwnershipValidator ownershipValidator;
    private final ManifestWriter manifestWriter;

    @Override
    public void run(ApplicationArguments args) throws Exception {
        if (!seedProperties.enabled()) {
            log.warn("TRIPPLANNING_SEED_ENABLED is false; exiting without seeding.");
            return;
        }
        for (String profile : environment.getActiveProfiles()) {
            if ("prod".equalsIgnoreCase(profile) || "production".equalsIgnoreCase(profile)) {
                throw new IllegalStateException("Refusing to seed with production profile active");
            }
        }

        log.info("Starting perf dataset seed job...");
        seedDataReset.wipeAll();
        SeedContext ctx = tripSqlSeeder.seed();
        Map<Long, List<String>> commentIdsByUser = socialFirestoreSeeder.seed(ctx);
        ownershipValidator.validate(ctx);
        manifestWriter.write(ctx, commentIdsByUser);
        log.info("Seed job finished successfully.");
    }
}
