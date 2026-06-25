package com.tripplanning.search;

/**
 * Outcome of a manual {@link SearchIndexCoordinationService#reconcile(boolean)} run, returned to the
 * internal maintenance endpoint so callers (e.g. a CronJob) can log the before/after index state.
 */
public record SearchReconcileResult(
        String action,
        boolean force,
        SearchIndexStatus before,
        SearchIndexStatus after,
        String message) {

    public static final String ACTION_SKIPPED = "SKIPPED";
    public static final String ACTION_RECONCILED = "RECONCILED";
    public static final String ACTION_CONFLICT = "CONFLICT";
    public static final String ACTION_FAILED = "FAILED";

    public boolean isConflict() {
        return ACTION_CONFLICT.equals(action);
    }

    public boolean isFailure() {
        return ACTION_FAILED.equals(action);
    }
}
