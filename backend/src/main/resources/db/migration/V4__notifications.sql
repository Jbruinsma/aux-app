CREATE TABLE notifications (
    notification_id VARCHAR NOT NULL PRIMARY KEY,
    public_id VARCHAR(8) NOT NULL UNIQUE,
    recipient_id VARCHAR NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    actor_id VARCHAR NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    playlist_id VARCHAR NOT NULL REFERENCES playlists(playlist_id) ON DELETE CASCADE,
    type VARCHAR NOT NULL CHECK (type IN ('PLAYLIST_INVITE', 'INVITE_ACCEPTED', 'TRACKS_ADDED')),
    permission VARCHAR CHECK (permission IN ('LISTENER', 'EDITOR')),
    track_count INTEGER,
    is_read BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMP NOT NULL
);

CREATE INDEX idx_notifications_recipient ON notifications(recipient_id, created_at, notification_id);
