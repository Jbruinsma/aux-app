package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aux_app.repository.MusicPieceRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

// Runs MusicPieceRepository.DISCOVER on a real SQLite DB built by the Flyway migrations
class DiscoverQueryTest {

    private static final Instant NOW = Instant.now();

    @Test
    void ranksCapsAndExcludes(@TempDir Path dir) throws SQLException {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) VALUES "
                    + "('caller', 'caller', 'c@x', 'x', 'DONE', 'u_c'), ('u1', 'u1', 'u1@x', 'x', 'DONE', 'u_1'), "
                    + "('u2', 'u2', 'u2@x', 'x', 'DONE', 'u_2'), ('u3', 'u3', 'u3@x', 'x', 'DONE', 'u_3')");
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id) VALUES ('A', 'A', 'a_A'), ('B', 'B', 'a_B')");

            piece(db, "seed", "A", "u1", true, "chill", "lo-fi");
            for (int i = 1; i <= 4; i++) {
                piece(db, "a" + i, "A", "u1", true, "chill");
            }
            piece(db, "a5", "A", "u1", true, "jazz"); // puts jazz in A's profile, then loses to the cap
            piece(db, "b1", "B", "u1", true, "chill", "lo-fi");
            piece(db, "b2", "B", "u1", true, "jazz");
            piece(db, "b3", "B", "u1", true); // co-listened
            piece(db, "b4", "B", "u1", true); // only a short play
            piece(db, "own", "B", "caller", true, "chill");
            piece(db, "fav", "B", "u1", true, "chill");
            piece(db, "recent", "B", "u1", true, "chill");
            piece(db, "old", "B", "u1", true, "chill");
            piece(db, "private", "B", "u1", false, "chill");
            piece(db, "pop", "B", "u1", true);
            piece(db, "stale", "B", "u1", true);

            exec(db, "INSERT INTO user_favorite_tracks (user_id, music_piece_id) VALUES ('caller', 'fav')");
            play(db, "u2", "seed", 60, NOW.minus(Duration.ofDays(2)));
            play(db, "u2", "b3", 60, NOW.minus(Duration.ofDays(2)));
            play(db, "u2", "b4", 10, NOW.minus(Duration.ofDays(2)));
            play(db, "u3", "pop", 60, NOW.minus(Duration.ofDays(5)));
            play(db, "u3", "stale", 60, NOW.minus(Duration.ofDays(40)));
            play(db, "caller", "old", 60, NOW.minus(Duration.ofDays(10)));
            // Seeded rows hold text timestamps; the query must read those too
            exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds) "
                    + "VALUES ('text-play', 'caller', 'recent', '"
                    + DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss.SSSSSSxxx").withZone(ZoneOffset.UTC)
                            .format(NOW.minus(Duration.ofDays(1)))
                    + "', 60)");

            List<String> ids = discover(db);

            assertEquals(8, ids.size(), ids.toString());
            assertEquals("m_b1", ids.get(0)); // both seed tags
            Set<String> threes = new HashSet<>(ids.subList(1, 5)); // one seed tag each, same artist capped at 3
            assertTrue(threes.contains("m_old"), ids.toString());
            assertEquals(3, threes.stream().filter(id -> id.matches("m_a[1-4]")).count(), ids.toString());
            assertEquals(Set.of("m_b2", "m_b3"), new HashSet<>(ids.subList(5, 7))); // artist tag, co-listen
            assertEquals("m_pop", ids.get(7)); // fill-in
        }
    }

    private static List<String> discover(Connection db) throws SQLException {
        Instant now = NOW;
        Map<String, Object> params = Map.of(
                "seedId", "seed",
                "artistId", "A",
                "userId", "caller",
                "recentSince", now.minus(RecommendationService.RECENTLY_PLAYED).toEpochMilli(),
                "popularSince", now.minus(RecommendationService.POPULAR_WINDOW).toEpochMilli(),
                "sameArtistCap", RecommendationService.SAME_ARTIST_CAP,
                "limit", RecommendationService.DISCOVER_LIMIT);

        // Named parameters to JDBC placeholders
        List<Object> values = new ArrayList<>();
        Matcher m = Pattern.compile(":(\\w+)").matcher(MusicPieceRepository.DISCOVER);
        StringBuilder sql = new StringBuilder();
        while (m.find()) {
            values.add(params.get(m.group(1)));
            m.appendReplacement(sql, "?");
        }
        m.appendTail(sql);

        try (PreparedStatement statement = db.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) {
                statement.setObject(i + 1, values.get(i));
            }
            List<String> ids = new ArrayList<>();
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    ids.add(rows.getString(1));
                }
            }
            return ids;
        }
    }

    private static void piece(Connection db, String id, String artist, String uploader, boolean isPublic, String... tags)
            throws SQLException {
        exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name) "
                + "VALUES ('" + id + "', '" + uploader + "', '" + artist + "', 'x', " + (isPublic ? 1 : 0) + ", 'm_" + id + "', '" + id + "')");
        for (String tag : tags) {
            exec(db, "INSERT INTO music_piece_tags (music_piece_id, tag_id) SELECT '" + id + "', tag_id FROM tags WHERE tag = '" + tag + "'");
        }
    }

    private static void play(Connection db, String user, String piece, int seconds, Instant at) throws SQLException {
        exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds) "
                + "VALUES ('" + user + "-" + piece + "', '" + user + "', '" + piece + "', " + at.toEpochMilli() + ", " + seconds + ")");
    }

    private static void exec(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
