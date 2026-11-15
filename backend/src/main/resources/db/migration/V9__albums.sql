-- Albums group an artist's pieces and give them a shared cover. Anyone can create one for any artist; its creator
-- and the artist's creator can edit or delete it. created_by_user_id goes null when that account is deleted
CREATE TABLE albums (
    album_id VARCHAR NOT NULL PRIMARY KEY,
    public_id VARCHAR(8) NOT NULL UNIQUE,
    artist_id VARCHAR NOT NULL REFERENCES artists(artist_id),
    album_name VARCHAR NOT NULL,
    -- Required: a piece that uses the album cover holds this same URL in music_pieces.cover_url
    cover_url VARCHAR NOT NULL,
    created_by_user_id VARCHAR REFERENCES users(user_id),
    created_at TIMESTAMP,
    updated_at TIMESTAMP
);

-- One album per name per artist, ignoring case
CREATE UNIQUE INDEX uq_albums_artist_name_nocase ON albums(artist_id, album_name COLLATE NOCASE);

-- Null for singles. When uses_album_cover is 1, cover_url is a copy of the album's cover_url, kept in sync by
-- AlbumService, so every query that reads music_pieces.cover_url stays unchanged and a piece always has a cover
ALTER TABLE music_pieces ADD COLUMN album_id VARCHAR REFERENCES albums(album_id);
ALTER TABLE music_pieces ADD COLUMN uses_album_cover BOOLEAN NOT NULL DEFAULT 0;

CREATE INDEX idx_music_pieces_album ON music_pieces(album_id);
-- Checking whether an R2 cover is still used before deleting it
CREATE INDEX idx_music_pieces_cover_url ON music_pieces(cover_url);
