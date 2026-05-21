-- Google Places on trip stops; drop legacy location FK.
-- Accommodation stay dates + cost; transport A→B + cost.

alter table trip_locations
    add column google_place_id varchar(255),
    add column place_name varchar(255),
    add column city_name varchar(255);

update trip_locations tl
set
    city_name = coalesce(l.city, 'Unknown'),
    place_name = coalesce(l.city, 'Unknown'),
    google_place_id = 'legacy-' || tl.id::text
from location l
where tl.location_id = l.id;

update trip_locations
set
    city_name = coalesce(city_name, 'Unknown'),
    place_name = coalesce(place_name, 'Unknown'),
    google_place_id = coalesce(google_place_id, 'legacy-' || id::text)
where google_place_id is null;

alter table trip_locations
    drop constraint if exists fk_trip_locations_location;

alter table trip_locations
    drop column location_id;

alter table trip_locations
    alter column google_place_id set not null,
    alter column place_name set not null,
    alter column city_name set not null;

alter table accommodation
    add column google_place_id varchar(255),
    add column city_name varchar(255),
    add column check_in_date date,
    add column check_out_date date,
    add column cost numeric(12, 2),
    add column currency varchar(3);

alter table transport
    add column start_google_place_id varchar(255),
    add column end_google_place_id varchar(255),
    add column start_address varchar(500),
    add column end_address varchar(500),
    add column cost numeric(12, 2),
    add column currency varchar(3);
