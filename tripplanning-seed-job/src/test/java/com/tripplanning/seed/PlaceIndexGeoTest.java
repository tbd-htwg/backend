package com.tripplanning.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import com.tripplanning.seed.assets.PrefetchedPlace;

class PlaceIndexGeoTest {

    @Test
    void pickDestinationForTopicPrefersMatchingCountry() {
        PrefetchedPlace zurich =
                new PrefetchedPlace("ch1", "Zurich", "Zurich", "Zurich, CH", 47.3769, 8.5417, "CH", "CITY");
        PrefetchedPlace bangkok =
                new PrefetchedPlace("th1", "Bangkok", "Bangkok", "Bangkok, TH", 13.7563, 100.5018, "TH", "CITY");
        PlaceIndex index = new PlaceIndex(java.util.List.of(zurich, bangkok));

        Random rng = new Random(7);
        PrefetchedPlace picked =
                index.pickDestinationForTopic(
                        java.util.Set.of("CH", "AT", "FR"), rng);

        assertThat(picked.countryCode()).isIn("CH", "AT", "FR");
    }

    @Test
    void tripPoolKeepsNearbyPlacesTogether() {
        PrefetchedPlace nyc =
                new PrefetchedPlace("nyc", "New York", "New York", "NY, US", 40.7128, -74.0060, "US", "CITY");
        PrefetchedPlace brooklyn =
                new PrefetchedPlace(
                        "bk", "Brooklyn Cafe", "Brooklyn", "NY, US", 40.6782, -73.9442, "US", "CAFE");
        PrefetchedPlace hoboken =
                new PrefetchedPlace(
                        "hb", "Hoboken Bistro", "Hoboken", "NJ, US", 40.7439, -74.0324, "US", "RESTAURANT");
        PrefetchedPlace losAngeles =
                new PrefetchedPlace("la", "Los Angeles", "Los Angeles", "CA, US", 34.0522, -118.2437, "US", "CITY");
        List<PrefetchedPlace> all = List.of(nyc, brooklyn, hoboken, losAngeles);

        List<PrefetchedPlace> pool = buildTripPool(all, nyc);
        assertThat(pool).contains(nyc, brooklyn, hoboken);
        assertThat(pool).doesNotContain(losAngeles);
    }

    @Test
    void placeIndexFindsPoiStopsInPool() {
        PrefetchedPlace dest =
                new PrefetchedPlace("d", "Zurich", "Zurich", "Zurich, CH", 47.3769, 8.5417, "CH", "CITY");
        PrefetchedPlace cafe =
                new PrefetchedPlace("c", "Zurich Cafe", "Zurich", "Zurich, CH", 47.3775, 8.5420, "CH", "CAFE");
        PrefetchedPlace museum =
                new PrefetchedPlace("m", "Zurich Museum", "Zurich", "Zurich, CH", 47.3780, 8.5430, "CH", "MUSEUM");
        PrefetchedPlace bern =
                new PrefetchedPlace("b", "Bern", "Bern", "Bern, CH", 46.9480, 7.4474, "CH", "CITY");
        PlaceIndex index = new PlaceIndex(List.of(dest, cafe, museum, bern));
        List<PrefetchedPlace> pool = buildTripPool(index.all(), dest);
        List<PrefetchedPlace> poi = index.poiInPool(pool);
        assertThat(poi).extracting(PrefetchedPlace::googlePlaceId).contains("c", "m");
    }

    /** Mirrors TripSqlSeeder.buildTripPool for unit testing without Spring context. */
    private static List<PrefetchedPlace> buildTripPool(List<PrefetchedPlace> places, PrefetchedPlace anchor) {
        double[] radii = {15, 30, 50, 80};
        for (double radiusKm : radii) {
            List<PrefetchedPlace> nearby = new ArrayList<>();
            for (PrefetchedPlace place : places) {
                if (distanceKm(anchor, place) <= radiusKm) {
                    nearby.add(place);
                }
            }
            if (nearby.size() >= 3) {
                return nearby;
            }
        }
        List<PrefetchedPlace> sameCountry = new ArrayList<>();
        for (PrefetchedPlace place : places) {
            if (anchor.countryCode().equals(place.countryCode())) {
                sameCountry.add(place);
            }
        }
        if (sameCountry.size() >= 3) {
            return sameCountry;
        }
        return places;
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
}
