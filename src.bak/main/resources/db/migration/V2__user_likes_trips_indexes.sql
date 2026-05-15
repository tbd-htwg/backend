-- Speed up membership checks and like counts: PostgreSQL does not index FK columns by default.
create index if not exists idx_user_likes_trips_user_id on user_likes_trips (user_id);

create index if not exists idx_user_likes_trips_trip_id on user_likes_trips (trip_id);
