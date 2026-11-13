package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aux_app.repository.MusicPieceRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.jpa.repository.Query;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

// Runs MusicPieceRepository.findTagPieceRows on a real SQLite DB built by the Flyway migrations
class TagPiecesQueryTest {

    @Test
    void publicPiecesWithTheTagByRecentPlays(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) "
                    + "VALUES ('up', 'up', 'u@x', 'x', 'DONE', 'u_up'), ('fan', 'fan', 'f@x', 'x', 'DONE', 'u_fan')");
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id) VALUES ('A', 'A', 'a_A')");
            exec(db, "INSERT INTO tags (tag_id, tag) VALUES ('t1', 'lo-fi test'), ('t2', 'rock test')");
            // since = 1000. 'hot' has 2 recent plays (one as seeded text); 'stale' has 3, all before the window;
            // 'self' only has plays by its uploader; 'old' and 'new' have none, so the newer one comes first.
            // 'priv' is private (left out even for its uploader) and 'rock' has another tag.
            exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name, created_at) VALUES "
                    + "('hot', 'up', 'A', 'x', 1, 'm_hot', 'hot', 1), ('stale', 'up', 'A', 'x', 1, 'm_stale', 'stale', 2), "
                    + "('self', 'up', 'A', 'x', 1, 'm_self', 'self', 3), ('old', 'up', 'A', 'x', 1, 'm_old', 'old', 4), "
                    + "('new', 'up', 'A', 'x', 1, 'm_new', 'new', 5), ('priv', 'up', 'A', 'x', 0, 'm_priv', 'priv', 6), "
                    + "('rock', 'up', 'A', 'x', 1, 'm_rock', 'rock', 7)");
            exec(db, "INSERT INTO music_piece_tags (music_piece_id, tag_id) VALUES ('hot', 't1'), ('stale', 't1'), "
                    + "('self', 't1'), ('old', 't1'), ('new', 't1'), ('priv', 't1'), ('rock', 't2')");
            exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds) VALUES "
                    + "('e1', 'fan', 'hot', 2000, 60), ('e2', 'fan', 'hot', '2026-01-01 00:00:00', 60), "
                    + "('e3', 'fan', 'stale', 1, 60), ('e4', 'fan', 'stale', 2, 60), ('e5', 'fan', 'stale', 3, 60), "
                    + "('e6', 'up', 'self', 2000, 60), ('e7', 'up', 'self', 2000, 60), ('e8', 'fan', 'priv', 2000, 60)");

            // 'self' ties at 0 recent plays with 'stale', 'old' and 'new', so they go newest first
            assertEquals(List.of("hot", "new", "old", "self", "stale"), names(db, "lo-fi test", "up", 10, 0));
            assertEquals(List.of("new", "old"), names(db, "lo-fi test", null, 2, 1));
            assertEquals(List.of(), names(db, "unknown test", null, 10, 0));
        }
    }

    private static List<String> names(Connection db, String tag, String userId, int limit, int offset) throws Exception {
        String sql = MusicPieceRepository.class
                .getMethod("findTagPieceRows", String.class, long.class, String.class, int.class, int.class)
                .getAnnotation(Query.class).value()
                .replace(":tag", "'" + tag + "'")
                .replace(":since", "1000")
                .replace(":userId", userId == null ? "NULL" : "'" + userId + "'")
                .replace(":limit", Integer.toString(limit))
                .replace(":offset", Integer.toString(offset));
        List<String> names = new ArrayList<>();
        try (PreparedStatement statement = db.prepareStatement(sql); ResultSet rows = statement.executeQuery()) {
            while (rows.next()) names.add(rows.getString("name"));
        }
        return names;
    }

    private static void exec(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
