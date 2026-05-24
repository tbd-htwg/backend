package com.tripplanning.seed;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/** Mutable state produced by SQL seeding and consumed by Firestore + manifest. */
public final class SeedContext {

    private final Map<Long, List<Long>> tripIdsByUser = new HashMap<>();
    private final List<Long> allTripIds = new ArrayList<>();
    private long tripIdMin = Long.MAX_VALUE;
    private long tripIdMax = Long.MIN_VALUE;

    public void addTrip(long userId, long tripId) {
        tripIdsByUser.computeIfAbsent(userId, k -> new ArrayList<>()).add(tripId);
        allTripIds.add(tripId);
        tripIdMin = Math.min(tripIdMin, tripId);
        tripIdMax = Math.max(tripIdMax, tripId);
    }

    public Map<Long, List<Long>> tripIdsByUser() {
        return tripIdsByUser;
    }

    public List<Long> allTripIds() {
        return allTripIds;
    }

    public long tripIdMin() {
        return tripIdMin == Long.MAX_VALUE ? 0 : tripIdMin;
    }

    public long tripIdMax() {
        return tripIdMax == Long.MIN_VALUE ? 0 : tripIdMax;
    }

    public List<Long> tripIdsForUser(long userId) {
        return tripIdsByUser.getOrDefault(userId, List.of());
    }
}
