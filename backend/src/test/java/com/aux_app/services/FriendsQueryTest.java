package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aux_app.repository.FollowRepository;
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

// Runs FollowRepository.findFriends on a real SQLite DB built by the Flyway migrations
class FriendsQueryTest {

    @Test
    void mutualFollowsWithTheirLatestPublicPlay(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) VALUES "
                    + "('me', 'me', 'me@x', 'x', 'DONE', 'u_me'), ('amy', 'amy', 'a@x', 'x', 'DONE', 'u_amy'), "
                    + "('bo', 'bo', 'b@x', 'x', 'DONE', 'u_bo'), ('cy', 'cy', 'c@x', 'x', 'DONE', 'u_cy'), "
                    + "('dee', 'dee', 'd@x', 'x', 'DONE', 'u_dee'), ('new', 'new', 'n@x', 'x', 'PROFILE', 'u_new')");
            // Friends: amy and bo. cy only follows me, I only follow dee, and 'new' hasn't finished onboarding
            exec(db, "INSERT INTO follows (follower_user_id, following_user_id) VALUES "
                    + "('me', 'amy'), ('amy', 'me'), ('me', 'bo'), ('bo', 'me'), ('cy', 'me'), ('me', 'dee'), "
                    + "('me', 'new'), ('new', 'me')");
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id) VALUES ('A', 'A', 'a_A')");
            exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name) VALUES "
                    + "('pub1', 'cy', 'A', 'x', 1, 'm_pub1', 'pub1'), ('pub2', 'cy', 'A', 'x', 1, 'm_pub2', 'pub2'), "
                    + "('priv', 'amy', 'A', 'x', 0, 'm_priv', 'priv')");
            // amy's latest play is private, so her latest public one (pub2, seeded as text) shows. bo has played nothing.
            exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds) VALUES "
                    + "('e1', 'amy', 'pub1', 1000, 60), ('e2', 'amy', 'pub2', '2026-01-01 00:00:00', 60), "
                    + "('e3', 'amy', 'priv', 1900000000000, 60), ('e4', 'cy', 'pub1', 5000, 60)");

            String sql = FollowRepository.class.getMethod("findFriends", String.class)
                    .getAnnotation(Query.class).value().replace(":userId", "'me'");
            List<String> rows = new ArrayList<>();
            try (PreparedStatement statement = db.prepareStatement(sql); ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(result.getString("username") + " " + result.getString("userId") + " "
                            + result.getString("musicPieceId") + " " + result.getLong("playedAt"));
                }
            }
            rows.sort(null);
            assertEquals(List.of("amy u_amy m_pub2 1767225600000", "bo u_bo  0"), rows);
        }
    }

    private static void exec(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
