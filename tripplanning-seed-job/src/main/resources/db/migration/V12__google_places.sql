-- Shared Google Places cache keyed by place ID.

CREATE TABLE google_places (
    google_place_id     varchar(255) PRIMARY KEY,
    place_name          varchar(255) NOT NULL,
    city_name           varchar(255) NOT NULL,
    formatted_address   text,
    latitude            double precision NOT NULL,
    longitude           double precision NOT NULL,
    country_code        varchar(10) NOT NULL,
    updated_at          timestamptz NOT NULL DEFAULT now()
);

-- Backfill from trip stops
INSERT INTO google_places (google_place_id, place_name, city_name, formatted_address, latitude, longitude, country_code, updated_at)
SELECT DISTINCT ON (google_place_id)
    google_place_id,
    place_name,
    city_name,
    NULL,
    0,
    0,
    'XX',
    now()
FROM trip_locations
WHERE google_place_id IS NOT NULL
ON CONFLICT (google_place_id) DO NOTHING;

-- Backfill from trip destinations
INSERT INTO google_places (google_place_id, place_name, city_name, formatted_address, latitude, longitude, country_code, updated_at)
SELECT DISTINCT ON (destination_google_place_id)
    destination_google_place_id,
    destination,
    destination,
    NULL,
    0,
    0,
    'XX',
    now()
FROM trips
WHERE destination_google_place_id IS NOT NULL
  AND destination_google_place_id <> ''
ON CONFLICT (google_place_id) DO NOTHING;

-- Backfill from accommodation
INSERT INTO google_places (google_place_id, place_name, city_name, formatted_address, latitude, longitude, country_code, updated_at)
SELECT DISTINCT ON (google_place_id)
    google_place_id,
    name,
    city_name,
    address,
    0,
    0,
    'XX',
    now()
FROM accommodation
WHERE google_place_id IS NOT NULL
  AND google_place_id <> ''
ON CONFLICT (google_place_id) DO NOTHING;

-- Backfill transport start
INSERT INTO google_places (google_place_id, place_name, city_name, formatted_address, latitude, longitude, country_code, updated_at)
SELECT DISTINCT ON (start_google_place_id)
    start_google_place_id,
    coalesce(start_address, 'Unknown'),
    coalesce(start_address, 'Unknown'),
    start_address,
    0,
    0,
    'XX',
    now()
FROM transport
WHERE start_google_place_id IS NOT NULL
  AND start_google_place_id <> ''
ON CONFLICT (google_place_id) DO NOTHING;

-- Backfill transport end
INSERT INTO google_places (google_place_id, place_name, city_name, formatted_address, latitude, longitude, country_code, updated_at)
SELECT DISTINCT ON (end_google_place_id)
    end_google_place_id,
    coalesce(end_address, 'Unknown'),
    coalesce(end_address, 'Unknown'),
    end_address,
    0,
    0,
    'XX',
    now()
FROM transport
WHERE end_google_place_id IS NOT NULL
  AND end_google_place_id <> ''
ON CONFLICT (google_place_id) DO NOTHING;

ALTER TABLE trips
    ADD CONSTRAINT fk_trips_destination_google_place
        FOREIGN KEY (destination_google_place_id) REFERENCES google_places (google_place_id);

ALTER TABLE trip_locations
    ADD CONSTRAINT fk_trip_locations_google_place
        FOREIGN KEY (google_place_id) REFERENCES google_places (google_place_id);

ALTER TABLE accommodation
    ADD CONSTRAINT fk_accommodation_google_place
        FOREIGN KEY (google_place_id) REFERENCES google_places (google_place_id);

ALTER TABLE transport
    ADD CONSTRAINT fk_transport_start_google_place
        FOREIGN KEY (start_google_place_id) REFERENCES google_places (google_place_id),
    ADD CONSTRAINT fk_transport_end_google_place
        FOREIGN KEY (end_google_place_id) REFERENCES google_places (google_place_id);
