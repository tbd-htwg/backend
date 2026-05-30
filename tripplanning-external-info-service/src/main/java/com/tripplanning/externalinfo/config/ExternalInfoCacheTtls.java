package com.tripplanning.externalinfo.config;

import java.time.Duration;

public final class ExternalInfoCacheTtls {

    public static final Duration PLACES = Duration.ofDays(7);
    public static final Duration VOLATILE = Duration.ofDays(1);

    private ExternalInfoCacheTtls() {}
}
