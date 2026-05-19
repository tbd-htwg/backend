-- Add required trip location date range fields introduced in TripLocationEntity.
-- Backfill existing rows from the parent trip's start_date so NOT NULL can be enforced safely.

alter table trip_locations
    add column start_date timestamp(6),
    add column end_date timestamp(6);

update trip_locations tl
set start_date = t.start_date::timestamp,
    end_date = t.start_date::timestamp
from trips t
where tl.trip_id = t.id;

alter table trip_locations
    alter column start_date set not null,
    alter column end_date set not null;
