ALTER TABLE tenants
ADD COLUMN IF NOT EXISTS resource_config_json TEXT;
