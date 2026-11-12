-- An artist's pieces: the artist page, discover's artist tags, and search by artist name all filter on it
CREATE INDEX IF NOT EXISTS idx_music_pieces_artist ON music_pieces(artist_id);
