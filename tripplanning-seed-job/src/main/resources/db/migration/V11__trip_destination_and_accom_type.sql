ALTER TABLE trips
    ADD COLUMN IF NOT EXISTS destination_google_place_id varchar(255);

-- Existing rows may lack a place id until edited; new trips require it via app validation.

ALTER TABLE accommodation
    ALTER COLUMN type DROP NOT NULL;

UPDATE accommodation SET type = '' WHERE type IS NULL;

ALTER TABLE accommodation
    ALTER COLUMN type SET DEFAULT '';
