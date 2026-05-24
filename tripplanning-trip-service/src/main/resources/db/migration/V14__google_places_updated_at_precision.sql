-- Hibernate validates Instant as TIMESTAMP(6) WITH TIME ZONE; V12 used timestamptz (same type, different JDBC metadata).
ALTER TABLE google_places
    ALTER COLUMN updated_at TYPE TIMESTAMP(6) WITH TIME ZONE;
