PRAGMA foreign_keys = OFF;

DROP TABLE IF EXISTS playlist_shared_with;
DROP TABLE IF EXISTS playlist_saved_by;
DROP TABLE IF EXISTS user_playlists_added_to;
DROP TABLE IF EXISTS user_saved_playlists;
DROP TABLE IF EXISTS music_pieces;
DROP TABLE IF EXISTS playlist_tracks;
DROP TABLE IF EXISTS playlist_tags;
DROP TABLE IF EXISTS music_piece_tags;
DROP TABLE IF EXISTS tags;
DROP TABLE IF EXISTS user_favorite_artists;
DROP TABLE IF EXISTS follows;
DROP TABLE IF EXISTS artists;
DROP TABLE IF EXISTS playlists;
DROP TABLE IF EXISTS users;

CREATE TABLE users (
    user_id VARCHAR NOT NULL PRIMARY KEY,
    username VARCHAR(16) NOT NULL UNIQUE,
    email VARCHAR(320) NOT NULL UNIQUE,
    password_hash VARCHAR(72) NOT NULL,
    profile_picture_url VARCHAR,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE playlists (
    playlist_id VARCHAR NOT NULL PRIMARY KEY,
    owner_id VARCHAR NOT NULL REFERENCES users(user_id),
    is_public BOOLEAN,
    playlist_cover_url VARCHAR,
    playlist_name VARCHAR,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE user_saved_playlists (
    user_id VARCHAR NOT NULL REFERENCES users(user_id),
    playlist_id VARCHAR NOT NULL REFERENCES playlists(playlist_id),
    saved_at TIMESTAMP DEFAULT (CURRENT_TIMESTAMP),
    PRIMARY KEY (user_id, playlist_id)
);

CREATE TABLE artists (
    artist_id VARCHAR NOT NULL PRIMARY KEY,
    artist_name VARCHAR NOT NULL,
    artist_pfp_url VARCHAR
);

CREATE TABLE music_pieces (
    music_piece_id VARCHAR NOT NULL PRIMARY KEY,
    uploader_user_id VARCHAR NOT NULL REFERENCES users(user_id),
    cover_url VARCHAR,
    name VARCHAR,
    artist_id VARCHAR NOT NULL REFERENCES artists(artist_id),
    mp3_file_url VARCHAR NOT NULL,
    duration_seconds INTEGER,
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

CREATE TABLE follows (
    follower_user_id VARCHAR NOT NULL REFERENCES users(user_id),
    following_user_id VARCHAR NOT NULL REFERENCES users(user_id),
    created_at TIMESTAMP DEFAULT (CURRENT_TIMESTAMP),
    PRIMARY KEY (follower_user_id, following_user_id)
);

CREATE TABLE user_favorite_artists (
    user_id VARCHAR NOT NULL REFERENCES users(user_id),
    artist_id VARCHAR NOT NULL REFERENCES artists(artist_id),
    created_at TIMESTAMP DEFAULT (CURRENT_TIMESTAMP),
    PRIMARY KEY (user_id, artist_id)
);

CREATE TABLE tags (
    tag_id VARCHAR NOT NULL PRIMARY KEY,
    tag VARCHAR NOT NULL UNIQUE
);

CREATE TABLE playlist_tags (
    playlist_id VARCHAR NOT NULL REFERENCES playlists(playlist_id),
    tag_id VARCHAR NOT NULL REFERENCES tags(tag_id),
    PRIMARY KEY (playlist_id, tag_id)
);

CREATE TABLE music_piece_tags (
    music_piece_id VARCHAR NOT NULL REFERENCES music_pieces(music_piece_id),
    tag_id VARCHAR NOT NULL REFERENCES tags(tag_id),
    PRIMARY KEY (music_piece_id, tag_id)
);

CREATE TABLE playlist_tracks (
    playlist_id VARCHAR NOT NULL REFERENCES playlists(playlist_id),
    music_piece_id VARCHAR NOT NULL REFERENCES music_pieces(music_piece_id),
    playlist_position INTEGER NOT NULL,
    added_at TIMESTAMP DEFAULT (CURRENT_TIMESTAMP),
    PRIMARY KEY (playlist_id, music_piece_id),
    UNIQUE (playlist_id, playlist_position)
);

CREATE TABLE play_events (
    play_event_id VARCHAR NOT NULL PRIMARY KEY,
    user_id VARCHAR NOT NULL REFERENCES users(user_id),
    music_piece_id VARCHAR NOT NULL REFERENCES music_pieces(music_piece_id),
    played_at TIMESTAMP NOT NULL DEFAULT (CURRENT_TIMESTAMP),
    listen_duration_seconds INTEGER NOT NULL,
    context_playlist_id VARCHAR REFERENCES playlists(playlist_id)
);

CREATE INDEX idx_play_events_user_played_at ON play_events(user_id, played_at);
CREATE INDEX idx_play_events_music_piece_played_at ON play_events(music_piece_id, played_at);

CREATE TABLE user_favorite_tracks (
    user_id VARCHAR NOT NULL REFERENCES users(user_id),
    music_piece_id VARCHAR NOT NULL REFERENCES music_pieces(music_piece_id),
    created_at TIMESTAMP DEFAULT (CURRENT_TIMESTAMP),
    PRIMARY KEY (user_id, music_piece_id)
);

PRAGMA foreign_keys = ON;
