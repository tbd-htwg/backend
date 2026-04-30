-- UserEntity.imagePath maps to image_path; V1 used image_url.
DO $$
BEGIN
  IF EXISTS (
    SELECT 1
    FROM information_schema.columns
    WHERE table_schema = current_schema()
      AND table_name = 'users'
      AND column_name = 'image_url'
  ) THEN
    ALTER TABLE users RENAME COLUMN image_url TO image_path;
  END IF;
END $$;
