-- Stub/local branding icons store data: URLs; GCS tenants store short object paths.
ALTER TABLE tenants
    ALTER COLUMN icon_url TYPE TEXT;
