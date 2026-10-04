package com.aux_app.controller;

import com.aux_app.dto.artist.ArtistSummary;
import com.aux_app.dto.music_piece.TopMusicPiece;
import com.aux_app.auth.OptionalCurrentUser;
import com.aux_app.dto.search.MusicPieceSearchResult;
import com.aux_app.dto.search.PlaylistSearchResult;
import com.aux_app.dto.search.SearchEntity;
import com.aux_app.dto.search.SearchQuery;
import com.aux_app.dto.search.UserSearchResult;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.MusicPieceRepository;
import com.aux_app.repository.PlaylistRepository;
import com.aux_app.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Content;
import io.swagger.v3.oas.annotations.media.ExampleObject;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/core")
public class CoreController {

    private static final int DEFAULT_LIMIT = 10;
    private static final int MAX_LIMIT = 50;

    private final UserRepository users;
    private final MusicPieceRepository musicPieces;
    private final PlaylistRepository playlists;

    public CoreController(UserRepository users, MusicPieceRepository musicPieces, PlaylistRepository playlists) {
        this.users = users;
        this.musicPieces = musicPieces;
        this.playlists = playlists;
    }

    @GetMapping("/users")
    @Operation(
            summary = "Get random user profile pictures",
            description = "Public. Returns up to 6 profile picture URLs of random users with a custom profile picture."
    )
    @ApiResponse(responseCode = "200", description = "OK")
    @ResponseStatus(HttpStatus.OK)
    public List<String> getUsers() {
        return users.findRandomProfilePictureUrls();
    }

    @GetMapping("/top")
    @Operation(
            summary = "Get the top music pieces",
            description = "Public. NOT IMPLEMENTED YET: returns one placeholder entry whose fields all read `NOT IMPLEMENTED`."
    )
    @ApiResponse(responseCode = "200", description = "OK")
    @ResponseStatus(HttpStatus.OK)
    public List<TopMusicPiece> getTopMusicPieces() {
        return List.of(
                new TopMusicPiece(
                        "NOT IMPLEMENTED",
                        "NOT IMPLEMENTED",
                        new ArtistSummary(
                                "NOT IMPLEMENTED",
                                "NOT IMPLEMENTED",
                                "NOT IMPLEMENTED"
                        )
                )
        );
    }

    @GetMapping("/search")
    @Operation(
            summary = "Search users, music pieces or playlists",
            description = """
                    Public; a token is optional. Query params: `category` (`USERS`, `MUSIC` or `PLAYLISTS`, required),
                    `query` (1 to 200 chars, trimmed, matched case-insensitively anywhere in the name; `MUSIC` also matches
                    the artist name), `limit` (1 to 50, default 10) and `offset` (default 0). Results are sorted by name.
                    `totalResults` counts all matches, not just this page. Private music pieces and playlists only show
                    up for their owner, and users who haven't finished onboarding are hidden.
                    """)
    @ApiResponse(
            responseCode = "200",
            description = "OK. `results` holds users, music pieces or playlists depending on `category`.",
            content = @Content(
                    mediaType = "application/json",
                    examples = {
                            @ExampleObject(name = "USERS", value = """
                                    {
                                      "totalResults": 1,
                                      "results": [
                                        {
                                          "userId": "u_41x9",
                                          "username": "justin",
                                          "profilePictureUrl": "https://aux.justinabruinsma.com/pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp"
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "MUSIC", value = """
                                    {
                                      "totalResults": 1,
                                      "results": [
                                        {
                                          "musicPieceId": "m_92kd0",
                                          "name": "Let It Happen",
                                          "coverUrl": "https://aux.justinabruinsma.com/music-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp",
                                          "artistSummary": {
                                            "artistId": "a_8f3k2",
                                            "artistName": "Tame Impala",
                                            "artistPfpUrl": "https://aux.justinabruinsma.com/artist-pfp/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp"
                                          }
                                        }
                                      ]
                                    }
                                    """),
                            @ExampleObject(name = "PLAYLISTS", value = """
                                    {
                                      "totalResults": 1,
                                      "results": [
                                        {
                                          "playlistId": "p_7c2d1",
                                          "playlistName": "Late Night Drive",
                                          "playlistCoverUrl": "https://aux.justinabruinsma.com/playlist-cover/3f2b8c1e-8d4a-4c6e-9a51-2f0d7b9e6c11.webp",
                                          "ownerUsername": "justin",
                                          "pieceCount": 5
                                        }
                                      ]
                                    }
                                    """)
                    }))
    @ApiResponse(responseCode = "400", description = "Missing category or query, or limit/offset out of range")
    @ResponseStatus(HttpStatus.OK)
    public SearchEntity<?> search(@OptionalCurrentUser UserEntity user, @Valid SearchQuery query) {
        if (query.category() == null) {
            throw new AuxException(
                    HttpStatus.BAD_REQUEST,
                    "MISSING_CATEGORY",
                    "category is required",
                    "category"
            );
        }
        String text = query.query() == null ? "" : query.query().strip();
        if (text.isEmpty()) {
            throw new AuxException(
                    HttpStatus.BAD_REQUEST,
                    "BLANK_QUERY",
                    "query must not be blank",
                    "query"
            );
        }
        int limit = query.limit() == null ? DEFAULT_LIMIT : query.limit();
        int offset = query.offset() == null ? 0 : query.offset();
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new AuxException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_LIMIT",
                    "limit must be between 1 and " + MAX_LIMIT,
                    "limit"
            );
        }
        if (offset < 0) {
            throw new AuxException(
                    HttpStatus.BAD_REQUEST,
                    "INVALID_OFFSET",
                    "offset must not be negative",
                    "offset"
            );
        }

        // Escape LIKE wildcards so "100%" or "a_b" match literally
        String pattern = "%" + text.replace("\\", "\\\\").replace("%", "\\%").replace("_", "\\_") + "%";
        // Never matches a row, so anonymous callers only see public items
        String userId = user == null ? "" : user.getUserId();

        return switch (query.category()) {
            case USERS -> new SearchEntity<>(
                    users.countSearchUsers(pattern),
                    users.searchUsers(pattern, limit, offset).stream()
                            .map(u -> new UserSearchResult(
                                    u.getUserId(),
                                    u.getUsername(),
                                    u.getProfilePictureUrl()
                                    )
                            ).toList());
            case MUSIC -> new SearchEntity<>(
                    musicPieces.countSearchMusicPieces(pattern, userId),
                    musicPieces.searchMusicPieces(pattern, userId, limit, offset).stream()
                            .map(m -> new MusicPieceSearchResult(
                                    m.getMusicPieceId(),
                                    m.getName(),
                                    m.getCoverUrl(),
                                    new ArtistSummary(
                                            m.getArtistId(),
                                            m.getArtistName(),
                                            m.getArtistPfpUrl()
                                    )
                                    )
                            ).toList());
            case PLAYLISTS -> new SearchEntity<>(
                    playlists.countSearchPlaylists(pattern, userId),
                    playlists.searchPlaylists(pattern, userId, limit, offset).stream()
                            .map(p -> new PlaylistSearchResult(
                                    p.getPlaylistId(), p.getPlaylistName(), p.getPlaylistCoverUrl(), p.getOwnerUsername(),
                                    p.getPieceCount()))
                            .toList());
        };
    }

}
