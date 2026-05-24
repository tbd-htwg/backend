package com.tripplanning.seed;

import java.util.List;
import java.util.concurrent.ExecutionException;

import javax.sql.DataSource;

import org.flywaydb.core.Flyway;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.google.api.core.ApiFuture;
import com.google.cloud.firestore.CollectionReference;
import com.google.cloud.firestore.Firestore;
import com.google.cloud.firestore.QueryDocumentSnapshot;
import com.google.cloud.firestore.QuerySnapshot;
import com.google.cloud.firestore.WriteBatch;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/** Wipes PostgreSQL trip schema and Firestore social collections before a full re-seed. */
@Component
@RequiredArgsConstructor
@Slf4j
public class SeedDataReset {

    private static final int FIRESTORE_DELETE_BATCH = 400;
    private static final List<String> FIRESTORE_COLLECTIONS = List.of("comments", "likes");

    private final SeedProperties seedProperties;
    private final JdbcTemplate jdbc;
    private final DataSource dataSource;
    private final ObjectProvider<Flyway> flyway;
    private final ObjectProvider<Firestore> firestoreProvider;

    public void wipeAll() throws Exception {
        if (!seedProperties.wipeBeforeSeed()) {
            log.info("tripplanning.seed.wipe-before-seed=false; skipping datastore wipe.");
            return;
        }
        log.info("Wiping PostgreSQL and Firestore before seed (GCS sample images are not touched)...");
        wipeFirestore();
        wipePostgres();
    }

    private void wipePostgres() {
        if (isPostgres()) {
            jdbc.execute("DROP SCHEMA public CASCADE");
            jdbc.execute("CREATE SCHEMA public");
            jdbc.execute("GRANT ALL ON SCHEMA public TO public");
            jdbc.execute("GRANT ALL ON SCHEMA public TO CURRENT_USER");
            runFlywayMigrate();
            log.info("PostgreSQL schema recreated via Flyway.");
            return;
        }
        jdbc.execute("DROP ALL OBJECTS");
        log.info("H2 database objects dropped.");
    }

    private void wipeFirestore() throws ExecutionException, InterruptedException {
        Firestore firestore = firestoreProvider.getIfAvailable();
        if (firestore == null) {
            log.warn("Firestore not available; skipping social data wipe.");
            return;
        }
        for (String collection : FIRESTORE_COLLECTIONS) {
            deleteCollection(firestore, collection);
        }
    }

    private void deleteCollection(Firestore firestore, String collectionId)
            throws ExecutionException, InterruptedException {
        CollectionReference collection = firestore.collection(collectionId);
        int deleted = 0;
        while (true) {
            ApiFuture<QuerySnapshot> future = collection.limit(FIRESTORE_DELETE_BATCH).get();
            List<QueryDocumentSnapshot> documents = future.get().getDocuments();
            if (documents.isEmpty()) {
                break;
            }
            WriteBatch batch = firestore.batch();
            for (QueryDocumentSnapshot document : documents) {
                batch.delete(document.getReference());
            }
            batch.commit().get();
            deleted += documents.size();
        }
        log.info("Firestore collection '{}' cleared ({} documents deleted).", collectionId, deleted);
    }

    private void runFlywayMigrate() {
        Flyway migration =
                flyway.getIfAvailable(
                        () ->
                                Flyway.configure()
                                        .dataSource(dataSource)
                                        .locations("classpath:db/migration")
                                        .load());
        migration.migrate();
    }

    private boolean isPostgres() {
        String version = jdbc.queryForObject("SELECT LOWER(version())", String.class);
        return version != null && version.contains("postgresql") && !version.contains("h2");
    }
}
