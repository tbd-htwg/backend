package com.tripplanning;

import java.nio.file.Files;
import java.nio.file.Path;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity check that Flyway migrations are syntactically valid DDL and
 * that Flyway applies them cleanly to a fresh database. Uses H2 in PostgreSQL
 * compatibility mode so we don't need a live Postgres for CI, but the SQL
 * itself is plain PostgreSQL.
 */
class FlywayMigrationSmokeTest {

    @Test
    void v1MigrationAppliesToEmptyDb(@TempDir Path tempDir) throws Exception {
        Path dbFile = tempDir.resolve("flyway-smoke");

        Flyway flyway = Flyway.configure()
                .dataSource(
                        "jdbc:h2:" + dbFile.toAbsolutePath()
                                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;DEFAULT_NULL_ORDERING=HIGH",
                        "sa",
                        "")
                .locations("classpath:db/migration")
                .load();

        var result = flyway.migrate();

        assertEquals(3, result.migrationsExecuted, "V1–V3 should apply on a fresh database");
        assertTrue(result.success, "Flyway migration should succeed");
        assertTrue(Files.exists(Path.of(dbFile.toAbsolutePath() + ".mv.db")), "DB file should exist");

        var info = flyway.info().current();
        assertEquals("3", info.getVersion().getVersion());

        var second = flyway.migrate();
        assertEquals(0, second.migrationsExecuted, "Re-running Flyway should be a no-op");
    }
}
