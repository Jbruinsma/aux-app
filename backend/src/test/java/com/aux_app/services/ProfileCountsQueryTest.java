package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.aux_app.repository.FollowRepository;
import com.aux_app.repository.UserRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.data.jpa.repository.Query;

import java.nio.file.Path;
import java.time.Instant;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

// Runs UserRepository.findProfileRows on a real SQLite DB built by the Flyway migrations
class ProfileCountsQueryTest {

    @Test
    void countsFollowsAndOnlyVisibleUploads(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) VALUES "
                    + "('owner', 'owner', 'o@x', 'x', 'DONE', 'u_o'), ('a', 'a', 'a@x', 'x', 'DONE', 'u_a'), "
                    + "('b', 'b', 'b@x', 'x', 'DONE', 'u_b')");
            exec(db, "INSERT INTO follows (follower_user_id, following_user_id) VALUES ('b', 'owner'), ('owner', 'a')");
            // FollowRepository.follow: a repeat still reports 1 row (no primary key error), an unknown username 0
            assertEquals(1, follow(db, "a", "OWNER"));
            assertEquals(1, follow(db, "a", "owner"));
            assertEquals(0, follow(db, "a", "nobody"));
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id) VALUES ('A', 'A', 'a_A')");
            exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name) "
                    + "VALUES ('pub', 'owner', 'A', 'x', 1, 'm_pub', 'pub'), ('priv', 'owner', 'A', 'x', 0, 'm_priv', 'priv')");

            // stranger: 2 followers, 1 following, only the public upload; owner sees both uploads
            assertCounts(db, "a", 2, 1, 1);
            assertCounts(db, "owner", 2, 1, 2);
        }
    }

    private static void assertCounts(Connection db, String caller, int followers, int following, int uploads) throws Exception {
        String sql = UserRepository.class.getMethod("findProfileRows", String.class, String.class)
                .getAnnotation(Query.class).value()
                .replace(":username", "'owner'")
                .replace(":currentUserId", "'" + caller + "'");
        try (Statement statement = db.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            assertTrue(rows.next());
            assertEquals(followers, rows.getInt("followersCount"));
            assertEquals(following, rows.getInt("followingCount"));
            assertEquals(uploads, rows.getInt("uploadsCount"));
        }
    }

    private static int follow(Connection db, String follower, String following) throws Exception {
        String sql = FollowRepository.class.getMethod("follow", String.class, String.class, Instant.class)
                .getAnnotation(Query.class).value()
                .replace(":followerUserId", "?").replace(":username", "?").replace(":createdAt", "?");
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            statement.setString(1, follower);
            statement.setString(2, Instant.now().toString()); // createdAt comes before username in the SQL
            statement.setString(3, following);
            return statement.executeUpdate();
        }
    }

    private static void exec(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
