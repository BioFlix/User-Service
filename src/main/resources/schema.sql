-- Runs on every start after Hibernate (ddl-auto: update) has created the tables.
-- Only things Hibernate cannot generate from the entities: partial / expression indexes.

-- E-mail is unique (case-insensitive) among users that are not deleted
CREATE UNIQUE INDEX IF NOT EXISTS users_email_active ON users (lower(email)) WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS outbox_unpublished ON outbox_events (created_at) WHERE published_at IS NULL;
