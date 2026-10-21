CREATE TABLE user_play_preferences (
    user_id VARCHAR NOT NULL PRIMARY KEY REFERENCES users(user_id) ON DELETE CASCADE,
    shuffle_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    repeat_preference VARCHAR NOT NULL DEFAULT 'OFF' CHECK (repeat_preference IN ('OFF', 'PIECE', 'PLAYLIST'))
);
