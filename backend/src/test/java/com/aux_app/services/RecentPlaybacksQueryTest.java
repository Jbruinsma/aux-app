package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

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
import java.sql.Types;

// Runs PlayEventRepository.findRecentPlaybacks on a real SQLite DB built by the Flyway migrations
class RecentPlaybacksQueryTest {

    // sqlite-jdbc types an expression column from the first row and Hibernate keeps that type for every row. A first
    // row with no playlist or tags used to report NUMERIC, so the next row's playlist id failed as a BigDecimal
    @Test
    void optionalColumnsAreTextWhenFirstRowHasNone(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) "
                    + "VALUES ('caller', 'caller', 'c@x', 'x', 'DONE', 'u_c')");
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id) VALUES ('A', 'A', 'a_A')");
            exec(db, "INSERT INTO playlists (playlist_id, owner_id, is_public, playlist_name, public_id) "
                    + "VALUES ('pl', 'caller', 1, 'Mix', 'p_pl')");
            exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name) "
                    + "VALUES ('bare', 'caller', 'A', 'x', 1, 'm_bare', 'bare'), ('tagged', 'caller', 'A', 'x', 1, 'm_tagged', 'tagged')");
            exec(db, "INSERT INTO music_piece_tags (music_piece_id, tag_id) SELECT 'tagged', tag_id FROM tags WHERE tag = 'chill'");
            exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds, context_playlist_id) "
                    + "VALUES ('old', 'caller', 'tagged', 1000, 60, 'pl'), ('new', 'caller', 'bare', 2000, 60, NULL)");

            String sql = PlayEventRepository.class.getMethod("findRecentPlaybacks", String.class)
                    .getAnnotation(Query.class).value();
            try (PreparedStatement statement = db.prepareStatement(sql.replace(":userId", "?"))) {
                for (int i = 1; i <= statement.getParameterMetaData().getParameterCount(); i++) {
                    statement.setString(i, "caller");
                }
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("m_bare", rows.getString("musicPieceId"));
                    int playlistId = rows.findColumn("playlistId");
                    int tags = rows.findColumn("tags");
                    assertEquals(Types.VARCHAR, rows.getMetaData().getColumnType(playlistId));
                    assertEquals(Types.VARCHAR, rows.getMetaData().getColumnType(tags));

                    assertTrue(rows.next());
                    assertEquals("p_pl", rows.getString(playlistId));
                    assertEquals("chill", rows.getString(tags));
                }
            }
        }
    }

    private static void exec(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
