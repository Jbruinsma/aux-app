-- Who can open a user's followers and following lists. The counts show to everyone either way.
ALTER TABLE users ADD COLUMN follows_visibility VARCHAR NOT NULL DEFAULT 'EVERYONE'
    CHECK (follows_visibility IN ('EVERYONE', 'FRIENDS', 'NO_ONE'));
