-- Seeded plays stored played_at as text ('2026-09-22 17:54:48.007605+00:00'); the app writes epoch millis. SQLite sorts
-- every text value above every number, so ORDER BY played_at kept old seeded plays ahead of newer ones (recently played,
-- last playback, the overlap check on new plays). Convert the text rows to millis, keeping the fractional seconds.
-- Rows SQLite can't parse as a date are left alone.
UPDATE play_events
SET played_at = CAST(round((julianday(played_at) - 2440587.5) * 86400000) AS INTEGER)
WHERE typeof(played_at) = 'text' AND julianday(played_at) IS NOT NULL;
