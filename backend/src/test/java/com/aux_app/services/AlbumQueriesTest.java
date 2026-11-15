package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.aux_app.repository.AlbumRepository;
import com.aux_app.repository.MusicPieceRepository;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
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

// Runs AlbumRepository's queries, and the album ones in MusicPieceRepository, on a real SQLite DB built by the
// Flyway migrations
class AlbumQueriesTest {

    @TempDir
    Path dir;
    private String url;

    // 'boss' created artist A; 'maker' created albums Full and Empty; 'up' uploads; 'fan' listens.
    // Full has two public pieces and up's private one; Empty has none; Secret has only up's private piece
    @BeforeEach
    void seed() throws Exception {
        url = "jdbc:sqlite:" + dir.resolve("test.db") + "?foreign_keys=on";
        Flyway.configure().dataSource(url, null, null).load().migrate();
        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) VALUES "
                    + "('boss', 'boss', 'b@x', 'x', 'DONE', 'u_boss'), ('maker', 'maker', 'm@x', 'x', 'DONE', 'u_maker'), "
                    + "('up', 'up', 'u@x', 'x', 'DONE', 'u_up'), ('fan', 'fan', 'f@x', 'x', 'DONE', 'u_fan')");
            exec(db, "INSERT INTO artists (artist_id, artist_name, public_id, created_by_user_id) VALUES "
                    + "('A', 'A', 'a_A', 'boss'), ('B', 'B', 'a_B', 'boss')");
            exec(db, "INSERT INTO albums (album_id, public_id, artist_id, album_name, cover_url, created_by_user_id) VALUES "
                    + "('full', 'l_full', 'A', 'Full', 'c_full', 'maker'), ('empty', 'l_empty', 'A', 'empty', 'c_empty', 'maker'), "
                    + "('secret', 'l_secret', 'A', 'Secret', 'c_secret', 'up'), ('other', 'l_other', 'B', 'Other', 'c_other', 'up')");
            exec(db, "INSERT INTO music_pieces (music_piece_id, uploader_user_id, artist_id, mp3_file_url, is_public, public_id, name, "
                    + "created_at, album_id, uses_album_cover, cover_url) VALUES "
                    + "('hit', 'up', 'A', 'x', 1, 'm_hit', 'hit', 1, 'full', 1, 'c_full'), "
                    + "('own', 'up', 'A', 'x', 1, 'm_own', 'own', 2, 'full', 0, 'c_own'), "
                    + "('priv', 'up', 'A', 'x', 0, 'm_priv', 'priv', 3, 'full', 1, 'c_full'), "
                    + "('hidden', 'up', 'A', 'x', 0, 'm_hidden', 'hidden', 4, 'secret', 1, 'c_secret'), "
                    + "('b1', 'up', 'B', 'x', 1, 'm_b1', 'b1', 5, 'other', 1, 'c_other'), "
                    + "('single', 'up', 'A', 'x', 1, 'm_single', 'single', 6, NULL, 0, 'c_single')");
            exec(db, "INSERT INTO play_events (play_event_id, user_id, music_piece_id, played_at, listen_duration_seconds) VALUES "
                    + "('e1', 'fan', 'own', 1, 60), ('e2', 'fan', 'own', 2, 60), ('e3', 'fan', 'b1', 3, 60), "
                    + "('e4', 'fan', 'hidden', 4, 60), ('e5', 'fan', 'hidden', 5, 60), ('e6', 'fan', 'hidden', 6, 60), "
                    + "('e7', 'fan', 'single', 7, 60)");
        }
    }

    @Test
    void artistAlbumsHideEmptyOnesFromAllButEditors() throws Exception {
        // Signed out: only Full has a playable piece. Names sort ignoring case
        assertEquals(List.of("Full:2:0"), artistAlbums(null));
        assertEquals(List.of("Full:2:0"), artistAlbums("fan"));
        // up sees their private pieces, and edits Secret
        assertEquals(List.of("Full:3:0", "Secret:1:1"), artistAlbums("up"));
        // maker edits Full and Empty; boss created the artist, so edits all of them
        assertEquals(List.of("empty:0:1", "Full:2:1"), artistAlbums("maker"));
        assertEquals(List.of("empty:0:1", "Full:2:1", "Secret:0:1"), artistAlbums("boss"));
    }

    @Test
    void topAlbumsOnlyCountPiecesTheCallerCanPlay() throws Exception {
        // fan played Secret most, but its piece is private to up, so only up sees it
        assertEquals(List.of("Full", "Other"), topAlbums(null));
        assertEquals(List.of("Secret", "Full", "Other"), topAlbums("up"));
    }

    @Test
    void albumPiecesMostPlayedFirst() throws Exception {
        assertEquals(List.of("own", "hit"), albumPieces(null));
        assertEquals(List.of("own", "priv", "hit"), albumPieces("up"));
    }

    @Test
    void overviewsCarryTheAlbum() throws Exception {
        String sql = MusicPieceRepository.OVERVIEW_COLUMNS.replace(":userId", "NULL")
                + " WHERE mp.public_id IN ('m_hit', 'm_own', 'm_single') ORDER BY mp.created_at";
        List<String> rows = new ArrayList<>();
        try (Connection db = DriverManager.getConnection(url);
             PreparedStatement statement = db.prepareStatement(sql); ResultSet r = statement.executeQuery()) {
            while (r.next()) rows.add(r.getString("albumId") + ":" + r.getString("albumName") + ":" + r.getInt("usesAlbumCover"));
        }
        assertEquals(List.of("l_full:Full:1", "l_full:Full:0", "null:null:0"), rows);
    }

    @Test
    void coverSyncAndDetachKeepEveryPieceCovered() throws Exception {
        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "UPDATE albums SET cover_url = 'c_new' WHERE album_id = 'full'");
            exec(db, modifying("syncPieceCovers").replace(":albumId", "'full'").replace(":coverUrl", "'c_new'"));
            // Only pieces using the album cover follow it
            assertEquals("hit:c_new,own:c_own,priv:c_new", one(db,
                    "SELECT group_concat(name || ':' || cover_url, ',' ORDER BY name) FROM music_pieces WHERE album_id = 'full'"));

            exec(db, modifying("detachPieces").replace(":albumId", "'full'"));
            exec(db, "DELETE FROM albums WHERE album_id = 'full'");
            assertEquals("hit:c_new:0,own:c_own:0,priv:c_new:0", one(db,
                    "SELECT group_concat(name || ':' || cover_url || ':' || uses_album_cover, ',' ORDER BY name) "
                            + "FROM music_pieces WHERE name IN ('hit', 'own', 'priv') AND album_id IS NULL"));
            assertEquals(null, one(db, "SELECT group_concat(\"table\") FROM pragma_foreign_key_check"));
        }
    }

    @Test
    void albumNamesAreUniquePerArtistIgnoringCase() throws Exception {
        try (Connection db = DriverManager.getConnection(url)) {
            assertThrows(SQLException.class, () -> exec(db,
                    "INSERT INTO albums (album_id, public_id, artist_id, album_name, cover_url) VALUES ('dup', 'l_dup', 'A', 'FULL', 'c')"));
            // Another artist can use the name
            exec(db, "INSERT INTO albums (album_id, public_id, artist_id, album_name, cover_url) VALUES ('ok', 'l_ok', 'B', 'Full', 'c')");
        }
    }

    private List<String> artistAlbums(String userId) throws Exception {
        String sql = query("findArtistAlbumRows", String.class, String.class)
                .replace(":artistId", "'A'").replace(":userId", literal(userId));
        return column(sql, r -> r.getString("albumName") + ":" + r.getInt("pieceCount") + ":" + r.getInt("canEdit"));
    }

    private List<String> topAlbums(String userId) throws Exception {
        String sql = query("findTopAlbumRows", String.class, String.class)
                .replace(":ownerId", "'fan'").replace(":userId", literal(userId));
        return column(sql, r -> r.getString("albumName"));
    }

    private List<String> albumPieces(String userId) throws Exception {
        String sql = MusicPieceRepository.class
                .getMethod("findAlbumPieceRows", String.class, String.class, int.class, int.class)
                .getAnnotation(Query.class).value()
                .replace(":albumId", "'full'").replace(":userId", literal(userId))
                .replace(":limit", "10").replace(":offset", "0");
        return column(sql, r -> r.getString("name"));
    }

    private static String query(String method, Class<?>... params) throws Exception {
        return AlbumRepository.class.getMethod(method, params).getAnnotation(Query.class).value();
    }

    private static String modifying(String method) throws Exception {
        for (var m : AlbumRepository.class.getMethods()) {
            if (m.getName().equals(method)) return m.getAnnotation(Query.class).value();
        }
        throw new NoSuchMethodException(method);
    }

    private static String literal(String userId) {
        return userId == null ? "NULL" : "'" + userId + "'";
    }

    interface Row<T> { T read(ResultSet r) throws SQLException; }

    private <T> List<T> column(String sql, Row<T> read) throws SQLException {
        List<T> out = new ArrayList<>();
        try (Connection db = DriverManager.getConnection(url);
             PreparedStatement statement = db.prepareStatement(sql); ResultSet r = statement.executeQuery()) {
            while (r.next()) out.add(read.read(r));
        }
        return out;
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
