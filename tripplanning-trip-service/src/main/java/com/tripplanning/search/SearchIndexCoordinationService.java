package com.tripplanning.search;

import java.time.Duration;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.hibernate.search.mapper.orm.Search;
import org.hibernate.search.mapper.orm.massindexing.MassIndexer;
import org.hibernate.search.mapper.orm.session.SearchSession;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import com.tripplanning.common.tenant.TenantContextHolder;
import com.tripplanning.trip.TripEntity;
import com.tripplanning.trip.TripRepository;

import jakarta.annotation.PreDestroy;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import lombok.extern.slf4j.Slf4j;

/**
 * Coordinates Hibernate Search mass indexing across trip-service pods using a Redis lock when
 * available. New pods skip re-indexing when Elasticsearch already matches PostgreSQL; only one pod
 * rebuilds on cold start or after partial index loss.
 */
@Service
@Slf4j
public class SearchIndexCoordinationService {

    private static final Duration LOCK_TTL = Duration.ofMinutes(30);
    private static final Duration STATUS_TTL = Duration.ofHours(24);

    private final EntityManagerFactory entityManagerFactory;
    private final TripRepository tripRepository;
    private final String instanceId;
    private final String lockKey;
    private final String statusKey;
    private final Optional<StringRedisTemplate> redis;
    private final AtomicBoolean localIndexing = new AtomicBoolean(false);
    private final boolean tenantRoutingEnabled;

    public SearchIndexCoordinationService(
            EntityManagerFactory entityManagerFactory,
            TripRepository tripRepository,
            @Value("${tripplanning.search.lock-key:tripplanning:search:index:lock}") String lockKey,
            @Value("${tripplanning.search.status-key:tripplanning:search:index:status}") String statusKey,
            @Value("${tripplanning.tenant.datasource-routing.enabled:false}") boolean tenantRoutingEnabled,
            @Autowired(required = false) StringRedisTemplate redisTemplate) {
        this.entityManagerFactory = entityManagerFactory;
        this.tripRepository = tripRepository;
        this.lockKey = lockKey;
        this.statusKey = statusKey;
        this.tenantRoutingEnabled = tenantRoutingEnabled;
        this.redis = Optional.ofNullable(redisTemplate);
        this.instanceId = defaultInstanceId();
    }

    public SearchIndexStatus currentStatus() {
        long dbCount = tripRepository.count();
        long esCount = countIndexedTripsSafe();
        boolean indexing =
                localIndexing.get() || SearchIndexStatus.STATE_INDEXING.equals(readSharedState());
        String lockOwner = redis.map(r -> r.opsForValue().get(scopedLockKey())).orElse(null);
        boolean lockHeldHere = instanceId.equals(lockOwner);
        String state = resolveState(dbCount, esCount, indexing);
        String message = describe(state, dbCount, esCount, lockOwner);
        return new SearchIndexStatus(
                state, dbCount, esCount, indexing, lockHeldHere, lockOwner, message);
    }

    public boolean isReadyForTraffic() {
        return currentStatus().isReady();
    }

    /**
     * Runs once per pod after application ready: skip, wait for another pod, or mass-index under
     * lock.
     */
    public void ensureIndexPopulated() {
        if (tenantRoutingEnabled) {
            log.info(
                    "Tenant datasource routing enabled; skipping global search index bootstrap.");
            return;
        }
        ensureIndexPopulatedForCurrentTenant();
    }

    /** Mass-indexes the current tenant database into the routed search index. */
    public void ensureIndexPopulatedForCurrentTenant() {
        SearchIndexStatus initial = currentStatus();
        if (initial.isReady()) {
            publishSharedState(SearchIndexStatus.STATE_READY);
            log.info(
                    "Trip search index ready ({} indexed, {} in database); no mass indexing.",
                    initial.indexedTrips(),
                    initial.databaseTrips());
            return;
        }

        if (!tryAcquireLock()) {
            waitForPeerIndexing();
            return;
        }

        localIndexing.set(true);
        publishSharedState(SearchIndexStatus.STATE_INDEXING);
        try {
            log.info(
                    "This pod ({}) owns the search index lock; starting mass indexer (db={}, es={}).",
                    instanceId,
                    initial.databaseTrips(),
                    initial.indexedTrips());
            runMassIndexer(initial.databaseTrips());
            publishSharedState(SearchIndexStatus.STATE_READY);
            log.info("Mass indexing finished; search index marked READY.");
        } catch (Exception e) {
            log.error("Mass indexing failed", e);
            publishSharedState(SearchIndexStatus.STATE_STALE);
        } finally {
            localIndexing.set(false);
            releaseLock();
        }
    }

