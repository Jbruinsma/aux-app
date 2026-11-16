package com.aux_app.controller;

import com.aux_app.auth.CurrentUser;
import com.aux_app.auth.OptionalCurrentUser;
import com.aux_app.dto.album.AlbumCreationDetails;
import com.aux_app.dto.album.AlbumDetails;
import com.aux_app.dto.album.AlbumDetailsUpdate;
import com.aux_app.dto.album.AlbumOverview;
import com.aux_app.dto.album.TopAlbumsResponse;
import com.aux_app.dto.music_piece.MusicPiecePage;
import com.aux_app.entity.AlbumEntity;
import com.aux_app.entity.ArtistEntity;
import com.aux_app.entity.UserEntity;
import com.aux_app.error.AuxException;
import com.aux_app.repository.AlbumRepository;
import com.aux_app.repository.ArtistRepository;
import com.aux_app.repository.MusicPieceRepository;
import com.aux_app.repository.UserRepository;
import com.aux_app.services.AlbumService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api")
public class AlbumController {

    private static final int PIECE_PAGE_SIZE = 50;

    private final AlbumService albumService;
    private final AlbumRepository albums;
    private final ArtistRepository artists;
    private final MusicPieceRepository musicPieces;
    private final UserRepository users;

    public AlbumController(
            AlbumService albumService,
            AlbumRepository albums,
            ArtistRepository artists,
            MusicPieceRepository musicPieces,
            UserRepository users
    ) {
        this.albumService = albumService;
        this.albums = albums;
        this.artists = artists;
        this.musicPieces = musicPieces;
        this.users = users;
    }

    @GetMapping("/artists/{artistId}/albums")
    @Operation(
            summary = "List an artist's albums",
            description = """
                    Public; a token is optional. Sorted by name, not paged. An album with no piece the caller can
                    play (public, or the caller's own private one) is left out unless the caller can edit it, so
                    empty albums only show to the person who made them and to the artist's creator.
                    `pieceCount` counts the pieces the caller can play. Use it for the artist page's Albums section
                    and the upload form's album picker.
                    """)
    @ApiResponse(responseCode = "200", description = "OK; empty if the artist has no albums the caller can see")
    @ApiResponse(responseCode = "404", description = "Artist not found (code ARTIST_NOT_FOUND)")
    public List<AlbumOverview> getArtistAlbums(
            @Parameter(description = "Public id of the artist", example = "a_8f3k2Q") @PathVariable String artistId,
            @OptionalCurrentUser UserEntity user
    ) {
        ArtistEntity artist = findArtist(artistId);
        return this.albums.findArtistAlbums(artist.getArtistId(), user == null ? null : user.getUserId());
    }

