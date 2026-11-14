package com.aux_app.services;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.aux_app.repository.UserRepository;
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

// Runs UserRepository.findLibraryRows on a real SQLite DB built by the Flyway migrations
class LibraryQueryTest {

    // `access` is the caller's access on each library playlist: OWNER, the accepted member's permission, or '' (read as
    // null) for a playlist only saved. Saved playlists sort first, so the first row has no access; it must still come
    // back as text (see TagRepository.PIECE_TAGS) or later rows fail to read
    @Test
    void accessIsTheCallersAccessOnEachPlaylist(@TempDir Path dir) throws Exception {
        String url = "jdbc:sqlite:" + dir.resolve("test.db");
        Flyway.configure().dataSource(url, null, null).load().migrate();

        try (Connection db = DriverManager.getConnection(url)) {
            exec(db, "INSERT INTO users (user_id, username, email, password_hash, onboarding_step, public_id) "
                    + "VALUES ('caller', 'caller', 'c@x', 'x', 'DONE', 'u_c'), ('other', 'other', 'o@x', 'x', 'DONE', 'u_o')");
            exec(db, "INSERT INTO playlists (playlist_id, owner_id, is_public, playlist_name, public_id, created_at) VALUES "
                    + "('own', 'caller', 0, 'Own', 'p_own', 1000), "
                    + "('edit', 'other', 0, 'Edit', 'p_edit', 2000), "
                    + "('listen', 'other', 1, 'Listen', 'p_listen', 3000), "
                    + "('saved', 'other', 1, 'Saved', 'p_saved', 4000), "
                    + "('pending', 'other', 1, 'Pending', 'p_pending', 5000)");
            exec(db, "INSERT INTO playlist_members (playlist_id, user_id, permission, status) VALUES "
                    + "('edit', 'caller', 'EDITOR', 'ACCEPTED'), "
                    + "('listen', 'caller', 'LISTENER', 'ACCEPTED'), "
                    + "('pending', 'caller', 'EDITOR', 'PENDING')");
            // Saved and also a member: the membership decides the access
            exec(db, "INSERT INTO user_saved_playlists (user_id, playlist_id, saved_at) VALUES ('caller', 'saved', 2), ('caller', 'listen', 1)");

            String sql = UserRepository.class.getMethod("findLibraryRows", String.class)
                    .getAnnotation(Query.class).value();
            List<String> rows = new ArrayList<>();
            try (PreparedStatement statement = db.prepareStatement(sql.replace(":userId", "?"))) {
                for (int i = 1; i <= statement.getParameterMetaData().getParameterCount(); i++) {
                    statement.setString(i, "caller");
                }
                try (ResultSet result = statement.executeQuery()) {
                    assertEquals(Types.VARCHAR, result.getMetaData().getColumnType(result.findColumn("access")));
                    while (result.next()) rows.add(result.getString("playlistId") + "=" + result.getString("access"));
                }
            }
            // Saved first (most recent first), then the rest newest first; the pending invite isn't in the library
            assertEquals(List.of("p_saved=", "p_listen=LISTENER", "p_edit=EDITOR", "p_own=OWNER"), rows);
        }
    }

    private static void exec(Connection db, String sql) throws SQLException {
        try (Statement statement = db.createStatement()) {
            statement.execute(sql);
        }
    }
}
