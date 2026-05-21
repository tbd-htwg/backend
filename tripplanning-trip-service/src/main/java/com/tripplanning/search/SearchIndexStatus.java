package com.tripplanning.search;

/** Snapshot of trip full-text index bootstrap state (shared across pods via Redis when available). */
public record SearchIndexStatus(
        String state,
        long databaseTrips,
        long indexedTrips,
        boolean indexingInProgress,
        boolean lockHeldByThisPod,
        String lockOwner,
        String message) {

    public static final String STATE_READY = "READY";
    public static final String STATE_INDEXING = "INDEXING";
    public static final String STATE_EMPTY = "EMPTY";
    public static final String STATE_STALE = "STALE";

    public boolean isReady() {
        return STATE_READY.equals(state);
    }
}
