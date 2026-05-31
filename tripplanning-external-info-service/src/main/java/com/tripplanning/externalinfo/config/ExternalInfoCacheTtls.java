package com.tripplanning.externalinfo.config;

import java.time.Duration;

public final class ExternalInfoCacheTtls {

    public static final Duration PLACES = Duration.ofHours(1);
    public static final Duration VOLATILE = Duration.ofMinutes(5);

    private ExternalInfoCacheTtls() {}
}
