package com.tripplanning.search;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.tripplanning.trip.TripEntity;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ensures the trip full-text index is populated without delaying application readiness.
 * <p>
 * When the index already contains documents (typical on PaaS when a new instance attaches to an
 * existing Elasticsearch index), we only run a cheap hit count and skip mass indexing. Otherwise
 * mass indexing is started asynchronously so HTTP traffic is not blocked on startup.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchIndexInitializer {

    private final EntityManagerFactory entityManagerFactory;

    private final ExecutorService indexExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "trip-search-index");
        t.setDaemon(true);
        return t;
    });

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleIndexing(ApplicationReadyEvent ignored) {
        indexExecutor.submit(this::runIndexing);
    }

    private void runIndexing() {
        log.info("Trip search index: checking whether mass indexing is needed (non-blocking)...");
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            SearchSession searchSession = Search.session(em);

            Long existingHits = countIndexedTrips(searchSession);
            if (existingHits != null && existingHits > 0) {
                log.info(
                        "Trip search index already contains {} document(s); skipping mass indexer.",
                        existingHits);
                return;
            }

            log.info(
                    "Trip search index is empty or not queryable yet; starting mass indexer in background...");
            searchSession
                    .massIndexer(TripEntity.class)
                    .threadsToLoadObjects(4)
                    .purgeAllOnStart(false)
                    .dropAndCreateSchemaOnStart(false)
                    .start()
                    .whenComplete(
                            (unused, err) -> {
                                if (err != null) {
                                    log.error("Mass indexing of trips failed", err);
                                } else {
                                    log.info("Mass indexing of trips finished.");
                                }
                            });
        } catch (Exception e) {
            log.warn(
                    "Trip search index could not be checked or populated; search may be incomplete. Reason: {}",
                    e.toString(),
                    e);
        }
    }

    /**
     * @return number of indexed trip documents, or {@code null} if the index cannot be queried yet
     */
    private static Long countIndexedTrips(SearchSession searchSession) {
        try {
            return searchSession
                    .search(TripEntity.class)
                    .where(f -> f.matchAll())
                    .fetch(0, 0)
                    .total()
                    .hitCount();
        } catch (RuntimeException e) {
            log.debug("Trip index hit count failed (index may be missing): {}", e.getMessage());
            return null;
        }
    }

    @PreDestroy
    public void shutdownExecutor() {
        indexExecutor.shutdown();
        try {
            if (!indexExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
                indexExecutor.shutdownNow();
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            indexExecutor.shutdownNow();
        }
    }
}
