package com.aux_app.controller;

import com.aux_app.auth.CurrentUser;
import com.aux_app.auth.OptionalCurrentUser;
import com.aux_app.dto.music_piece.MusicPiecePage;
import com.aux_app.entity.UserEntity;
import com.aux_app.repository.MusicPieceRepository;
import com.aux_app.services.TagService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

@RestController
@RequestMapping("/api/tags")
public class TagController {

    private static final int PIECE_PAGE_SIZE = 50;
    // Plays counted for a tag's order, so the page follows what's being listened to now
    private static final Duration PLAY_WINDOW = Duration.ofDays(30);

    private final TagService tags;
    private final MusicPieceRepository musicPieces;

    public TagController(TagService tags, MusicPieceRepository musicPieces) {
        this.tags = tags;
        this.musicPieces = musicPieces;
    }

    @GetMapping
    @Operation(
            summary = "Suggest tags",
            description = """
                    Tags starting with `q` (case-insensitive), at most 20. Curated genres and moods come first, then
                    tags used most on public pieces and playlists. Tags only used on private items are left out.
                    Omit `q` for the top tags. Use it for the tag picker on the upload and playlist forms.
                    """)
    @ApiResponse(responseCode = "200", description = "OK; tag names, empty if none match")
    public List<String> suggestTags(
            @CurrentUser UserEntity user,
            @Parameter(description = "Start of the tag name", example = "ch") @RequestParam(required = false) String q
    ) {
        return this.tags.suggest(q);
    }

    @GetMapping("/{tag}/music-pieces")
    @Operation(
            summary = "List the music pieces with a tag",
            description = """
                    Public; a token is optional. Public pieces carrying the tag, matched case-insensitively. The
                    caller's own private pieces are left out too, since the list is for discovery. Most played in the
                    last 30 days first (uploaders' plays of their own pieces don't count), newest as the tie-break.
                    Pages of 50: send `nextCursor` back as `cursor` for the next page. The order follows live play
                    counts, so a piece can move between pages while they're being read. Play them with `/stream` and
                    no `playlistId`.
                    """)
    @ApiResponse(responseCode = "200", description = "OK; `musicPieces` is empty for an unknown tag or one with no public pieces")
    @ApiResponse(responseCode = "400", description = "Bad cursor (code INVALID_FIELD)")
    public MusicPiecePage getTagMusicPieces(
            @Parameter(description = "Tag name, URL-encoded", example = "lo-fi") @PathVariable String tag,
            @Parameter(description = "`nextCursor` from the previous page") @RequestParam(required = false) String cursor,
            @OptionalCurrentUser UserEntity user
    ) {
        int offset = OffsetCursor.parse(cursor);
        long since = Instant.now().minus(PLAY_WINDOW).toEpochMilli();
        return OffsetCursor.page(this.musicPieces.findTagPieces(
                TagService.clean(tag), since, user == null ? null : user.getUserId(), PIECE_PAGE_SIZE + 1, offset),
                offset, PIECE_PAGE_SIZE);
    }
}
