-- NEW_FOLLOWER notifications: about a user, not a playlist, so playlist_id becomes nullable. SQLite can't change a
-- column or CHECK in place, so the table is rebuilt. Nothing references notifications, so the drop is safe.
CREATE TABLE notifications_new (
    notification_id VARCHAR NOT NULL PRIMARY KEY,
    public_id VARCHAR(8) NOT NULL UNIQUE,
    recipient_id VARCHAR NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    actor_id VARCHAR NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    playlist_id VARCHAR REFERENCES playlists(playlist_id) ON DELETE CASCADE,
    type VARCHAR NOT NULL CHECK (type IN ('PLAYLIST_INVITE', 'INVITE_ACCEPTED', 'TRACKS_ADDED', 'NEW_FOLLOWER')),
    permission VARCHAR CHECK (permission IN ('LISTENER', 'EDITOR')),
    track_count INTEGER,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL,
    CHECK ((type = 'NEW_FOLLOWER') = (playlist_id IS NULL))
);

INSERT INTO notifications_new (notification_id, public_id, recipient_id, actor_id, playlist_id, type, permission,
                               track_count, is_read, created_at)
SELECT notification_id, public_id, recipient_id, actor_id, playlist_id, type, permission, track_count, is_read, created_at
FROM notifications;

DROP TABLE notifications;
ALTER TABLE notifications_new RENAME TO notifications;

CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, created_at, notification_id);
-- One NEW_FOLLOWER per follower: following again moves it to the top instead of adding another
CREATE UNIQUE INDEX idx_notifications_new_follower ON notifications(recipient_id, actor_id) WHERE type = 'NEW_FOLLOWER';
