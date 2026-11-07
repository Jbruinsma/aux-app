package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aux_app.repository.PlayEventRepository;
import com.aux_app.services.CompatibilityService.Score;
import com.aux_app.services.CompatibilityService.Taste;
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
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CompatibilityServiceTest {

    private static Taste taste(Map<String, Double> artists, Map<String, Double> tags) {
        return new Taste(artists, tags);
    }

    private static double w(int plays) {
        return Math.log(1 + plays);
    }

    // The worked example in docs/compatibility.html
    @Test
    void alexAndSamScore48() {
        Taste alex = taste(
                Map.of("Frank Ocean", w(12) + 2, "SZA", w(6), "Mitski", w(3) + 0.5, "Tame Impala", w(2)),
                Map.of("r&b", w(14), "chill", w(10), "indie", w(5), "sad", w(4)));
        Taste sam = taste(
                Map.of("Frank Ocean", w(8), "SZA", w(9) + 2, "Kendrick Lamar", w(10), "Mitski", w(1)),
                Map.of("r&b", w(12), "chill", w(6), "hip-hop", w(10), "hype", w(7)));

        Score score = CompatibilityService.score(alex, sam);
        assertEquals(48, score.compatibility());
        assertEquals(List.of("Frank Ocean", "SZA", "Mitski"), score.sharedArtists());
        assertEquals(List.of("r&b", "chill"), score.sharedTags());
        // Symmetric
        assertEquals(48, CompatibilityService.score(sam, alex).compatibility());
    }

    @Test
    void identicalTasteReachesSuperOnlyWithEnoughSharedArtists() {
        Map<String, Double> tags = Map.of("pop", 1.0);
        Taste four = taste(Map.of("A", 1.0, "B", 2.0, "C", 3.0, "D", 4.0), tags);
        assertEquals(80, CompatibilityService.score(four, four).compatibility());

        // Two people who share one artist and nothing else stay at High
        Taste one = taste(Map.of("A", 1.0), tags);
        assertEquals(50, CompatibilityService.score(one, one).compatibility());
    }

    @Test
    void tagOnlyMatchStopsAtMedium() {
        Taste a = taste(Map.of("A", 1.0), Map.of("r&b", 1.0, "chill", 1.0));
        Taste b = taste(Map.of("B", 1.0), Map.of("r&b", 1.0, "chill", 1.0));
        Score score = CompatibilityService.score(a, b);
        assertEquals(39, score.compatibility()); // 100 * 1.0 * 2/3 = 67 before the cap
        assertTrue(score.sharedArtists().isEmpty());
        assertTrue(score.sharesAnything());
    }

    @Test
    void sharingNothingScoresZero() {
        Score score = CompatibilityService.score(
                taste(Map.of("A", 1.0), Map.of("pop", 1.0)),
                taste(Map.of("B", 1.0), Map.of("rock", 1.0)));
        assertEquals(0, score.compatibility());
        assertFalse(score.sharesAnything());
    }

    // ARTIST_WEIGHTS and TAG_WEIGHTS on a real SQLite DB built by the Flyway migrations
    @Test
    void weightQueriesKeepOnlyTasteSignals(@TempDir Path dir) throws SQLException {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();
        Instant now = Instant.parse("2026-11-06T12:00:00Z");

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) VALUES "
                    + "('v', 'v', 'v@x', 'x', 'DONE', 'u_v'), ('o', 'o', 'o@x', 'x', 'DONE', 'u_o'), "
                    + "('n', 'n', 'n@x', 'x', 'USERNAME', 'u_n')");
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id) VALUES ('A', 'A', 'a_A'), ('B', 'B', 'a_B'), ('C', 'C', 'a_C')");
            exec(db, "INSERT INTO tags (tag_id, tag, is_curated) VALUES ('mine', 'my tag', 0)");
            piece(db, "pA", "A", "o", true);
            piece(db, "priv", "C", "o", false);
            piece(db, "own", "C", "v", true);
            exec(db, "INSERT INTO music_piece_tags (music_piece_id, tag_id) SELECT 'pA', tag_id FROM tags WHERE tag IN ('chill', 'my tag')");

            play(db, "v", "pA", now);
            play(db, "v", "pA", now.minus(Duration.ofDays(90))); // half weight
            play(db, "v", "priv", now); // private: left out
            play(db, "v", "own", now); // own upload: left out
            play(db, "n", "pA", now); // onboarding not finished: left out
            exec(db, "INSERT INTO user_favorite_artists (user_id, artist_id) VALUES ('v', 'B')"); // no plays needed
            exec(db, "INSERT INTO user_favorite_tracks (user_id, music_piece_id) VALUES ('v', 'pA')");

            Map<String, Object> all = params(now, null, null);
            Map<String, Double> artists = weights(db, PlayEventRepository.ARTIST_WEIGHTS, all, "artistId");
            assertEquals(Map.of("v/a_A", Math.log(2.5) + 0.5, "v/a_B", 2.0).keySet(), artists.keySet());
            assertEquals(Math.log(2.5) + 0.5, artists.get("v/a_A"), 1e-9);
            assertEquals(2.0, artists.get("v/a_B"), 1e-9);

            Map<String, Double> tags = weights(db, PlayEventRepository.TAG_WEIGHTS, all, "tag");
            assertEquals(1, tags.size(), tags.toString()); // user-made tag ignored
            assertEquals(Math.log(2.5), tags.get("v/chill"), 1e-9);

            // A pair filter leaves other users out
            assertTrue(weights(db, PlayEventRepository.ARTIST_WEIGHTS, params(now, "o", "n"), "artistId").isEmpty());
        }
    }

    private static Map<String, Object> params(Instant now, String userA, String userB) {
        Map<String, Object> params = new HashMap<>();
        params.put("now", now.toEpochMilli());
        params.put("halfLifeMillis", (double) CompatibilityService.HALF_LIFE.toMillis());
        params.put("userA", userA);
        params.put("userB", userB);
        return params;
    }

    // "userId/key" -> weight
    private static Map<String, Double> weights(Connection db, String query, Map<String, Object> params, String keyColumn)
            throws SQLException {
        // Named parameters to JDBC placeholders
        List<Object> values = new ArrayList<>();
        Matcher m = Pattern.compile(":(\\w+)").matcher(query);
        StringBuilder sql = new StringBuilder();
        while (m.find()) {
            values.add(params.get(m.group(1)));
            m.appendReplacement(sql, "?");
        }
        m.appendTail(sql);

        Map<String, Double> out = new HashMap<>();
        try (PreparedStatement statement = db.prepareStatement(sql.toString())) {
            for (int i = 0; i < values.size(); i++) {
                statement.setObject(i + 1, values.get(i));
            }
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    out.put(rows.getString("userId") + "/" + rows.getString(keyColumn), rows.getDouble("weight"));
                }
            }
        }
        return out;
    }

    private static void piece(Connection db, String id, String artist, String uploader, boolean isPublic) throws SQLException {
        exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name) "
                + "VALUES ('" + id + "', '" + uploader + "', '" + artist + "', 'x', " + (isPublic ? 1 : 0) + ", 'm_" + id + "', '" + id + "')");
    }

    private static void play(Connection db, String user, String piece, Instant at) throws SQLException {
        exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds) "
                + "VALUES ('" + user + "-" + piece + "-" + at.toEpochMilli() + "', '" + user + "', '" + piece + "', "
                + at.toEpochMilli() + ", 60)");
    }

    private static void exec(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
