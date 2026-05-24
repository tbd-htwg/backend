package com.tripplanning.seed;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "tripplanning.seed")
public record SeedProperties(
        boolean enabled,
        boolean wipeBeforeSeed,
        String manifestOutputPath,
        String copyManifestTo) {}
