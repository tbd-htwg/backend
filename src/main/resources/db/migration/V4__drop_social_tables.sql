-- Drop social tables migrated to MongoDB.
-- Safe to run: constraints are dropped first, then the tables.

ALTER TABLE comment DROP CONSTRAINT IF EXISTS fk_comment_user;
ALTER TABLE comment DROP CONSTRAINT IF EXISTS fk_comment_trip;

DROP TABLE IF EXISTS user_likes_trips;
DROP TABLE IF EXISTS comment;
