-- TripLocationImageEntity maps imagePath -> image_path; V5 used image_url.
-- Idempotent for DBs that already use image_path (e.g. after manual fixes).
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'trip_location_images'
      AND column_name = 'image_url'
  ) THEN
    ALTER TABLE trip_location_images RENAME COLUMN image_url TO image_path;
  END IF;
END $$;
