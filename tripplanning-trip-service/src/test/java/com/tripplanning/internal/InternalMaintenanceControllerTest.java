package com.tripplanning.internal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import com.tripplanning.search.SearchIndexCoordinationService;
import com.tripplanning.search.SearchIndexStatus;
import com.tripplanning.search.SearchReconcileResult;

/** Verifies the internal reconcile endpoint maps reconcile outcomes to HTTP status codes. */
class InternalMaintenanceControllerTest {

    private final SearchIndexCoordinationService service =
            mock(SearchIndexCoordinationService.class);
    private final InternalMaintenanceController controller =
            new InternalMaintenanceController(service);

    private static SearchIndexStatus status(String state, long db, long es) {
        return new SearchIndexStatus(state, db, es, false, false, null, state);
    }

    @Test
    void reconciled_returns200() {
        SearchIndexStatus before = status(SearchIndexStatus.STATE_STALE, 5, 2);
        SearchIndexStatus after = status(SearchIndexStatus.STATE_READY, 5, 5);
        when(service.reconcile(anyBoolean()))
                .thenReturn(
                        new SearchReconcileResult(
                                SearchReconcileResult.ACTION_RECONCILED, true, before, after, "done"));

        ResponseEntity<SearchReconcileResult> response = controller.searchReconcile(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().action()).isEqualTo(SearchReconcileResult.ACTION_RECONCILED);
    }

    @Test
    void skipped_returns200() {
        SearchIndexStatus ready = status(SearchIndexStatus.STATE_READY, 5, 5);
        when(service.reconcile(anyBoolean()))
                .thenReturn(
                        new SearchReconcileResult(
                                SearchReconcileResult.ACTION_SKIPPED, false, ready, ready, "noop"));

        ResponseEntity<SearchReconcileResult> response = controller.searchReconcile(false);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
    }

    @Test
    void conflict_returns409() {
        SearchIndexStatus indexing = status(SearchIndexStatus.STATE_INDEXING, 5, 2);
        when(service.reconcile(anyBoolean()))
                .thenReturn(
                        new SearchReconcileResult(
                                SearchReconcileResult.ACTION_CONFLICT, true, indexing, indexing, "busy"));

        ResponseEntity<SearchReconcileResult> response = controller.searchReconcile(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void failure_returns500() {
        SearchIndexStatus before = status(SearchIndexStatus.STATE_STALE, 5, 2);
        when(service.reconcile(anyBoolean()))
                .thenReturn(
                        new SearchReconcileResult(
                                SearchReconcileResult.ACTION_FAILED, true, before, before, "boom"));

        ResponseEntity<SearchReconcileResult> response = controller.searchReconcile(true);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.INTERNAL_SERVER_ERROR);
    }
}