    @PostMapping(value = "/artists/{artistId}/albums", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Find or create an album",
            description = """
                    Anyone signed in can add an album to any artist. Multipart fields:
                    - `albumName` (required): at most 100 chars, trimmed.
                    - `coverImage`: same rules as a music piece cover. Required when the album is new.
                    If the artist already has an album with the same name (case-insensitive), returns that album
                    unchanged and ignores `coverImage`, so sending only `albumName` is a lookup that never creates.
                    The caller becomes the album's creator; they and the artist's creator can edit or delete it.
                    """)
    @ApiResponse(responseCode = "200", description = "Album found or created; body is the album")
    @ApiResponse(responseCode = "400", description = "Missing or bad field, including no cover for a new album (code INVALID_FIELD), or unreadable or too small cover (codes INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "404", description = "Artist not found (code ARTIST_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "Someone created the same album at the same moment; retry (code CONFLICT)")
    @ApiResponse(responseCode = "413", description = "Cover over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "Cover is not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public AlbumDetails createAlbum(
            @Parameter(description = "Public id of the artist", example = "a_8f3k2Q") @PathVariable String artistId,
            @CurrentUser UserEntity user,
            @Valid @ModelAttribute AlbumCreationDetails details
    ) {
        ArtistEntity artist = findArtist(artistId);
        AlbumEntity album = this.albumService.findOrCreate(user, artist, details);
        return this.albumService.details(album, user.getUserId());
    }

    @GetMapping("/albums/{albumId}")
    @Operation(
            summary = "Get an album",
            description = """
                    Public; a token is optional. `canEdit` is true when the caller created the album or its artist.
                    An album with no piece the caller can play is a 404 unless the caller can edit it, the same
                    rule as the artist's album list. List its pieces with `/music-pieces`.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "404", description = "Album not found or hidden from the caller (code ALBUM_NOT_FOUND)")
    public AlbumDetails getAlbum(
            @Parameter(description = "Public id of the album", example = "l_4Hq9xT") @PathVariable String albumId,
            @OptionalCurrentUser UserEntity user
    ) {
        String userId = user == null ? null : user.getUserId();
        return this.albumService.details(this.albumService.findVisibleAlbum(albumId, userId), userId);
    }

    @GetMapping("/albums/{albumId}/music-pieces")
    @Operation(
            summary = "List an album's music pieces",
            description = """
                    Public; a token is optional. The album's pieces the caller can play on their own: every public
                    one, plus the caller's own private ones. Most played first (all plays, all time), newest as the
                    tie-break, the same order as an artist's pieces; there are no track numbers. Pages of 50: send
                    `nextCursor` back as `cursor` for the next page. For "Play album", play them in this order with
                    `/stream` and no `playlistId`.
                    """)
    @ApiResponse(responseCode = "200", description = "OK; `musicPieces` is empty if the caller can play none")
    @ApiResponse(responseCode = "400", description = "Bad cursor (code INVALID_FIELD)")
    @ApiResponse(responseCode = "404", description = "Album not found (code ALBUM_NOT_FOUND)")
    public MusicPiecePage getAlbumMusicPieces(
            @Parameter(description = "Public id of the album", example = "l_4Hq9xT") @PathVariable String albumId,
            @Parameter(description = "`nextCursor` from the previous page") @RequestParam(required = false) String cursor,
            @OptionalCurrentUser UserEntity user
    ) {
        AlbumEntity album = this.albumService.findAlbum(albumId);
        int offset = OffsetCursor.parse(cursor);
        return OffsetCursor.page(this.musicPieces.findAlbumPieces(
                        album.getAlbumId(), user == null ? null : user.getUserId(), PIECE_PAGE_SIZE + 1, offset),
                offset, PIECE_PAGE_SIZE);
    }

    @PutMapping(value = "/albums/{albumId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    @Operation(
            summary = "Edit an album",
            description = """
                    Only the album's creator or the artist's creator. Every multipart field is optional; a missing
                    or blank field leaves that value unchanged.
                    - `albumName`: at most 100 chars, trimmed. Must not match another of the artist's albums
                      (case-insensitive).
                    - `coverImage`: same rules as on create. Every piece that uses the album cover switches to the
                      new one, including other people's; pieces with their own cover keep it.
                    Returns the album after the update.
                    """)
    @ApiResponse(responseCode = "200", description = "OK")
    @ApiResponse(responseCode = "400", description = "Bad field, or unreadable or too small cover (codes INVALID_FIELD, INVALID_IMAGE, IMAGE_TOO_SMALL)")
    @ApiResponse(responseCode = "403", description = "Caller didn't create the album or its artist (code NOT_ALBUM_EDITOR)")
    @ApiResponse(responseCode = "404", description = "Album not found (code ALBUM_NOT_FOUND)")
    @ApiResponse(responseCode = "409", description = "The artist already has an album with that name (code ALBUM_NAME_TAKEN)")
    @ApiResponse(responseCode = "413", description = "Cover over 5MB or 25 megapixels (code IMAGE_TOO_LARGE), or file over 25MB (code REQUEST_FAILED)")
    @ApiResponse(responseCode = "415", description = "Cover is not a JPEG or PNG (code UNSUPPORTED_IMAGE_TYPE)")
    public AlbumDetails editAlbum(
            @Parameter(description = "Public id of the album", example = "l_4Hq9xT") @PathVariable String albumId,
            @CurrentUser UserEntity user,
            @Valid @ModelAttribute AlbumDetailsUpdate details
    ) {
        return this.albumService.details(this.albumService.update(user, albumId, details), user.getUserId());
    }

    @DeleteMapping("/albums/{albumId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @Operation(
            summary = "Delete an album",
            description = """
                    Only the album's creator or the artist's creator. Its pieces aren't deleted: they become singles
                    and keep the cover they show, so a piece that used the album cover keeps that image as its own.
                    """)
    @ApiResponse(responseCode = "204", description = "Album deleted")
    @ApiResponse(responseCode = "403", description = "Caller didn't create the album or its artist (code NOT_ALBUM_EDITOR)")
    @ApiResponse(responseCode = "404", description = "Album not found (code ALBUM_NOT_FOUND)")
    public void deleteAlbum(
            @Parameter(description = "Public id of the album", example = "l_4Hq9xT") @PathVariable String albumId,
            @CurrentUser UserEntity user
    ) {
        this.albumService.delete(user, albumId);
    }

    @GetMapping("/albums/top/{username}")
    @Operation(
            summary = "List a user's most played albums",
            description = """
                    Public; a token is optional. Up to 10 albums the user played most, all time, ties going to the
                    one played most recently. Only plays of pieces the caller can play on their own count, so the
                    list never reveals private pieces. `pieceCount` and `canEdit` are for the caller, as elsewhere.
                    The profile response already includes this as `topAlbums`.
                    """)
    @ApiResponse(responseCode = "200", description = "OK; `albums` is empty if the user hasn't played any album's pieces")
    @ApiResponse(responseCode = "404", description = "User not found (code USER_NOT_FOUND)")
    public TopAlbumsResponse getTopAlbums(
            @Parameter(description = "Username of the user whose albums to list", example = "justin") @PathVariable String username,
            @OptionalCurrentUser UserEntity user
    ) {
        UserEntity owner = this.users.findByUsernameIgnoreCase(username);
        if (owner == null) {
            throw new AuxException(HttpStatus.NOT_FOUND, "USER_NOT_FOUND", "User not found", "username");
        }
        return new TopAlbumsResponse(
                this.albums.findTopAlbums(owner.getUserId(), user == null ? null : user.getUserId()));
    }

    private ArtistEntity findArtist(String artistId) {
        ArtistEntity artist = this.artists.findByPublicId(artistId);
        if (artist == null) {
            throw new AuxException(HttpStatus.NOT_FOUND, "ARTIST_NOT_FOUND", "Artist not found", "artistId");
        }
        return artist;
    }
}
