package db.migration;

import com.aux_app.entity.PublicId;
import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

// Adds public_id to every table the API exposes and backfills rows that existed before it.
// Java, not SQL, because SQLite can't generate the random base62 ids. Flyway runs it once on startup, in one transaction.
public class V2__Public_ids extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws SQLException {
        Connection connection = context.getConnection();
        backfill(connection, "users", "user_id", PublicId.USER);
        backfill(connection, "playlists", "playlist_id", PublicId.PLAYLIST);
        backfill(connection, "artists", "artist_id", PublicId.ARTIST);
        backfill(connection, "music_pieces", "music_piece_id", PublicId.MUSIC_PIECE);
    }

    private static void backfill(Connection connection, String table, String key, String prefix) throws SQLException {
        // SQLite can't ADD COLUMN ... NOT NULL without a default; the entity always sets it, so the unique index is enough
        try (Statement statement = connection.createStatement()) {
            statement.execute("ALTER TABLE " + table + " ADD COLUMN public_id VARCHAR(8)");
        }

        List<String> keys = new ArrayList<>();
        try (Statement statement = connection.createStatement();
             ResultSet rows = statement.executeQuery("SELECT " + key + " FROM " + table)) {
            while (rows.next()) {
                keys.add(rows.getString(1));
            }
        }

        Set<String> used = new HashSet<>();
        try (PreparedStatement update = connection.prepareStatement(
                "UPDATE " + table + " SET public_id = ? WHERE " + key + " = ?")) {
            for (String id : keys) {
                String publicId;
                do {
                    publicId = PublicId.generate(prefix);
                } while (!used.add(publicId));
                update.setString(1, publicId);
                update.setString(2, id);
                update.addBatch();
            }
            update.executeBatch();
        }

        try (Statement statement = connection.createStatement()) {
            statement.execute("CREATE UNIQUE INDEX uq_" + table + "_public_id ON " + table + "(public_id)");
        }
    }
}
