-- Curated tags are suggested first in the tag picker; users can still create free-form ones
ALTER TABLE tags ADD COLUMN is_curated BOOLEAN NOT NULL DEFAULT 0;

-- For looking up pieces and playlists by tag
CREATE INDEX idx_music_piece_tags_tag ON music_piece_tags(tag_id);
CREATE INDEX idx_playlist_tags_tag ON playlist_tags(tag_id);

-- Genres, then moods. Names follow TagService.normalize. A tag that already exists just becomes curated
INSERT INTO tags (tag_id, tag, is_curated)
SELECT lower(hex(randomblob(16))), name, 1 FROM (
    SELECT 'hip-hop' AS name UNION ALL SELECT 'rap' UNION ALL SELECT 'r&b' UNION ALL SELECT 'pop'
    UNION ALL SELECT 'rock' UNION ALL SELECT 'indie' UNION ALL SELECT 'alternative' UNION ALL SELECT 'electronic'
    UNION ALL SELECT 'house' UNION ALL SELECT 'techno' UNION ALL SELECT 'edm' UNION ALL SELECT 'dubstep'
    UNION ALL SELECT 'drum & bass' UNION ALL SELECT 'lo-fi' UNION ALL SELECT 'jazz' UNION ALL SELECT 'blues'
    UNION ALL SELECT 'soul' UNION ALL SELECT 'funk' UNION ALL SELECT 'country' UNION ALL SELECT 'folk'
    UNION ALL SELECT 'classical' UNION ALL SELECT 'metal' UNION ALL SELECT 'punk' UNION ALL SELECT 'reggae'
    UNION ALL SELECT 'latin' UNION ALL SELECT 'k-pop' UNION ALL SELECT 'afrobeats' UNION ALL SELECT 'gospel'
    UNION ALL SELECT 'ambient' UNION ALL SELECT 'soundtrack'
    UNION ALL SELECT 'chill' UNION ALL SELECT 'hype' UNION ALL SELECT 'sad' UNION ALL SELECT 'happy'
    UNION ALL SELECT 'romantic' UNION ALL SELECT 'angry' UNION ALL SELECT 'dreamy' UNION ALL SELECT 'dark'
    UNION ALL SELECT 'energetic' UNION ALL SELECT 'focus' UNION ALL SELECT 'workout' UNION ALL SELECT 'party'
    UNION ALL SELECT 'late night' UNION ALL SELECT 'nostalgic' UNION ALL SELECT 'mellow'
)
WHERE true -- SQLite needs a WHERE before ON CONFLICT in INSERT ... SELECT
ON CONFLICT (tag) DO UPDATE SET is_curated = 1;
