package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aux_app.repository.PlayEventRepository;
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

// Runs PlayEventRepository.findTopPieces on a real SQLite DB built by the Flyway migrations
class TopPiecesQueryTest {

    @Test
    void ranksRecentPublicPlaysByOthers(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) "
                    + "VALUES ('up', 'up', 'u@x', 'x', 'DONE', 'u_up'), ('fan', 'fan', 'f@x', 'x', 'DONE', 'u_fan')");
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id) VALUES ('A', 'A', 'a_A')");
            exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name) "
                    + "VALUES ('one', 'up', 'A', 'x', 1, 'm_one', 'one'), ('two', 'up', 'A', 'x', 1, 'm_two', 'two'), "
                    + "('old', 'up', 'A', 'x', 1, 'm_old', 'old'), ('priv', 'up', 'A', 'x', 0, 'm_priv', 'priv'), "
                    + "('self', 'up', 'A', 'x', 1, 'm_self', 'self')");
            // since = 1000. 'two' has 2 plays (one as seeded text), 'one' has 1; the rest must not show up:
            // 'old' is before the window, 'priv' is private, 'self' is only played by its uploader.
            exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds) VALUES "
                    + "('e1', 'fan', 'one', 2000, 60), ('e2', 'fan', 'two', 2000, 60), "
                    + "('e3', 'fan', 'two', '2026-01-01 00:00:00', 60), ('e4', 'fan', 'old', 500, 60), "
                    + "('e5', 'fan', 'priv', 2000, 60), ('e6', 'up', 'self', 2000, 60), ('e7', 'up', 'self', 2000, 60)");

            String sql = PlayEventRepository.class.getMethod("findTopPieces", long.class, int.class)
                    .getAnnotation(Query.class).value();
            try (PreparedStatement statement = db.prepareStatement(sql.replace(":since", "?").replace(":limit", "?"))) {
                statement.setLong(1, 1000);
                statement.setInt(2, 10);
                List<String> rows = new ArrayList<>();
                try (ResultSet result = statement.executeQuery()) {
                    while (result.next()) {
                        rows.add(result.getString("musicPieceId") + " " + result.getInt("playCount") + " "
                                + result.getString("uploaderUsername"));
                    }
                }
                assertEquals(List.of("m_two 2 up", "m_one 1 up"), rows);
            }
        }
    }

    private static void exec(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
