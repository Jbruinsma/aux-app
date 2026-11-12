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

// Runs MusicPieceRepository.findArtistPieceRows on a real SQLite DB built by the Flyway migrations
class ArtistPiecesQueryTest {

    @Test
    void mostPlayedFirstWithCallersPrivatePieces(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) "
                    + "VALUES ('up', 'up', 'u@x', 'x', 'DONE', 'u_up'), ('fan', 'fan', 'f@x', 'x', 'DONE', 'u_fan')");
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id) VALUES ('A', 'A', 'a_A'), ('B', 'B', 'a_B')");
            // 'hit' has 2 plays, 'deep' 1; 'old' and 'new' have none, so the newer one comes first.
            // 'mine' is private to 'up', and 'other' is by another artist.
            exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name, created_at) VALUES "
                    + "('hit', 'up', 'A', 'x', 1, 'm_hit', 'hit', 1), ('deep', 'up', 'A', 'x', 1, 'm_deep', 'deep', 2), "
                    + "('old', 'up', 'A', 'x', 1, 'm_old', 'old', 3), ('new', 'up', 'A', 'x', 1, 'm_new', 'new', 4), "
                    + "('mine', 'up', 'A', 'x', 0, 'm_mine', 'mine', 5), ('other', 'up', 'B', 'x', 1, 'm_other', 'other', 6)");
            exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds) VALUES "
                    + "('e1', 'fan', 'hit', 1, 60), ('e2', 'up', 'hit', 2, 60), ('e3', 'fan', 'deep', 3, 60), "
                    + "('e4', 'fan', 'other', 4, 60), ('e5', 'fan', 'other', 5, 60), ('e6', 'fan', 'other', 6, 60)");

            assertEquals(List.of("hit", "deep", "new", "old"), names(db, null, 10, 0));
            assertEquals(List.of("hit", "deep", "mine", "new", "old"), names(db, "up", 10, 0));
            assertEquals(List.of("deep", "new"), names(db, null, 2, 1));
        }
    }

    private static List<String> names(Connection db, String userId, int limit, int offset) throws Exception {
        String sql = MusicPieceRepository.class
                .getMethod("findArtistPieceRows", String.class, String.class, int.class, int.class)
                .getAnnotation(Query.class).value()
                .replace(":artistId", "'A'")
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
