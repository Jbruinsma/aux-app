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
import java.util.ArrayList;
import java.util.List;

// Runs PlayEventRepository.findRecentPlays (findRecentPlaybacks and the profile's recent plays) on a real SQLite DB
// built by the Flyway migrations
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

            try (PreparedStatement statement = db.prepareStatement(recentPlaysSql("'caller'", "'caller'"))) {
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("m_bare", rows.getString("musicPieceId"));
                    int playlistId = rows.findColumn("playlistId");
                    int tags = rows.findColumn("tags");
                    assertEquals(Types.VARCHAR, rows.getMetaData().getColumnType(playlistId));
                    assertEquals(Types.VARCHAR, rows.getMetaData().getColumnType(tags));
                    // is_public is declared BOOLEAN, so a raw select came back as Boolean and broke the Integer getter
                    assertEquals(Types.INTEGER, rows.getMetaData().getColumnType(rows.findColumn("isPublic")));

                    int owner = rows.findColumn("playlistOwnerUsername");
                    assertEquals(Types.VARCHAR, rows.getMetaData().getColumnType(owner));
                    assertEquals("", rows.getString(owner));
                    int name = rows.findColumn("playlistName");
                    assertEquals(Types.VARCHAR, rows.getMetaData().getColumnType(name));
                    assertEquals("", rows.getString(name));

                    assertTrue(rows.next());
                    assertEquals("p_pl", rows.getString(playlistId));
                    assertEquals("caller", rows.getString(owner));
                    assertEquals("Mix", rows.getString(name));
                    assertEquals("chill", rows.getString(tags));
                }
            }
        }
    }

    // GET /me/last-playback links the player back to the playlist, so it needs the owner and name as well as the id
    @Test
    void lastPlaybackHasPlaylistOwnerAndName(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) "
                    + "VALUES ('caller', 'caller', 'c@x', 'x', 'DONE', 'u_c'), ('owner', 'owner', 'o@x', 'x', 'DONE', 'u_o')");
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id) VALUES ('A', 'A', 'a_A')");
            exec(db, "INSERT INTO playlists (playlist_id, owner_id, is_public, playlist_name, public_id) "
                    + "VALUES ('pl', 'owner', 1, 'Road trip', 'p_pl')");
            exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name) "
                    + "VALUES ('song', 'owner', 'A', 'x', 1, 'm_song', 'song')");
            exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds, context_playlist_id) "
                    + "VALUES ('p1', 'caller', 'song', 1000, 60, 'pl')");

            String sql = PlayEventRepository.class.getMethod("findLastPlayback", String.class)
                    .getAnnotation(Query.class).value();
            try (PreparedStatement statement = db.prepareStatement(sql.replace(":userId", "?"))) {
                for (int i = 1; i <= statement.getParameterMetaData().getParameterCount(); i++) {
                    statement.setString(i, "caller");
                }
                try (ResultSet rows = statement.executeQuery()) {
                    assertTrue(rows.next());
                    assertEquals("p_pl", rows.getString("playlistId"));
                    assertEquals("owner", rows.getString("playlistOwnerUsername"));
                    assertEquals("Road trip", rows.getString("playlistName"));
                }
            }
        }
    }

    // Seeded plays held played_at as text and the app writes epoch millis. SQLite sorts every text value above every
    // number, so ORDER BY played_at kept old seeded plays ahead of anything played since, until V8 converted them
    @Test
    void newPlaysPassOldSeededOnes(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).target("7").load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) "
                    + "VALUES ('caller', 'caller', 'c@x', 'x', 'DONE', 'u_c')");
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id) VALUES ('A', 'A', 'a_A')");
            exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name) "
                    + "VALUES ('one', 'caller', 'A', 'x', 1, 'm_one', 'one'), ('two', 'caller', 'A', 'x', 1, 'm_two', 'two')");
            // Seeded in September: 'one', then 'two'. Played from the app in November: 'one' again
            exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds) VALUES "
                    + "('s1', 'caller', 'one', '2026-09-22 17:54:48.007605+00:00', 60), "
                    + "('s2', 'caller', 'two', '2026-09-22 18:54:48.007605+00:00', 60), "
                    + "('a1', 'caller', 'one', 1794579829790, 60)");
            Flyway.configure().dataSource(url, null, null).load().migrate();

            try (Statement statement = db.createStatement();
                 ResultSet row = statement.executeQuery("SELECT played_at, typeof(played_at) FROM play_events WHERE play_event_id = 's1'")) {
                assertTrue(row.next());
                assertEquals(1790099688008L, row.getLong(1)); // fractional seconds kept
                assertEquals("integer", row.getString(2));
            }
            assertEquals(List.of("m_one", "m_two"), pieceIds(db, recentPlaysSql("'caller'", "'caller'")));
            assertEquals(List.of("m_one"), pieceIds(db, lastPlaybackSql()));
        }
    }

    // On a profile the viewer sees only what they could play: public pieces and their own uploads, the playlist only
    // when they can see it, and their own favorites
    @Test
    void profileShowsOnlyWhatTheViewerCanPlay(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) VALUES "
                    + "('owner', 'owner', 'o@x', 'x', 'DONE', 'u_o'), ('viewer', 'viewer', 'v@x', 'x', 'DONE', 'u_v')");
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id) VALUES ('A', 'A', 'a_A')");
            exec(db, "INSERT INTO playlists (playlist_id, owner_id, is_public, playlist_name, public_id) "
                    + "VALUES ('secret', 'owner', 0, 'Secret mix', 'p_secret')");
            exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name) VALUES "
                    + "('pub', 'owner', 'A', 'x', 1, 'm_pub', 'pub'), "
                    + "('ownersPrivate', 'owner', 'A', 'x', 0, 'm_op', 'op'), "
                    + "('viewersPrivate', 'viewer', 'A', 'x', 0, 'm_vp', 'vp')");
            exec(db, "INSERT INTO user_favorite_tracks (user_id, music_piece_id) VALUES ('viewer', 'pub')");
            exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds, context_playlist_id) VALUES "
                    + "('p1', 'owner', 'pub', 1000, 60, 'secret'), "
                    + "('p2', 'owner', 'ownersPrivate', 2000, 60, NULL), "
                    + "('p3', 'owner', 'viewersPrivate', 3000, 60, NULL)");

            assertEquals(List.of("m_op", "m_pub"), pieceIds(db, recentPlaysSql("'owner'", "'owner'")));
            assertEquals(List.of("m_vp", "m_pub"), pieceIds(db, recentPlaysSql("'owner'", "'viewer'")));
            assertEquals(List.of("m_pub"), pieceIds(db, recentPlaysSql("'owner'", "NULL")));

            try (Statement statement = db.createStatement();
                 ResultSet rows = statement.executeQuery(recentPlaysSql("'owner'", "'viewer'"))) {
                assertTrue(rows.next());
                assertTrue(rows.next());
                assertEquals("m_pub", rows.getString("musicPieceId"));
                assertEquals("", rows.getString("playlistId")); // the viewer can't see the owner's private playlist
                assertEquals(1, rows.getInt("isFavorite"));
            }
        }
    }

    // owner and viewer are SQL literals: 'id' or NULL
    private static String recentPlaysSql(String owner, String viewer) throws Exception {
        return PlayEventRepository.class.getMethod("findRecentPlays", String.class, String.class, int.class)
                .getAnnotation(Query.class).value()
                .replace(":ownerId", owner).replace(":viewerId", viewer).replace(":limit", "20");
    }

    private static String lastPlaybackSql() throws Exception {
        return PlayEventRepository.class.getMethod("findLastPlayback", String.class)
                .getAnnotation(Query.class).value().replace(":userId", "'caller'");
    }

    private static List<String> pieceIds(Connection db, String sql) throws Exception {
        List<String> ids = new ArrayList<>();
        try (PreparedStatement statement = db.prepareStatement(sql);
             ResultSet rows = statement.executeQuery()) {
            while (rows.next()) ids.add(rows.getString("musicPieceId"));
        }
        return ids;
    }

    private static void exec(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
