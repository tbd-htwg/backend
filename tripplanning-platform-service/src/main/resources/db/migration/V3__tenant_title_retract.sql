ALTER TABLE tenants
    ADD COLUMN title_retract_to_initials BOOLEAN NOT NULL DEFAULT FALSE;

UPDATE tenants
SET title_retract_to_initials = TRUE
WHERE slug = 'free';
