package com.tripplanning;

import java.nio.file.Files;
import java.nio.file.Path;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Sanity check that V1__initial_schema.sql is syntactically valid DDL and
 * that Flyway applies it cleanly to a fresh database. Uses H2 in PostgreSQL
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

        assertEquals(1, result.migrationsExecuted, "V1 should have been applied exactly once");
        assertTrue(result.success, "Flyway migration should succeed");
        assertTrue(Files.exists(Path.of(dbFile.toAbsolutePath() + ".mv.db")), "DB file should exist");

        var info = flyway.info().current();
        assertEquals("1", info.getVersion().getVersion());

        var second = flyway.migrate();
        assertEquals(0, second.migrationsExecuted, "Re-running Flyway should be a no-op");
    }
}
