-- Expand location table to match LocationEntity schema.
-- Adds city, countryCode, latitude, longitude, formattedAddress fields
-- and migrates existing 'name' data to 'city'.

ALTER TABLE location
    ADD COLUMN IF NOT EXISTS city varchar(255),
    ADD COLUMN IF NOT EXISTS country_code varchar(10),
    ADD COLUMN IF NOT EXISTS latitude double precision,
    ADD COLUMN IF NOT EXISTS longitude double precision,
    ADD COLUMN IF NOT EXISTS formatted_address text;

UPDATE location
SET city = name
WHERE city IS NULL;

ALTER TABLE location
    ALTER COLUMN city SET NOT NULL,
    DROP COLUMN IF EXISTS name;
