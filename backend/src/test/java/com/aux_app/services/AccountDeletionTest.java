package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

// Runs AccountService.DELETES on a real SQLite DB built by the Flyway migrations, with foreign keys on like the app
class AccountDeletionTest {

    @Test
    void removesEverythingTheUserHadAndKeepsOthersData(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db") + "?foreign_keys=on";
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            assertEquals("1", one(db, "PRAGMA foreign_keys"), "the DELETES order is only tested with foreign keys on");
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) "
                    + "VALUES ('gone', 'gone', 'g@x', 'x', 'DONE', 'u_gone'), ('keep', 'keep', 'k@x', 'x', 'DONE', 'u_keep')");
            exec(db, "INSERT INTO profile_details (user_id, about) VALUES ('gone', 'hi'), ('keep', 'hi')");
            exec(db, "INSERT INTO user_play_preferences (user_id) VALUES ('gone')");
            exec(db, "INSERT INTO otp_codes (user_id, code_hash, expires_at) VALUES ('gone', 'x', 1)");
            // 'gone' created the artist, and 'keep' uploads under it
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id, created_by_user_id) VALUES ('A', 'A', 'a_A', 'gone')");
            exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name) "
                    + "VALUES ('mg', 'gone', 'A', 'x', 1, 'm_mg', 'mg'), ('mk', 'keep', 'A', 'x', 1, 'm_mk', 'mk')");
            exec(db, "INSERT INTO music_piece_tags (music_piece_id, tag_id) SELECT 'mg', tag_id FROM tags WHERE tag = 'chill'");
            exec(db, "INSERT INTO playlists (playlist_id, owner_id, is_public, playlist_name, public_id) "
                    + "VALUES ('pg', 'gone', 1, 'G', 'p_pg'), ('pk', 'keep', 1, 'K', 'p_pk')");
            exec(db, "INSERT INTO playlist_tags (playlist_id, tag_id) SELECT 'pg', tag_id FROM tags WHERE tag = 'chill'");
            exec(db, "INSERT INTO playlist_tracks (playlist_id, music_piece_id, playlist_position) "
                    + "VALUES ('pg', 'mg', 0), ('pg', 'mk', 1), ('pk', 'mg', 0), ('pk', 'mk', 1)");
            exec(db, "INSERT INTO playlist_members (playlist_id, user_id, permission, status) "
                    + "VALUES ('pg', 'keep', 'EDITOR', 'ACCEPTED'), ('pk', 'gone', 'LISTENER', 'PENDING')");
            exec(db, "INSERT INTO notifications (notification_id, public_id, recipient_id, actor_id, playlist_id, type, created_at) "
                    + "VALUES ('n1', 'n_1', 'keep', 'gone', 'pg', 'INVITE_ACCEPTED', 1), ('n2', 'n_2', 'gone', 'keep', 'pk', 'PLAYLIST_INVITE', 1)");
            exec(db, "INSERT INTO user_saved_playlists (user_id, playlist_id) VALUES ('gone', 'pk'), ('keep', 'pg')");
            exec(db, "INSERT INTO follows (follower_user_id, following_user_id) VALUES ('gone', 'keep'), ('keep', 'gone')");
            exec(db, "INSERT INTO user_favorite_artists (user_id, artist_id) VALUES ('gone', 'A')");
            exec(db, "INSERT INTO user_favorite_tracks (user_id, music_piece_id) VALUES ('gone', 'mk'), ('keep', 'mg'), ('keep', 'mk')");
            // keep's play of their own piece from gone's playlist survives, without the playlist
            exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds, context_playlist_id) "
                    + "VALUES ('e1', 'gone', 'mk', 1, 60, NULL), ('e2', 'keep', 'mg', 2, 60, 'pk'), ('e3', 'keep', 'mk', 3, 60, 'pg')");

            for (String sql : AccountService.DELETES) {
                exec(db, sql.replace(":userId", "'gone'"));
            }

            assertEquals("keep", one(db, "SELECT group_concat(user_id) FROM users"));
            assertEquals("keep", one(db, "SELECT group_concat(user_id) FROM profile_details"));
            assertEquals("mk", one(db, "SELECT group_concat(music_piece_id) FROM music_pieces"));
            assertEquals("pk", one(db, "SELECT group_concat(playlist_id) FROM playlists"));
            assertEquals("pk:mk", one(db, "SELECT group_concat(playlist_id || ':' || music_piece_id) FROM playlist_tracks"));
            assertEquals("e3:", one(db, "SELECT group_concat(play_event_id || ':' || COALESCE(context_playlist_id, '')) FROM play_events"));
            assertEquals("keep", one(db, "SELECT group_concat(user_id) FROM user_favorite_tracks"));
            assertEquals("A:", one(db, "SELECT group_concat(artist_id || ':' || COALESCE(created_by_user_id, '')) FROM artists"));
            for (String table : new String[] {"playlist_members", "notifications", "user_saved_playlists", "follows",
                    "user_favorite_artists", "music_piece_tags", "playlist_tags", "otp_codes", "user_play_preferences"}) {
                assertEquals("0", one(db, "SELECT COUNT(*) FROM " + table), table);
            }
            assertEquals(null, one(db, "SELECT group_concat(\"table\") FROM pragma_foreign_key_check"));
        }
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
