package com.tripplanning.internal;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.tripplanning.search.SearchIndexCoordinationService;
import com.tripplanning.search.SearchReconcileResult;

import lombok.RequiredArgsConstructor;

/**
 * Internal maintenance operations, secured by {@code X-Internal-Secret} via the global internal API
 * filter. Intended for scheduled CronJobs, not public traffic.
 */
@RestController
@RequestMapping("/internal/maintenance")
@RequiredArgsConstructor
public class InternalMaintenanceController {

    private final SearchIndexCoordinationService searchIndexCoordination;

    /**
     * Triggers a search-index reconcile. Defaults to {@code force=true} for a full rebuild (used by
     * the nightly CronJob); pass {@code force=false} to only rebuild on count drift.
     */
    @PostMapping("/search-reconcile")
    public ResponseEntity<SearchReconcileResult> searchReconcile(
            @RequestParam(name = "force", defaultValue = "true") boolean force) {
        SearchReconcileResult result = searchIndexCoordination.reconcile(force);
        HttpStatus status =
                result.isConflict()
                        ? HttpStatus.CONFLICT
                        : result.isFailure() ? HttpStatus.INTERNAL_SERVER_ERROR : HttpStatus.OK;
        return ResponseEntity.status(status).body(result);
    }
}
