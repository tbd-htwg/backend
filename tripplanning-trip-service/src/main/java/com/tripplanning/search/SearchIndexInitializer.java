package com.tripplanning.search;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Schedules {@link SearchIndexCoordinationService} after startup without blocking HTTP listeners. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SearchIndexInitializer {

    private final SearchIndexCoordinationService coordinationService;

    private final ExecutorService indexExecutor = Executors.newSingleThreadExecutor(r -> {
        Thread t = new Thread(r, "trip-search-index");
        t.setDaemon(true);
        return t;
    });

    @EventListener(ApplicationReadyEvent.class)
    public void scheduleIndexing(ApplicationReadyEvent ignored) {
        indexExecutor.submit(
                () -> {
                    log.info("Trip search index bootstrap started (non-blocking).");
                    coordinationService.ensureIndexPopulated();
                });
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
