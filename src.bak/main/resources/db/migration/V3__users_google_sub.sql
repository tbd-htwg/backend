-- Stable Google account id (OIDC "sub") for linking; multiple NULLs allowed under UNIQUE (PostgreSQL).
alter table users add column google_sub varchar(255);

alter table users add constraint uq_users_google_sub unique (google_sub);
