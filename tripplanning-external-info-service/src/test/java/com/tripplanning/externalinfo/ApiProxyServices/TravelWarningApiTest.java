package com.tripplanning.externalinfo.ApiProxyServices;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class TravelWarningApiTest {

    @Test
    void parseCountryDisplayName_prefersGermanSegmentFromTitle() {
        assertEquals(
                "Vereinigte Staaten",
                TravelWarningApi.parseCountryDisplayName(
                        "USA/Vereinigte Staaten: Reise- und Sicherheitshinweise", "USA"));
    }

    @Test
    void parseCountryDisplayName_fallsBackToCountryName() {
        assertEquals(
                "Frankreich",
                TravelWarningApi.parseCountryDisplayName(
                        "Frankreich: Reise- und Sicherheitshinweise", "Frankreich"));
    }
}
