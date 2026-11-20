package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aux_app.repository.NotificationRepository;
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
import java.time.Instant;

// V13 and NotificationRepository.upsertNewFollower on a real SQLite DB built by the Flyway migrations
class FollowNotificationQueryTest {

    @Test
    void migrationKeepsPlaylistNotificationsAndAllowsNewFollower(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).target("12").load().migrate();
        try (Connection db = DriverManager.getConnection(url)) {
            seedUsers(db);
            exec(db, "INSERT INTO playlists (playlist_id, owner_id, playlist_name, is_public, public_id) VALUES ('p', 'me', 'P', 1, 'p_p')");
            exec(db, "INSERT INTO notifications (notification_id, public_id, recipient_id, actor_id, playlist_id, type, track_count, is_read, created_at) "
                    + "VALUES ('n1', 'n_1', 'me', 'amy', 'p', 'TRACKS_ADDED', 3, 1, '2026-01-01T00:00:00Z')");
        }
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            assertEquals("TRACKS_ADDED|p|3|1", one(db, "SELECT type || '|' || playlist_id || '|' || track_count || '|' || is_read FROM notifications WHERE notification_id = 'n1'"));
            // Only NEW_FOLLOWER may (and must) leave playlist_id empty
            assertThrows(SQLException.class, () -> exec(db, "INSERT INTO notifications (notification_id, public_id, recipient_id, actor_id, type, created_at) "
                    + "VALUES ('n2', 'n_2', 'me', 'amy', 'TRACKS_ADDED', '2026-01-01T00:00:00Z')"));
            assertThrows(SQLException.class, () -> exec(db, "INSERT INTO notifications (notification_id, public_id, recipient_id, actor_id, playlist_id, type, created_at) "
                    + "VALUES ('n3', 'n_3', 'me', 'amy', 'p', 'NEW_FOLLOWER', '2026-01-01T00:00:00Z')"));
        }
    }

    @Test
    void followingAgainMovesTheOneNotificationToTheTopUnread(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();
        try (Connection db = DriverManager.getConnection(url)) {
            seedUsers(db);
            upsert(db, "id1", "n_a", "me", "amy", 1000);
            exec(db, "UPDATE notifications SET is_read = 1");
            upsert(db, "id2", "n_b", "me", "amy", 2000);
            // Another follower gets their own row
            upsert(db, "id3", "n_c", "me", "bo", 1500);

            assertEquals("2", one(db, "SELECT COUNT(*) FROM notifications"));
            // The first row is kept (same id) with the new time, unread
            assertEquals("id1|n_a|0|2000", one(db, "SELECT notification_id || '|' || public_id || '|' || is_read || '|' || created_at "
                    + "FROM notifications WHERE actor_id = 'amy'"));
        }
    }

    private static void upsert(Connection db, String id, String publicId, String recipient, String actor, long createdAt) throws Exception {
        String sql = NotificationRepository.class.getMethod("upsertNewFollower", String.class, String.class, String.class, String.class, Instant.class)
                .getAnnotation(Query.class).value()
                .replace(":notificationId", "?").replace(":publicId", "?").replace(":recipientId", "?")
                .replace(":actorId", "?").replace(":createdAt", "?");
        try (PreparedStatement statement = db.prepareStatement(sql)) {
            statement.setString(1, id);
            statement.setString(2, publicId);
            statement.setString(3, recipient);
            statement.setString(4, actor);
            statement.setLong(5, createdAt);
            statement.executeUpdate();
        }
    }

    private static void seedUsers(Connection db) throws SQLException {
        exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) VALUES "
                + "('me', 'me', 'me@x', 'x', 'DONE', 'u_me'), ('amy', 'amy', 'a@x', 'x', 'DONE', 'u_amy'), "
                + "('bo', 'bo', 'b@x', 'x', 'DONE', 'u_bo')");
    }

    private static String one(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement(); ResultSet rows = statement.executeQuery(sql)) {
            rows.next();
            return rows.getString(1);
        }
    }

    private static void exec(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
