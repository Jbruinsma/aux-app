package com.aux_app.repository;

import com.aux_app.entity.TagEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface TagRepository extends JpaRepository<TagEntity, String> {

    // Column snippets for native queries: a piece's (alias `mp`) or playlist's (alias `p`) tags, joined with
    // commas in name order, null when it has none. Read with split(). Tag names can't contain a comma.
    String PIECE_TAGS = """
            (SELECT group_concat(t.tag, ',' ORDER BY t.tag) FROM music_piece_tags mpt
             JOIN tags t ON t.tag_id = mpt.tag_id WHERE mpt.music_piece_id = mp.music_piece_id)""";
    String PLAYLIST_TAGS = """
            (SELECT group_concat(t.tag, ',' ORDER BY t.tag) FROM playlist_tags ptg
             JOIN tags t ON t.tag_id = ptg.tag_id WHERE ptg.playlist_id = p.playlist_id)""";

    static List<String> split(String tags) {
        return tags == null || tags.isEmpty() ? List.of() : List.of(tags.split(","));
    }

    @Modifying
    @Query(value = "INSERT INTO tags (tag_id, tag) VALUES (:tagId, :tag) ON CONFLICT (tag) DO NOTHING", nativeQuery = true)
    void insertIfMissing(@Param("tagId") String tagId, @Param("tag") String tag);

    @Modifying
    @Query(value = "DELETE FROM music_piece_tags WHERE music_piece_id = :musicPieceId", nativeQuery = true)
    void clearPieceTags(@Param("musicPieceId") String musicPieceId);

    @Modifying
    @Query(value = """
            INSERT INTO music_piece_tags (music_piece_id, tag_id)
            SELECT :musicPieceId, tag_id FROM tags WHERE tag IN (:tags)
            """, nativeQuery = true)
    void addPieceTags(@Param("musicPieceId") String musicPieceId, @Param("tags") List<String> tags);

    @Modifying
    @Query(value = "DELETE FROM playlist_tags WHERE playlist_id = :playlistId", nativeQuery = true)
    void clearPlaylistTags(@Param("playlistId") String playlistId);

    @Modifying
    @Query(value = """
            INSERT INTO playlist_tags (playlist_id, tag_id)
            SELECT :playlistId, tag_id FROM tags WHERE tag IN (:tags)
            """, nativeQuery = true)
    void addPlaylistTags(@Param("playlistId") String playlistId, @Param("tags") List<String> tags);

    // Tag picker: curated tags first, then by how many public pieces and playlists use them. Tags only used on
    // private items stay hidden. `prefix` is a LIKE pattern (caller escapes %, _ and \\).
    @Query(value = """
            SELECT tag FROM (
                SELECT t.tag, t.is_curated,
                       (SELECT COUNT(*) FROM music_piece_tags mpt
                        JOIN music_pieces mp ON mp.music_piece_id = mpt.music_piece_id AND mp.is_public = 1
                        WHERE mpt.tag_id = t.tag_id)
                     + (SELECT COUNT(*) FROM playlist_tags ptg
                        JOIN playlists p ON p.playlist_id = ptg.playlist_id AND p.is_public = 1
                        WHERE ptg.tag_id = t.tag_id) AS uses
                FROM tags t
                WHERE t.tag LIKE :prefix ESCAPE '\\'
            )
            WHERE is_curated = 1 OR uses > 0
            ORDER BY is_curated DESC, uses DESC, tag
            LIMIT :limit
            """, nativeQuery = true)
    List<String> suggest(@Param("prefix") String prefix, @Param("limit") int limit);
}
