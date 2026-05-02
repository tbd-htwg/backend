package com.tripplanning.search;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import com.tripplanning.accommodation.AccomEntity;
import com.tripplanning.location.LocationEntity;
import com.tripplanning.transport.TransportEntity;
import com.tripplanning.trip.TripEntity;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ensures Hibernate Search indexes are populated without delaying application readiness.
 * <p>
 * Each indexed entity type is checked independently: if its index already has documents, mass
 * indexing for that type is skipped.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchIndexInitializer {

    private static final List<Class<?>> INDEXED_TYPES = List.of(
            TripEntity.class, LocationEntity.class, TransportEntity.class, AccomEntity.class);

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
        log.info("Search indexes: checking whether mass indexing is needed (non-blocking)...");
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            SearchSession searchSession = Search.session(em);
            for (Class<?> type : INDEXED_TYPES) {
                Long existingHits = countIndexed(searchSession, type);
                if (existingHits != null && existingHits > 0) {
                    log.info(
                            "Index {} already contains {} document(s); skipping mass indexer.",
                            type.getSimpleName(),
                            existingHits);
                    continue;
                }
                log.info(
                        "Index {} is empty or not queryable yet; mass indexing...",
                        type.getSimpleName());
                try {
                    searchSession
                            .massIndexer(type)
                            .threadsToLoadObjects(4)
                            .purgeAllOnStart(false)
                            .dropAndCreateSchemaOnStart(false)
                            .start()
                            .toCompletableFuture()
                            .join();
                    log.info("Mass indexing finished for {}.", type.getSimpleName());
                } catch (Exception e) {
                    log.error("Mass indexing failed for {}", type.getSimpleName(), e);
                }
            }
        } catch (Exception e) {
            log.warn(
                    "Search indexes could not be checked or populated; search may be incomplete. Reason: {}",
                    e.toString(),
                    e);
        }
    }

    private static Long countIndexed(SearchSession searchSession, Class<?> type) {
        try {
            return searchSession
                    .search(type)
                    .where(f -> f.matchAll())
                    .fetch(0, 0)
                    .total()
                    .hitCount();
        } catch (RuntimeException e) {
            log.debug(
                    "Index hit count failed for {} (index may be missing): {}",
                    type.getSimpleName(),
                    e.getMessage());
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
