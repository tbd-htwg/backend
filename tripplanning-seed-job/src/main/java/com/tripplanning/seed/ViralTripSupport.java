package com.tripplanning.seed;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Random;

/** Helpers for viral trip social seeding. */
final class ViralTripSupport {

    private ViralTripSupport() {}

    static List<Long> viralTripIds(List<Long> allTripIds, int interval) {
        List<Long> viral = new ArrayList<>();
        for (Long tripId : allTripIds) {
            if (tripId % interval == 0) {
                viral.add(tripId);
            }
        }
        Collections.sort(viral);
        return viral;
    }

    static long canonicalViralTripId(List<Long> viralTripIds) {
        if (viralTripIds.isEmpty()) {
            return 0L;
        }
        return viralTripIds.get(viralTripIds.size() / 2);
    }

    static List<Long> sampleDistinctUserIds(int totalUsers, int count, Random rng) {
        List<Long> ids = new ArrayList<>();
        for (long userId = 1; userId <= totalUsers; userId++) {
            ids.add(userId);
        }
        Collections.shuffle(ids, rng);
        return ids.subList(0, Math.min(count, ids.size()));
    }
}