    @PreDestroy
    void releaseLockOnShutdown() {
        releaseLock();
    }

    private void waitForPeerIndexing() {
        log.info("Another pod is indexing or index is catching up; waiting for READY status...");
        for (int attempt = 0; attempt < 120; attempt++) {
            if (currentStatus().isReady()) {
                log.info("Trip search index became ready while waiting (attempt {}).", attempt + 1);
                return;
            }
            sleepSeconds(5);
        }
        log.warn(
                "Timed out waiting for peer indexing; pod will become ready when counts match (db vs es).");
    }

    private void runMassIndexer(long databaseTrips) throws InterruptedException {
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            SearchSession searchSession = Search.session(em);
            MassIndexer indexer =
                    searchSession
                            .massIndexer(TripEntity.class)
                            .threadsToLoadObjects(2)
                            // Drop orphaned index docs when PostgreSQL is empty (common after H2 reset).
                            .purgeAllOnStart(databaseTrips == 0)
                            .dropAndCreateSchemaOnStart(false);
            indexer.startAndWait();
        }
    }

    private String scopedLockKey() {
        if (!tenantRoutingEnabled) {
            return lockKey;
        }
        return lockKey + ":" + TenantContextHolder.slugOrDefault();
    }

    private String scopedStatusKey() {
        if (!tenantRoutingEnabled) {
            return statusKey;
        }
        return statusKey + ":" + TenantContextHolder.slugOrDefault();
    }

    private boolean tryAcquireLock() {
        String scopedLock = scopedLockKey();
        return redis
                .map(
                        r ->
                                Boolean.TRUE.equals(
                                        r.opsForValue()
                                                .setIfAbsent(
                                                        scopedLock, instanceId, LOCK_TTL)))
                .orElseGet(() -> localIndexing.compareAndSet(false, true));
    }

    private void releaseLock() {
        String scopedLock = scopedLockKey();
        redis.ifPresent(
                r -> {
                    String owner = r.opsForValue().get(scopedLock);
                    if (instanceId.equals(owner)) {
                        r.delete(scopedLock);
                    }
                });
    }

    private void publishSharedState(String state) {
        String scopedStatus = scopedStatusKey();
        redis.ifPresent(
                r -> r.opsForValue().set(scopedStatus, state, STATUS_TTL));
    }

    private String readSharedState() {
        return redis.map(r -> r.opsForValue().get(scopedStatusKey())).orElse(null);
    }

    private long countIndexedTripsSafe() {
        try (EntityManager em = entityManagerFactory.createEntityManager()) {
            SearchSession searchSession = Search.session(em);
            return searchSession
                    .search(TripEntity.class)
                    .where(f -> f.matchAll())
                    .fetch(0, 0)
                    .total()
                    .hitCount();
        } catch (RuntimeException e) {
            log.debug("Trip index hit count failed: {}", e.getMessage());
            return 0L;
        }
    }

    /** Exposed for unit tests only. */
    static String resolveStateForTest(long dbCount, long esCount, boolean indexing) {
        return resolveState(dbCount, esCount, indexing);
    }

    private static String resolveState(long dbCount, long esCount, boolean indexing) {
        // Empty database: nothing to search; leftover ES docs from a prior run must not block readiness.
        if (dbCount == 0) {
            return SearchIndexStatus.STATE_READY;
        }
        if (esCount >= dbCount) {
            return SearchIndexStatus.STATE_READY;
        }
        if (indexing) {
            return SearchIndexStatus.STATE_INDEXING;
        }
        if (esCount == 0 && dbCount > 0) {
            return SearchIndexStatus.STATE_EMPTY;
        }
        return SearchIndexStatus.STATE_STALE;
    }

    private static String describe(String state, long dbCount, long esCount, String lockOwner) {
        return switch (state) {
            case SearchIndexStatus.STATE_READY ->
                    "Index matches database (%d trips indexed, %d in DB)."
                            .formatted(esCount, dbCount);
            case SearchIndexStatus.STATE_INDEXING ->
                    "Mass indexing in progress (lock owner: %s, %d/%d)."
                            .formatted(lockOwner != null ? lockOwner : "local", esCount, dbCount);
            case SearchIndexStatus.STATE_EMPTY ->
                    "Index empty; %d trips in database.".formatted(dbCount);
            default -> "Index stale (%d indexed, %d in DB).".formatted(esCount, dbCount);
        };
    }

    private static void sleepSeconds(int seconds) {
        try {
            Thread.sleep(seconds * 1000L);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static String defaultInstanceId() {
        String host = System.getenv("HOSTNAME");
        if (host != null && !host.isBlank()) {
            return host;
        }
        return "trip-service-" + ProcessHandle.current().pid();
    }
}
