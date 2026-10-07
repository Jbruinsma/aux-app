CREATE TABLE playlist_members (
    playlist_id VARCHAR NOT NULL REFERENCES playlists(playlist_id) ON DELETE CASCADE,
    user_id VARCHAR NOT NULL REFERENCES users(user_id) ON DELETE CASCADE,
    permission VARCHAR NOT NULL CHECK (permission IN ('LISTENER', 'EDITOR')),
    status VARCHAR NOT NULL DEFAULT 'PENDING' CHECK (status IN ('PENDING', 'ACCEPTED')),
    invited_at TIMESTAMP DEFAULT (CURRENT_TIMESTAMP),
    responded_at TIMESTAMP,
    PRIMARY KEY (playlist_id, user_id)
);

CREATE INDEX idx_playlist_members_user ON playlist_members(user_id, status);
